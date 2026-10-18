package dev.alllexey.itmowidgets.feature.map.data.mirror

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.CookieJar

internal actual fun campusMapEngine(): HttpClientEngine = OkHttp.create {
    config {
        cookieJar(CookieJar.NO_COOKIES)
        cache(null)
    }
}
