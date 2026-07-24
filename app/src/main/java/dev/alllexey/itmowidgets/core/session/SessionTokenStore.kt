package dev.alllexey.itmowidgets.core.session

interface SessionTokenStore {

    fun hasRefreshToken(): Boolean

    fun replaceWithRefreshToken(refreshToken: String)

    fun clearTokens()
}
