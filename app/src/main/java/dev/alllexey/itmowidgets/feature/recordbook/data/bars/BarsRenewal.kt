package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import dev.alllexey.itmoapi.bars.BarsCodeSupplier
import dev.alllexey.itmowidgets.core.result.AppError
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The library's [BarsCodeSupplier]: screens renew an expired session through the headless WebView flow, background
 * blocks of [BarsClient] through ITMO.ID cookies without a WebView. Only one [BarsClient] block runs at a time, so the
 * mode belongs to the block that holds the client's lock. The library calls this inside its session lock, so it never
 * calls back into the library client.
 */
@Singleton
class BarsRenewal @Inject constructor(
    private val silentLogin: BarsSilentLogin,
    private val backgroundLogin: BarsBackgroundLogin
) : BarsCodeSupplier {

    @Volatile private var cookies = false

    override suspend fun obtainCode(state: String): String? =
        if (cookies) codeOf(backgroundLogin.renew(state)) else silentLogin.authorizationCode(state)

    /** Runs [block] with cookie renewal; the caller holds the [BarsClient] lock. */
    internal suspend fun <T> throughCookies(block: suspend () -> T): T {
        cookies = true
        try {
            return block()
        } finally {
            cookies = false
        }
    }

    private fun codeOf(renewal: BarsCookieRenewal): String = when (renewal) {
        is BarsCookieRenewal.Code -> renewal.code
        BarsCookieRenewal.SessionEnded -> throw BarsSessionEnded()
        is BarsCookieRenewal.Failed -> throw BarsFailure(renewal.error)
    }
}

/** Ends a cookie renewal without touching the saved header; [BarsClient.backgroundAccount] reports the end. */
internal class BarsSessionEnded : RuntimeException()

/** An [AppError] carried through the library; it never holds a URL, a code or a response body. */
internal class BarsFailure(val error: AppError) : RuntimeException()
