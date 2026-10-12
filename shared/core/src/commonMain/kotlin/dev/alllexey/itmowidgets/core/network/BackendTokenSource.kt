package dev.alllexey.itmowidgets.core.network

import dev.alllexey.itmoapi.itmoid.TokenManager
import dev.alllexey.itmowidgets.client.AccessTokenSource

/**
 * The access token for Backend from the one 2.x refresher. A missing or expired session sends no `Authorization`
 * header, as Core 1.x did; otherwise an expiring token is refreshed before the request and written by [TokenManager]
 * alone. A refresh that fails reaches the caller as `BackendException.Transport`, which is `AppError.Network` as
 * with 1.x.
 */
fun TokenManager.asBackendTokenSource(): AccessTokenSource = AccessTokenSource {
    if (isRefreshTokenExpired()) null else validAccessToken()
}
