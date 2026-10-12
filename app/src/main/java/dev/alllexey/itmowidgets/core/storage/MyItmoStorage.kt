package dev.alllexey.itmowidgets.core.storage

import dev.alllexey.itmoapi.itmoid.TokenSet
import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import java.io.File
import javax.crypto.AEADBadTagException
import kotlin.io.encoding.Base64
import kotlin.time.Instant

/**
 * The only writer of `myitmo_tokens.enc`: the MyItmoApi 2.x [TokenStorage] and the session's [SessionTokenStore]
 * read and write the same 5-field state under one lock, so a write through one is what the other reads next. The
 * 2.x client's `tokens` is the only refresher.
 *
 * Only content that cannot be parsed or authenticated is discarded. A Keystore or file failure (`KeyStoreException`,
 * `ProviderException`, an `IOException`) reads as no session for that call alone and keeps the file, so a transient
 * failure never signs the user out; the next read tries again.
 */
class MyItmoStorage(
    tokenFile: File,
    private val tokenCipher: TokenCipher,
    private val log: AppLog
) : TokenStorage, SessionTokenStore {

    private val encryptedFile = AtomicTextFile(tokenFile)
    private var cachedState: TokenState? = null

    /** The stored fields as 2.2 laid them out, a refresh-token-only state included, which [read] hides. */
    @Synchronized
    internal fun getAccessToken(): String? = currentState().accessToken

    @Synchronized
    internal fun getAccessExpiresAt(): Long = currentState().accessExpiresAt

    @Synchronized
    internal fun getRefreshToken(): String? = currentState().refreshToken

    @Synchronized
    internal fun getRefreshExpiresAt(): Long = currentState().refreshExpiresAt

    @Synchronized
    override fun getIdToken(): String? = currentState().idToken

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
        // Not cached when unavailable, so the next call reads the kept file again.
        return loadState()?.also { cachedState = it } ?: TokenState()
    }

    private fun persist(state: TokenState) {
        if (state == TokenState()) {
            encryptedFile.write(null)
        } else {
            encryptedFile.write(tokenCipher.encrypt(state.serialize()))
        }
        cachedState = state
    }

    /** The stored state, or null when the file or the Keystore cannot be read now. */
    private fun loadState(): TokenState? = try {
        encryptedFile.read()?.let { TokenState.deserialize(tokenCipher.decrypt(it)) } ?: TokenState()
    } catch (error: IllegalArgumentException) {
        discard(error)
    } catch (error: AEADBadTagException) {
        // The sealed text does not match the key: it never decrypts again.
        discard(error)
    } catch (error: Exception) {
        log.warn(TAG, "Token storage unavailable; keeping it for the next read", error)
        null
    }

    private fun discard(error: Exception): TokenState {
        log.warn(TAG, "Discarding unreadable token storage", error)
        runCatching { encryptedFile.write(null) }
        return TokenState()
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
