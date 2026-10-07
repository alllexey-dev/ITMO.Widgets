package dev.alllexey.itmowidgets.feature.recordbook.data.sheets

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.CookieJar

/** OkHttp without a cookie jar, cache or redirects; `HttpRedirect` follows them and `HttpTimeout` sets the limits. */
internal actual fun publicSheetEngine(): HttpClientEngine = OkHttp.create {
    config {
        followRedirects(false)
        followSslRedirects(false)
        cookieJar(CookieJar.NO_COOKIES)
        cache(null)
    }
}
