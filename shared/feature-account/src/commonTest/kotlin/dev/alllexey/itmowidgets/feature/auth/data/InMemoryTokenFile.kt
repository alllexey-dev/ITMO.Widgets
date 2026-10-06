package dev.alllexey.itmowidgets.feature.auth.data

import dev.alllexey.itmoapi.itmoid.TokenSet
import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.session.SessionTokens
import kotlin.time.Instant

/**
 * The five fields Android's `MyItmoStorage` keeps in `myitmo_tokens.enc`, in memory: the client's [TokenStorage] and
 * the session's [SessionTokenStore] over one state, expiries as epoch milliseconds. A refresh-token-only state is no
 * 2.x session, as there.
 */
internal class InMemoryTokenFile : TokenStorage, SessionTokenStore {
    var accessToken: String? = null
        private set
    var accessExpiresAt = 0L
        private set
    var refreshToken: String? = null
        private set
    var refreshExpiresAt = 0L
        private set
    private var idToken: String? = null

    override suspend fun read(): TokenSet? {
        val access = accessToken?.takeIf(String::isNotBlank) ?: return null
        val refresh = refreshToken?.takeIf(String::isNotBlank) ?: return null
        val identity = idToken?.takeIf(String::isNotBlank) ?: return null
        return TokenSet(
            accessToken = access,
            accessExpiresAt = Instant.fromEpochMilliseconds(accessExpiresAt),
            refreshToken = refresh,
            refreshExpiresAt = Instant.fromEpochMilliseconds(refreshExpiresAt),
            idToken = identity
        )
    }

    override suspend fun write(tokens: TokenSet?) {
        clearTokens()
        if (tokens == null) return
        accessToken = tokens.accessToken
        accessExpiresAt = tokens.accessExpiresAt.toEpochMilliseconds()
        refreshToken = tokens.refreshToken
        refreshExpiresAt = tokens.refreshExpiresAt.toEpochMilliseconds()
        idToken = tokens.idToken
    }

    override fun hasRefreshToken(): Boolean = refreshToken != null

    override fun getIdToken(): String? = idToken

    override fun replaceWithRefreshToken(refreshToken: String) {
        clearTokens()
        this.refreshToken = refreshToken
    }

    override fun replaceWithTokens(tokens: SessionTokens) {
        error("The session writes through the client's TokenStorage")
    }

    override fun clearTokens() {
        accessToken = null
        accessExpiresAt = 0L
        refreshToken = null
        refreshExpiresAt = 0L
        idToken = null
    }
}
