package dev.alllexey.itmowidgets.feature.map.data.mirror

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout

/** The platform engine of the mirror client: OkHttp on Android, URLSession on iOS, without cookies or a cache. */
internal expect fun campusMapEngine(): HttpClientEngine

/** No cookies and no credentials; 15 s to connect, 30 s between bytes, 120 s per file (the largest is 2 MB). */
internal fun campusMapHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 30_000
        requestTimeoutMillis = 120_000
    }
}
