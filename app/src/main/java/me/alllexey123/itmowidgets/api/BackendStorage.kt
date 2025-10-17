package me.alllexey123.itmowidgets.api

interface BackendStorage {
    fun getBackendAccessToken(): String?

    fun getBackendAccessExpiresAt(): Long

    fun getBackendRefreshToken(): String?

    fun getBackendRefreshExpiresAt(): Long

    fun getBackendAllow(): Boolean

    fun setBackendAccessToken(accessToken: String?)

    fun setBackendAccessExpiresAt(accessExpiresAt: Long)

    fun setBackendRefreshToken(refreshToken: String?)

    fun setBackendRefreshExpiresAt(refreshExpiresAt: Long)

    fun setBackendAllow(allow: Boolean)

    fun updateBackendTokens(response: BackendTokenResponse) {
        val currentMillis = System.currentTimeMillis()
        setBackendAccessToken(response.accessToken)
        setBackendAccessExpiresAt(currentMillis + response.accessTokenExpiresIn * 1000)
        setBackendRefreshToken(response.refreshToken)
        setBackendRefreshExpiresAt(currentMillis + response.refreshTokenExpiresIn * 1000)
    }

    fun clearBackendTokens() {
        setBackendAccessToken(null)
        setBackendAccessExpiresAt(0)
        setBackendRefreshToken(null)
        setBackendAccessExpiresAt(0)
    }
}