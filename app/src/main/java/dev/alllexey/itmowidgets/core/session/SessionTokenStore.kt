package dev.alllexey.itmowidgets.core.session

interface SessionTokenStore {

    fun hasRefreshToken(): Boolean

    /** Raw ITMO.ID token; its claims describe the signed-in user. */
    fun getIdToken(): String?

    fun replaceWithRefreshToken(refreshToken: String)

    fun clearTokens()
}
