package dev.alllexey.itmowidgets.core.storage

import api.myitmo.model.other.TokenResponse
import api.myitmo.storage.Storage
import dev.alllexey.itmoapi.itmoid.TokenSet
import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.session.SessionTokens
import dev.alllexey.itmowidgets.core.time.WallClock
import java.io.File
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.io.encoding.Base64
import kotlin.time.Instant

/**
 * The only writer of `myitmo_tokens.enc`: the MyItmoApi 1.x [Storage], the 2.x [TokenStorage] and the session's
 * [SessionTokenStore] read and write the same 5-field state under one lock, so a write through one is what the
 * others read next. The 2.x client's `tokens` is the only proactive refresher; 1.x still refreshes on demand for
 * the areas it serves, which at worst rotates the refresh token twice (ITMO.ID does not revoke the old one).
 */
@Singleton
class MyItmoStorage @Inject constructor(
    @TokenStorageFile tokenFile: File,
    private val tokenCipher: TokenCipher,
    @param:WallClock private val clock: Clock,
    private val log: AppLog
) : Storage, TokenStorage, SessionTokenStore {

    private val encryptedFile = AtomicTextFile(tokenFile)
    private var cachedState: TokenState? = null

    @Synchronized
    override fun getAccessToken(): String? = currentState().accessToken

    @Synchronized
    override fun getAccessExpiresAt(): Long = currentState().accessExpiresAt

    @Synchronized
    override fun getRefreshToken(): String? = currentState().refreshToken

    @Synchronized
    override fun getRefreshExpiresAt(): Long = currentState().refreshExpiresAt

    @Synchronized
    override fun getIdToken(): String? = currentState().idToken

    @Synchronized
    override fun setAccessToken(accessToken: String?) {
        updateState { it.copy(accessToken = accessToken) }
    }

    @Synchronized
    override fun setAccessExpiresAt(accessExpiresAt: Long) {
        updateState { it.copy(accessExpiresAt = accessExpiresAt) }
    }

    @Synchronized
    override fun setRefreshToken(refreshToken: String?) {
        updateState { it.copy(refreshToken = refreshToken) }
    }

    @Synchronized
    override fun setRefreshExpiresAt(refreshExpiresAt: Long) {
        updateState { it.copy(refreshExpiresAt = refreshExpiresAt) }
    }

    @Synchronized
    override fun setIdToken(idToken: String?) {
        updateState { it.copy(idToken = idToken) }
    }

    @Synchronized
    override fun update(tokenResponse: TokenResponse) {
        replaceWithTokens(
            SessionTokens(
                accessToken = tokenResponse.accessToken,
                accessExpiresInSeconds = tokenResponse.expiresIn,
                refreshToken = tokenResponse.refreshToken,
                refreshExpiresInSeconds = tokenResponse.refreshExpiresIn,
                idToken = tokenResponse.idToken
            )
        )
    }

    @Synchronized
    override fun replaceWithTokens(tokens: SessionTokens) {
        val now = clock.millis()
        persist(
            TokenState(
                accessToken = tokens.accessToken,
                accessExpiresAt = calculateTokenExpiration(
                    now,
                    tokens.accessExpiresInSeconds
                ),
                refreshToken = tokens.refreshToken,
                refreshExpiresAt = calculateTokenExpiration(
                    now,
                    tokens.refreshExpiresInSeconds
                ),
                idToken = tokens.idToken
            )
        )
    }

    /** Null unless all three tokens are present: a refresh-token-only state is not a 2.x session yet. */
    override suspend fun read(): TokenSet? = synchronized(this) {
        val state = currentState()
        val accessToken = state.accessToken?.takeIf(String::isNotBlank) ?: return null
        val refreshToken = state.refreshToken?.takeIf(String::isNotBlank) ?: return null
        val idToken = state.idToken?.takeIf(String::isNotBlank) ?: return null
        TokenSet(
            accessToken = accessToken,
            accessExpiresAt = Instant.fromEpochMilliseconds(state.accessExpiresAt),
            refreshToken = refreshToken,
            refreshExpiresAt = Instant.fromEpochMilliseconds(state.refreshExpiresAt),
            idToken = idToken
        )
    }

    /** Expiry instants are stored as epoch milliseconds, as 2.2 stored them. */
    override suspend fun write(tokens: TokenSet?) = synchronized(this) {
        if (tokens == null) {
            clearTokens()
        } else {
            persist(
                TokenState(
                    accessToken = tokens.accessToken,
                    accessExpiresAt = tokens.accessExpiresAt.toEpochMilliseconds(),
                    refreshToken = tokens.refreshToken,
                    refreshExpiresAt = tokens.refreshExpiresAt.toEpochMilliseconds(),
                    idToken = tokens.idToken
                )
            )
        }
    }

    @Synchronized
    override fun hasRefreshToken(): Boolean {
        return currentState().refreshToken != null
    }

    @Synchronized
    override fun replaceWithRefreshToken(refreshToken: String) {
        persist(
            TokenState(
                refreshToken = refreshToken
            )
        )
    }

    @Synchronized
    override fun clearTokens() {
        encryptedFile.write(null)
        cachedState = TokenState()
    }

    private fun currentState(): TokenState {
        cachedState?.let { return it }
        return loadState().also { cachedState = it }
    }

    private fun updateState(transform: (TokenState) -> TokenState) {
        persist(transform(currentState()))
    }

    private fun persist(state: TokenState) {
        if (state == TokenState()) {
            encryptedFile.write(null)
        } else {
            encryptedFile.write(tokenCipher.encrypt(state.serialize()))
        }
        cachedState = state
    }

    private fun loadState(): TokenState {
        val encrypted = runCatching(encryptedFile::read).getOrNull() ?: return TokenState()
        return runCatching {
            TokenState.deserialize(tokenCipher.decrypt(encrypted))
        }.onFailure { error ->
            log.warn(TAG, "Discarding unreadable token storage", error)
            runCatching { encryptedFile.write(null) }
        }.getOrDefault(TokenState())
    }

    private data class TokenState(
        val accessToken: String? = null,
        val accessExpiresAt: Long = 0L,
        val refreshToken: String? = null,
        val refreshExpiresAt: Long = 0L,
        val idToken: String? = null
    ) {

        fun serialize(): String {
            return listOf(
                accessToken.encode(),
                accessExpiresAt.toString(),
                refreshToken.encode(),
                refreshExpiresAt.toString(),
                idToken.encode()
            ).joinToString(SEPARATOR)
        }

        companion object {
            fun deserialize(value: String): TokenState {
                val fields = value.split(SEPARATOR)
                require(fields.size == FIELD_COUNT) {
                    "Unexpected token storage field count"
                }
                return TokenState(
                    accessToken = fields[0].decode(),
                    accessExpiresAt = fields[1].toLong(),
                    refreshToken = fields[2].decode(),
                    refreshExpiresAt = fields[3].toLong(),
                    idToken = fields[4].decode()
                )
            }
        }
    }

    private companion object {
        const val TAG = "MyItmoStorage"
        const val NULL_VALUE = "~"
        const val SEPARATOR = "\n"
        const val FIELD_COUNT = 5

        // 2.2 wrote the fields unpadded and read them with a decoder that also accepts padding. A stricter
        // reader would discard the stored session and sign the user out.
        val FIELD_ENCODER = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        val FIELD_DECODER = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

        fun String?.encode(): String {
            if (this == null) return NULL_VALUE
            return FIELD_ENCODER.encode(encodeToByteArray())
        }

        fun String.decode(): String? {
            if (this == NULL_VALUE) return null
            return FIELD_DECODER.decode(this).decodeToString()
        }
    }
}
