package dev.alllexey.itmowidgets.core.network

import dev.alllexey.itmoapi.itmoid.TokenManager
import dev.alllexey.itmowidgets.client.AccessTokenSource
import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.client.ClientVersion
import io.ktor.client.engine.HttpClientEngine

/**
 * Builds the one Core 2.0 Backend client of the process. Each platform passes the Backend origin, the MyItmoApi 2.x
 * [TokenManager] of its `MyItmoClient` and its engine; `:shared:backend-client` never picks an engine itself. The
 * apps pass their build as [version] (`X-App-Version`, Backend requests only); tests may leave it out.
 */
object BackendClientFactory {

    fun create(
        baseUrl: String,
        tokens: TokenManager,
        engine: HttpClientEngine,
        version: ClientVersion? = null,
    ): BackendClient = BackendClient(baseUrl, tokens.asBackendTokenSource(), engine, version)
}

/**
 * The access token for Backend from the one 2.x refresher. A missing or expired session sends no `Authorization`
 * header, as Core 1.x did; otherwise an expiring token is refreshed before the request and written by [TokenManager]
 * alone. A refresh that fails reaches the caller as `BackendException.Transport`, which is `AppError.Network` as
 * with 1.x.
 */
fun TokenManager.asBackendTokenSource(): AccessTokenSource = AccessTokenSource {
    if (isRefreshTokenExpired()) null else validAccessToken()
}
