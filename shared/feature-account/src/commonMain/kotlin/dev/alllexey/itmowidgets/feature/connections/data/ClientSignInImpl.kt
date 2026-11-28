package dev.alllexey.itmowidgets.feature.connections.data

import dev.alllexey.itmoapi.itmoid.CallbackUrl
import dev.alllexey.itmoapi.itmoid.ItmoIdClient
import dev.alllexey.itmoapi.itmoid.ItmoSessionLabel
import dev.alllexey.itmoapi.itmoid.Pkce
import dev.alllexey.itmoapi.itmoid.TokenSet
import dev.alllexey.itmowidgets.core.connections.SignInLabel
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.appResultOf
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.feature.connections.domain.ClientItmoSession
import dev.alllexey.itmowidgets.feature.connections.domain.ClientRefreshToken
import dev.alllexey.itmowidgets.feature.connections.domain.ClientSignIn
import dev.alllexey.itmowidgets.feature.connections.domain.ClientSignInAttempt
import dev.alllexey.itmowidgets.feature.connections.domain.ClientSignInResult
import dev.alllexey.itmowidgets.feature.connections.domain.ClientType
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoIdentity
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoLogins
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoLoginsRepository
import dev.alllexey.itmowidgets.feature.connections.domain.PairingRequest
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * A client's own ITMO.ID sign-in: PKCE for the first ITMO OIDC client on the official page, the callback caught before
 * it loads, the code exchanged here. The tokens must name the phone's ISU, else that session is ended at once. The
 * session's end comes from ITMO.ID through the phone's cookie, else the sign-in plus 90 days (plan 5.4).
 *
 * An attempt with identity (an `itmo:sso` grant) also takes the session's `KEYCLOAK_IDENTITY`, which the host read
 * from the isolated store at the first callback: its unverified payload must carry the token response's `sid`, and
 * its `exp` is when it ends. A missing or foreign cookie ends the new session (ADR 0040).
 *
 * Every further ITMO OIDC client is a silent authorize (`prompt=none`, PKCE) in the same page and store, its callback
 * caught too and its code exchanged with that client. Each token must carry the first one's session and the phone's
 * ISU; otherwise, or when ITMO.ID answers `login_required`, every token minted so far is logged out (none reached
 * anyone) and the attempt is [ClientSignInResult.SessionMismatch].
 */
