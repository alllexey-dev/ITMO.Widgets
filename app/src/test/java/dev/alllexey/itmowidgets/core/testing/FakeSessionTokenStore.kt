package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.session.SessionTokens

/**
 * The ITMO.ID token storage of one test. A session is [signedIn] while it holds a [refreshToken]; the ID token comes
 * from the last [tokens] or else [idToken]. [clearTokens] appends "tokens" to [clearOrder] when the test traces order.
 */
class FakeSessionTokenStore(
    signedIn: Boolean = true,
    private val idToken: String? = null,
    private val clearOrder: MutableList<String>? = null,
) : SessionTokenStore {
    var refreshToken: String? = if (signedIn) STORED_REFRESH_TOKEN else null
    var tokens: SessionTokens? = null
    var cleared = false

    var signedIn: Boolean
        get() = refreshToken != null
        set(value) {
            refreshToken = if (value) STORED_REFRESH_TOKEN else null
        }

    override fun hasRefreshToken(): Boolean = refreshToken != null

    override fun getIdToken(): String? = tokens?.idToken ?: idToken

    override fun replaceWithRefreshToken(refreshToken: String) {
        this.refreshToken = refreshToken
    }

    override fun replaceWithTokens(tokens: SessionTokens) {
        this.tokens = tokens
        refreshToken = tokens.refreshToken
    }

    override fun clearTokens() {
        clearOrder?.add("tokens")
        refreshToken = null
        tokens = null
        cleared = true
    }

    private companion object {
        const val STORED_REFRESH_TOKEN = "stored-token"
    }
}
