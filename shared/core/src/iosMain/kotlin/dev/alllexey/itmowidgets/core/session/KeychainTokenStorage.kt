package dev.alllexey.itmowidgets.core.session

import dev.alllexey.itmoapi.itmoid.TokenSet
import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.storage.SecureStore
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The iOS session: the MyItmoApi 2.x [TokenStorage] and the session's [SessionTokenStore] over one [SecureStore]
 * item, [ITEM] (the Keychain on iOS, shared by the app and the notification service). Holds no copy: every read goes
 * to the store, so a refresh written by another process is what the next read sees, and the refresh guard's re-read
 * after locking is a real one.
 *
 * The value is serialised as Android's `MyItmoStorage` writes `myitmo_tokens.enc` before sealing it: five lines,
 * each token base64url without padding (`~` for none), each expiry in epoch milliseconds. A value that does not
 * parse is dropped and reads as no session; a store failure (a locked Keychain) is thrown and drops nothing.
 */
class KeychainTokenStorage(
    private val store: SecureStore,
    private val clock: Clock,
    private val log: AppLog
) : TokenStorage, SessionTokenStore {

    /** Null unless all three tokens are present: a refresh-token-only state is not a 2.x session yet. */
    override suspend fun read(): TokenSet? {
        val state = load()
        return TokenSet(
            accessToken = state.accessToken?.takeIf(String::isNotBlank) ?: return null,
            accessExpiresAt = Instant.fromEpochMilliseconds(state.accessExpiresAt),
            refreshToken = state.refreshToken?.takeIf(String::isNotBlank) ?: return null,
            refreshExpiresAt = Instant.fromEpochMilliseconds(state.refreshExpiresAt),
            idToken = state.idToken?.takeIf(String::isNotBlank) ?: return null
        )
    }

    override suspend fun write(tokens: TokenSet?) {
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

    override fun hasRefreshToken(): Boolean = load().refreshToken != null

    override fun getIdToken(): String? = load().idToken

    override fun replaceWithRefreshToken(refreshToken: String) = persist(TokenState(refreshToken = refreshToken))

    override fun replaceWithTokens(tokens: SessionTokens) {
        val now = clock.now().toEpochMilliseconds()
        persist(
            TokenState(
                accessToken = tokens.accessToken,
                accessExpiresAt = expiresAt(now, tokens.accessExpiresInSeconds),
                refreshToken = tokens.refreshToken,
                refreshExpiresAt = expiresAt(now, tokens.refreshExpiresInSeconds),
                idToken = tokens.idToken
            )
        )
    }

    override fun clearTokens() = store.delete(ITEM)

    private fun load(): TokenState {
        val value = store.read(ITEM) ?: return TokenState()
        return try {
            TokenState.deserialize(value)
        } catch (error: IllegalArgumentException) {
            // Never log the value; the type says enough.
            log.warn(TAG, "Discarding unreadable session: ${error::class.simpleName}")
            store.delete(ITEM)
            TokenState()
        }
    }

    private fun persist(state: TokenState) {
        if (state == TokenState()) store.delete(ITEM) else store.write(ITEM, state.serialize())
    }

    override fun toString(): String = "KeychainTokenStorage(redacted)"

    private data class TokenState(
        val accessToken: String? = null,
        val accessExpiresAt: Long = 0L,
        val refreshToken: String? = null,
        val refreshExpiresAt: Long = 0L,
        val idToken: String? = null
    ) {
        fun serialize(): String = listOf(
            accessToken.encode(),
            accessExpiresAt.toString(),
            refreshToken.encode(),
            refreshExpiresAt.toString(),
            idToken.encode()
        ).joinToString(SEPARATOR)

        override fun toString(): String = "TokenState(redacted)"

        companion object {
            /** Throws [IllegalArgumentException] for anything but five well-formed fields. */
            fun deserialize(value: String): TokenState {
                val fields = value.split(SEPARATOR)
                require(fields.size == FIELD_COUNT) { "Unexpected session field count" }
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

    companion object {

        /** The `SecureStore` name of the session; Android's file is `myitmo_tokens.enc`. */
        const val ITEM = "myitmo_tokens"

        private const val TAG = "KeychainTokenStorage"
        private const val NULL_VALUE = "~"
        private const val SEPARATOR = "\n"
        private const val FIELD_COUNT = 5
        private const val MILLIS_PER_SECOND = 1000L

        // As MyItmoStorage: written unpadded, read with or without padding.
        private val FIELD_ENCODER = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        private val FIELD_DECODER = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

        private fun String?.encode(): String = this?.let { FIELD_ENCODER.encode(it.encodeToByteArray()) } ?: NULL_VALUE

        private fun String.decode(): String? = if (this == NULL_VALUE) null else FIELD_DECODER.decode(this).decodeToString()

        /** [now] plus [lifetimeSeconds], saturated as Android's `calculateTokenExpiration`. */
        private fun expiresAt(now: Long, lifetimeSeconds: Long): Long {
            val lifetime = lifetimeSeconds * MILLIS_PER_SECOND
            val overflows = lifetimeSeconds != 0L && lifetime / lifetimeSeconds != MILLIS_PER_SECOND
            return if (overflows || lifetime > Long.MAX_VALUE - now) Long.MAX_VALUE else now + lifetime
        }
    }
}