class ClientSignInImpl(
    private val oidc: ItmoOidcClients,
    private val logins: ItmoLoginsRepository,
    private val users: CurrentUserProvider,
    private val label: SignInLabel,
    private val clock: Clock,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers,
) : ClientSignIn {

    override suspend fun begin(request: PairingRequest, oidcClients: List<String>, withIdentity: Boolean): ClientSignInAttempt {
        require(oidcClients.isNotEmpty()) { "No ITMO client to sign in to" }
        val clients = oidcClients.map { id -> id to requireNotNull(oidc.client(id)) { "Unknown ITMO client" } }
        val userAgent = if (label.isBrowserLike()) {
            ItmoSessionLabel.browserUserAgent(request.platform)
        } else {
            ItmoSessionLabel.botUserAgent(request.client.labelName(), request.name, request.version, request.platform)
        }
        return Attempt(userAgent, withIdentity, clients)
    }

    /** One authorize: the PKCE secrets and the callback of [client]. Holds secrets, so [toString] is redacted. */
    private class Step(val oidcClient: String, val client: ItmoIdClient, silent: Boolean) {
        private val verifier = Pkce.newVerifier()
        private val state = Pkce.newState()
        private val callback = client.configuration.let { CallbackUrl(it.redirectUri, it.issuer) }
        val url: String = client.loginUrl(Pkce.challenge(verifier), state).let { if (silent) "$it&$SILENT" else it }

        fun isCallback(url: String): Boolean = callback.isCallback(url)

        /** The code, or null for an error callback (`login_required` among them) or a wrong state. */
        fun code(url: String): String? = callback.extractCode(url, state)

        suspend fun exchange(code: String): TokenSet = client.exchange(code, verifier)

        override fun toString(): String = "Step(redacted)"
    }

    /** A token minted for [oidcClient], kept to be sealed or logged out. */
    private class Minted(val oidcClient: String, val client: ItmoIdClient, val tokens: TokenSet) {
        override fun toString(): String = "Minted(redacted)"
    }

    private inner class Attempt(
        override val userAgent: String,
        override val withIdentity: Boolean,
        private val clients: List<Pair<String, ItmoIdClient>>,
    ) : ClientSignInAttempt {
        private var index = 0
        private var step = clients.first().let { (id, client) -> Step(id, client, silent = false) }
        private val minted = mutableListOf<Minted>()
        private var first: ClientItmoSession? = null

        override val url: String = step.url

        override val lastStep: Boolean get() = index == clients.lastIndex

        override fun isCallback(url: String): Boolean = step.isCallback(url)

        override suspend fun finish(callbackUrl: String, identityCookie: String?): ClientSignInResult {
            if (demo.isActive()) return ClientSignInResult.Failed(AppError.DemoUnavailable)
            val result = if (index == 0) signedIn(callbackUrl, identityCookie) else authorized(callbackUrl)
            val session = (result as? ClientSignInResult.Success)?.session ?: return result
            if (lastStep) return result
            first = session
            index++
            step = clients[index].let { (id, client) -> Step(id, client, silent = true) }
            return ClientSignInResult.Next(step.url)
        }

        override suspend fun abandon() {
            withContext(NonCancellable) { logOutMinted() }
        }

        /** The user's sign-in for the first client. */
        private suspend fun signedIn(callbackUrl: String, identityCookie: String?): ClientSignInResult {
            val code = step.code(callbackUrl) ?: return ClientSignInResult.Failed(AppError.Unauthorized)
            val tokens = when (val exchanged = io { step.exchange(code) }) {
                is AppResult.Success -> exchanged.value
                is AppResult.Failure -> return ClientSignInResult.Failed(exchanged.error)
            }
            val isu = IdTokenClaims.isu(tokens.idToken)
            if (isu == null || isu != users.getCurrentUser()?.isu) {
                io { step.client.logout(tokens.refreshToken) }
                return ClientSignInResult.OtherAccount
            }
            val sid = tokens.sessionId ?: return ClientSignInResult.Failed(AppError.Unauthorized)
            val identity = if (withIdentity) {
                identity(identityCookie, sid) ?: run {
                    // The user granted the cookie: a pairing without it is not what they approved.
                    io { step.client.logout(tokens.refreshToken) }
                    return ClientSignInResult.IdentityMissing
                }
            } else {
                null
            }
            minted += Minted(step.oidcClient, step.client, tokens)
            val listed = (logins.list() as? AppResult.Success)?.value as? ItmoLogins.Listed
            val expiresAt = listed?.sessions?.firstOrNull { it.sid == sid }?.expires ?: (clock.now() + SESSION_LIFETIME)
            return ClientSignInResult.Success(
                ClientItmoSession(
                    isu = isu,
                    sid = sid,
                    expiresAt = expiresAt,
                    refreshToken = tokens.refreshToken,
                    refreshExpiresAt = tokens.refreshExpiresAt,
                    identity = identity,
                    oidcClient = step.oidcClient,
                ),
            )
        }

        /** A silent authorize for a further client: the same session and ISU as the first, else nothing is kept. */
        private suspend fun authorized(callbackUrl: String): ClientSignInResult {
            val session = checkNotNull(first) { "No first sign-in" }
            val code = step.code(callbackUrl) ?: return mismatch()
            val tokens = when (val exchanged = io { step.exchange(code) }) {
                is AppResult.Success -> exchanged.value
                is AppResult.Failure -> {
                    logOutMinted()
                    return ClientSignInResult.Failed(exchanged.error)
                }
            }
            minted += Minted(step.oidcClient, step.client, tokens)
            val sid = tokens.sessionId ?: IdTokenClaims.sessionId(tokens.idToken)
            if (sid != session.sid || IdTokenClaims.isu(tokens.idToken) != session.isu) return mismatch()
            return ClientSignInResult.Success(
                ClientItmoSession(
                    isu = session.isu,
                    sid = session.sid,
                    expiresAt = session.expiresAt,
                    refreshToken = session.refreshToken,
                    refreshExpiresAt = session.refreshExpiresAt,
                    identity = session.identity,
                    oidcClient = session.oidcClient,
                    others = session.others + ClientRefreshToken(step.oidcClient, tokens.refreshToken, tokens.refreshExpiresAt),
                ),
            )
        }

        private suspend fun mismatch(): ClientSignInResult {
            logOutMinted()
            return ClientSignInResult.SessionMismatch
        }

        /** Ends every session minted so far; one of them ending first makes the others' logout fail, which is fine. */
        private suspend fun logOutMinted() {
            val all = minted.toList()
            minted.clear()
            all.forEach { io { it.client.logout(it.tokens.refreshToken) } }
        }

        /** The cookie when its payload names [sid] and carries `exp`; null otherwise. */
        private fun identity(cookie: String?, sid: String): ItmoIdentity? {
            if (cookie.isNullOrBlank() || IdTokenClaims.sessionId(cookie) != sid) return null
            return IdTokenClaims.expiresAt(cookie)?.let { ItmoIdentity(cookie, it) }
        }

        override fun toString(): String = "ClientSignInAttempt(redacted)"
    }

    private suspend fun <T> io(block: suspend () -> T): AppResult<T> =
        appResultOf(::backendAppError) { withContext(dispatchers.io) { block() } }

    private companion object {
        /** An ITMO.ID session ends 90 days after its password sign-in (plan 0.1). */
        val SESSION_LIFETIME = 90.days

        /** OIDC's silent authorize: ITMO.ID answers from its cookie or with `error=login_required`, never a page. */
        const val SILENT = "prompt=none"

        /** The `client` part of the honest label: `agent` or `MCP`. */
        fun ClientType.labelName(): String = if (this == ClientType.MCP) "MCP" else "agent"
    }
}
