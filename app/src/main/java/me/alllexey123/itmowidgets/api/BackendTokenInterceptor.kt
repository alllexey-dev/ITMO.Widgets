package me.alllexey123.itmowidgets.api

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class BackendTokenInterceptor(
    val backend: ItmoWidgetsBackend
) : Interceptor {

    private val tokenRefreshLock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (!backend.storage.getBackendAllow()) {
            throw IOException("User has not allowed backend usage")
        }

        if (request.url.pathSegments.contains("auth")) {
            return chain.proceed(request)
        }

        var accessToken = backend.storage.getBackendAccessToken()
        val accessExpiresAt = backend.storage.getBackendAccessExpiresAt()
        val currentTime = System.currentTimeMillis()

        if (accessToken == null || accessExpiresAt < currentTime) {
            synchronized(tokenRefreshLock) {
                val currentToken = backend.storage.getBackendAccessToken()
                val currentExpiresAt = backend.storage.getBackendAccessExpiresAt()

                if (currentToken == null || currentExpiresAt < currentTime) {
                    val newTokens = backend.refreshTokensBlocking()
                    accessToken = newTokens.accessToken
                } else {
                    accessToken = currentToken
                }
            }
        }

        val newRequest = request.newBuilder()
            .header("Authorization", "Bearer $accessToken")
            .build()

        return chain.proceed(newRequest)
    }


}