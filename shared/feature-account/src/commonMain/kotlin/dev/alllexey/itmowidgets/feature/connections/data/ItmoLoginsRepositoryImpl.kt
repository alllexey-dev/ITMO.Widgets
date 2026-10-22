package dev.alllexey.itmowidgets.feature.connections.data

import dev.alllexey.itmoapi.itmoid.ItmoIdSessions
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.appResultOf
import dev.alllexey.itmowidgets.core.session.ItmoIdCookies
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoLogin
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoLogins
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoLoginsRepository
import kotlinx.coroutines.withContext

/**
 * The account's ITMO.ID sessions through MyItmoApi's [ItmoIdSessions] with a `profile` token that the phone's own SSO
 * cookie gets silently ([cookies]). Nothing here is sent to Backend or logged: labels, IPs and `sid`s stay on the
 * phone. The demo session never reaches ITMO.ID.
 */
class ItmoLoginsRepositoryImpl(
    private val sessions: ItmoIdSessions,
    private val cookies: ItmoIdCookies,
    private val tokens: SessionTokenStore,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers,
) : ItmoLoginsRepository {

    override suspend fun list(): AppResult<ItmoLogins> = call {
        val bearer = profileToken() ?: return@call ItmoLogins.NoCookie
        val listed = sessions.list(bearer).map {
            ItmoLogin(
                sid = it.sid,
                started = it.started,
                lastAccess = it.lastAccess,
                expires = it.expires,
                label = label(it.os, it.browser),
                ipAddress = it.ipAddress.ifBlank { null },
                mobile = it.mobile,
            )
        }
        ItmoLogins.Listed(listed, IdTokenClaims.sid(tokens.getIdToken()))
    }

    override suspend fun end(sids: List<String>): AppResult<Unit> {
        if (sids.isEmpty()) return AppResult.Success(Unit)
        val bearer = when (val token = call { profileToken() }) {
            is AppResult.Failure -> return token
            is AppResult.Success -> token.value ?: return AppResult.Failure(AppError.Unauthorized)
        }
        return call { sids.forEach { sessions.end(bearer, it) } }
    }

    private suspend fun profileToken(): String? = sessions.profileToken(cookies.cookieHeader(COOKIE_URL))

    private suspend fun <T> call(request: suspend () -> T): AppResult<T> {
        if (demo.isActive()) return AppResult.Failure(AppError.DemoUnavailable)
        return appResultOf(::backendAppError) { withContext(dispatchers.io) { request() } }
    }

    companion object {
        /** Where the realm's SSO cookies (`KEYCLOAK_IDENTITY`, `KEYCLOAK_SESSION`) apply. */
        const val COOKIE_URL = "https://id.itmo.ru/auth/realms/itmo/protocol/openid-connect/auth"

        /**
         * ITMO's own row title, browser first, as id.itmo.ru/activity reads: «Chrome 141, Windows»; an unknown
         * browser (`Other/...`) leaves the OS alone, an unknown OS the browser alone.
         */
        internal fun label(os: String, browser: String): String {
            val name = browser.substringBefore('/').trim()
            val major = browser.substringAfter('/', "").substringBefore('.').trim()
            val shownBrowser = name.takeUnless { it.isEmpty() || it == "Other" }?.let { if (major.isEmpty()) it else "$it $major" }
            val shownOs = os.trim().takeUnless { it.isEmpty() || it == "Other" }
            return listOfNotNull(shownBrowser, shownOs).joinToString(", ").ifEmpty { browser.ifBlank { os } }
        }
    }
}
