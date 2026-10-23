package dev.alllexey.itmowidgets.feature.connections.data

import dev.alllexey.itmoapi.itmoid.CallbackUrl
import dev.alllexey.itmoapi.itmoid.ItmoSessionLabel
import dev.alllexey.itmoapi.itmoid.Pkce
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.core.connections.SignInLabel
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.appResultOf
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.feature.connections.domain.ClientItmoSession
import dev.alllexey.itmowidgets.feature.connections.domain.ClientSignIn
import dev.alllexey.itmowidgets.feature.connections.domain.ClientSignInAttempt
import dev.alllexey.itmowidgets.feature.connections.domain.ClientSignInResult
import dev.alllexey.itmowidgets.feature.connections.domain.ClientType
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoLogins
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoLoginsRepository
import dev.alllexey.itmowidgets.feature.connections.domain.PairingRequest
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.withContext

/**
 * A client's own ITMO.ID sign-in: PKCE for MyITMO's public client on the official page, the callback caught before
 * it loads, the code exchanged here. The tokens must name the phone's ISU, else that session is ended at once. The
 * session's end comes from ITMO.ID through the phone's cookie, else the sign-in plus 90 days (plan 5.4).
 */
class ClientSignInImpl(
    private val myItmo: MyItmoClient,
    private val logins: ItmoLoginsRepository,
    private val users: CurrentUserProvider,
    private val label: SignInLabel,
    private val clock: Clock,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers,
) : ClientSignIn {

    override suspend fun begin(request: PairingRequest): ClientSignInAttempt {
        val verifier = Pkce.newVerifier()
        val state = Pkce.newState()
        val userAgent = if (label.isBrowserLike()) {
            ItmoSessionLabel.browserUserAgent(request.platform)
        } else {
            ItmoSessionLabel.botUserAgent(request.client.labelName(), request.name, request.version, request.platform)
        }
        return Attempt(myItmo.identity.loginUrl(Pkce.challenge(verifier), state), userAgent, verifier, state)
    }

    private inner class Attempt(
        override val url: String,
        override val userAgent: String,
        private val verifier: String,
        private val state: String,
    ) : ClientSignInAttempt {
        private val callback = myItmo.identity.configuration.let { CallbackUrl(it.redirectUri, it.issuer) }

        override fun isCallback(url: String): Boolean = callback.isCallback(url)

        override suspend fun finish(callbackUrl: String): ClientSignInResult {
            if (demo.isActive()) return ClientSignInResult.Failed(AppError.DemoUnavailable)
            val code = callback.extractCode(callbackUrl, state)
                ?: return ClientSignInResult.Failed(AppError.Unauthorized)
            val tokens = when (val exchanged = io { myItmo.identity.exchange(code, verifier) }) {
                is AppResult.Success -> exchanged.value
                is AppResult.Failure -> return ClientSignInResult.Failed(exchanged.error)
            }
            val isu = IdTokenClaims.isu(tokens.idToken)
            if (isu == null || isu != users.getCurrentUser()?.isu) {
                io { myItmo.identity.logout(tokens.refreshToken) }
                return ClientSignInResult.OtherAccount
            }
            val sid = tokens.sessionId ?: return ClientSignInResult.Failed(AppError.Unauthorized)
            val listed = (logins.list() as? AppResult.Success)?.value as? ItmoLogins.Listed
            val expiresAt = listed?.sessions?.firstOrNull { it.sid == sid }?.expires ?: (clock.now() + SESSION_LIFETIME)
            return ClientSignInResult.Success(
                ClientItmoSession(isu, sid, expiresAt, tokens.refreshToken, tokens.refreshExpiresAt),
            )
        }

        override fun toString(): String = "ClientSignInAttempt(redacted)"
    }

    private suspend fun <T> io(block: suspend () -> T): AppResult<T> =
        appResultOf(::backendAppError) { withContext(dispatchers.io) { block() } }

    private companion object {
        /** An ITMO.ID session ends 90 days after its password sign-in (plan 0.1). */
        val SESSION_LIFETIME = 90.days

        /** The `client` part of the honest label: `agent` or `MCP`. */
        fun ClientType.labelName(): String = if (this == ClientType.MCP) "MCP" else "agent"
    }
}
