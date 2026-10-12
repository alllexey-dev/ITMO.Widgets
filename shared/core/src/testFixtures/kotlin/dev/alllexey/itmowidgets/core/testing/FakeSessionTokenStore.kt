package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.session.SessionTokenStore

/**
 * The ITMO.ID token storage of one test. A session is [signedIn] while it holds a [refreshToken]; the ID token is
 * [idToken]. [clearTokens] appends "tokens" to [clearOrder] when the test traces order.
 */
class FakeSessionTokenStore(
    signedIn: Boolean = true,
    private val idToken: String? = null,
    private val clearOrder: MutableList<String>? = null,
) : SessionTokenStore {
    var refreshToken: String? = if (signedIn) STORED_REFRESH_TOKEN else null
    var cleared = false

    var signedIn: Boolean
        get() = refreshToken != null
        set(value) {
            refreshToken = if (value) STORED_REFRESH_TOKEN else null
        }

    override fun hasRefreshToken(): Boolean = refreshToken != null

    override fun getIdToken(): String? = idToken

    override fun replaceWithRefreshToken(refreshToken: String) {
        this.refreshToken = refreshToken
    }

    override fun clearTokens() {
        clearOrder?.add("tokens")
        refreshToken = null
        cleared = true
    }

    private companion object {
        const val STORED_REFRESH_TOKEN = "stored-token"
    }
}
