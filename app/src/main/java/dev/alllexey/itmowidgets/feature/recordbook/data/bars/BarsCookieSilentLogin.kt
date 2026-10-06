package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import dev.alllexey.itmoapi.bars.auth.BarsLogin
import dev.alllexey.itmoapi.bars.auth.BarsSessionCode
import dev.alllexey.itmowidgets.core.network.isCausedByNetworkFailure
import dev.alllexey.itmowidgets.core.result.AppError
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/** Result of renewing the BARS session without a WebView. */
sealed interface BarsCookieRenewal {
    /** A one-time authorization code; exchange it at once. The code never appears in [toString]. */
    class Code(val code: String) : BarsCookieRenewal {
        override fun toString(): String = "Code"
    }

    /** ITMO.ID wants the user: there are no session cookies or they no longer sign in. */
    data object SessionEnded : BarsCookieRenewal

    /** ITMO.ID or the network failed; the session may still be alive. */
    data class Failed(val error: AppError) : BarsCookieRenewal
}

/** Renews the BARS session in the background, where no WebView can be shown. */
interface BarsBackgroundLogin {
    suspend fun renew(state: String): BarsCookieRenewal
}

/**
 * Repeats the official ITMO.ID authorization request of the `bars` client with the WebView's ITMO.ID cookies
 * ([BarsLogin.requestCodeWithCookies]). The library sends the cookies only to that URL over HTTPS, follows no
 * redirects, keeps no cookie jar or cache and accepts only the exact callback with the same `state`. `Set-Cookie` of
 * the answer goes back to the WebView's store for the same URL. Codes and cookies are never logged or put into
 * exceptions.
 */
class BarsCookieSilentLogin @Inject constructor(
    private val login: BarsLogin,
    private val cookies: ItmoIdCookies
) : BarsBackgroundLogin {

    override suspend fun renew(state: String): BarsCookieRenewal {
        val url = login.loginUrl(state)
        val header = try {
            cookies.cookieHeader(url)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            // CookieManager throws while the WebView package is being updated or is missing.
            return BarsCookieRenewal.Failed(AppError.Unknown())
        }
        if (header.isNullOrBlank()) return BarsCookieRenewal.SessionEnded
        val answer = try {
            login.requestCodeWithCookies(state, header)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (failure: Exception) {
            // No network is not an ended session: the cookies may still sign in on the next run.
            return BarsCookieRenewal.Failed(if (failure.isCausedByNetworkFailure()) AppError.Network else AppError.Unknown())
        }
        storeCookies(url, answer.setCookies)
        return when (answer.outcome) {
            // The library sets a code for CODE only; a missing one is a rejected answer, not an ended session.
            BarsSessionCode.Outcome.CODE ->
                answer.code?.let(BarsCookieRenewal::Code) ?: BarsCookieRenewal.Failed(AppError.Unknown())
            BarsSessionCode.Outcome.LOGIN_REQUIRED -> BarsCookieRenewal.SessionEnded
            BarsSessionCode.Outcome.REJECTED, BarsSessionCode.Outcome.HTTP_ERROR ->
                BarsCookieRenewal.Failed(AppError.Unknown())
        }
    }

    private suspend fun storeCookies(url: String, setCookies: List<String>) {
        try {
            cookies.store(url, setCookies)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            // The code is already issued; a cookie store that cannot be written only affects a later renewal.
        }
    }
}
