package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import kotlin.io.encoding.Base64
import kotlin.time.Instant

/**
 * The owner's check of the BARS session on the simulator, which the Debug launch arguments of the app run until the
 * recordbook reaches iOS (IO-09d2): the saved header is replaced with one BARS rejects, then a request renews it as
 * an expired session would, through the hidden WebView ([renewInForeground]) or the cookie replay
 * ([renewInBackground]). Reports only the header's length and expiry, never a value.
 */
class BarsSessionCheck(
    private val client: BarsClient,
    private val tokens: BarsTokenStore,
    private val currentUser: CurrentUserProvider,
) {

    /** The saved header of the signed-in user. */
    suspend fun report(): String {
        val owner = currentUser.getCurrentUser()?.isu ?: return "BARS: signed out"
        return describe(tokens.load(owner))
    }

    suspend fun renewInForeground(): String = renew { client.account { user.login.length } is AppResult.Success }

    suspend fun renewInBackground(): String =
        renew { client.backgroundAccount { user.login.length } is BarsBackground.Success }

    private suspend fun renew(request: suspend () -> Boolean): String {
        val owner = currentUser.getCurrentUser()?.isu ?: return "BARS: signed out"
        val saved = tokens.load(owner) ?: return "BARS: no session"
        tokens.install(owner, REJECTED)
        val renewed = request()
        val header = tokens.load(owner)
        if (renewed && header != null && header != REJECTED) return "BARS renewed: ${describe(header)}"
        // A failed renewal leaves the session as it was before the check.
        if (header == REJECTED) tokens.install(owner, saved)
        return "BARS not renewed"
    }

    private fun describe(header: String?): String {
        if (header == null) return "BARS: no session"
        return "header length ${header.length}, expires ${expiry(header) ?: "unknown"}"
    }

    /** The `exp` claim of the bearer JWT. */
    private fun expiry(header: String): Instant? {
        val payload = header.removePrefix(BEARER).split('.').getOrNull(1) ?: return null
        val text = runCatching {
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(payload).decodeToString()
        }.getOrNull() ?: return null
        val seconds = EXP.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        return Instant.fromEpochSeconds(seconds)
    }

    private companion object {
        const val BEARER = "Bearer "

        /** Valid in form, so the library sends it and BARS answers 401. */
        const val REJECTED = "Bearer rejected-by-the-session-check"

        val EXP = Regex("\"exp\"\\s*:\\s*(\\d+)")
    }
}
