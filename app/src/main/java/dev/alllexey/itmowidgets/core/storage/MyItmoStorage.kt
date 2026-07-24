package dev.alllexey.itmowidgets.core.storage

import android.util.Log
import api.myitmo.model.other.TokenResponse
import api.myitmo.storage.Storage
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.time.WallClock
import java.io.File
import java.time.Clock
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MyItmoStorage @Inject constructor(
    @TokenStorageFile tokenFile: File,
    private val tokenCipher: TokenCipher,
    @param:WallClock private val clock: Clock
) : Storage, SessionTokenStore {

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
        val now = clock.millis()
        updateState {
            TokenState(
                accessToken = tokenResponse.accessToken,
                accessExpiresAt = calculateTokenExpiration(
                    now,
                    tokenResponse.expiresIn.toLong()
                ),
                refreshToken = tokenResponse.refreshToken,
                refreshExpiresAt = calculateTokenExpiration(
                    now,
                    tokenResponse.refreshExpiresIn.toLong()
                ),
                idToken = tokenResponse.idToken
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
            Log.w(TAG, "Discarding unreadable token storage", error)
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

        fun String?.encode(): String {
            if (this == null) return NULL_VALUE
            return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(toByteArray(Charsets.UTF_8))
        }

        fun String.decode(): String? {
            if (this == NULL_VALUE) return null
            return Base64.getUrlDecoder()
                .decode(this)
                .toString(Charsets.UTF_8)
        }
    }
}
