package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import dev.alllexey.itmoapi.bars.BarsStorage
import dev.alllexey.itmowidgets.core.storage.SecureStore
import kotlin.concurrent.Volatile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import dev.alllexey.itmoapi.bars.BarsClient as LibraryBarsClient

/**
 * Encrypted, owner-bound BARS session; a header saved for one ISU is never handed to another, and it is never
 * included in a toString.
 *
 * The value `"<isu>\n<header>"` lives in [SecureStore] under `"bars_tokens.enc"`: on Android that is the file of 2.2 in
 * `noBackupFilesDir`, sealed by the Keystore cipher with the same bytes, so the session of 2.2 reads as it is. A value
 * that cannot be read or belongs to another ISU reads as no session and stays until the next write or [clear].
 */
class BarsTokenStore(private val store: SecureStore) {
    private val lock = Mutex()

    suspend fun load(owner: Int): String? = lock.withLock {
        runCatching {
            val parts = store.read(NAME).orEmpty().split('\n', limit = 2)
            parts.getOrNull(1)?.takeIf { parts[0] == owner.toString() && LibraryBarsClient.isValidAuthorization(it) }
        }.getOrNull()
    }

    suspend fun install(owner: Int, header: String) = lock.withLock {
        require(LibraryBarsClient.isValidAuthorization(header))
        store.write(NAME, "$owner\n$header")
    }

    suspend fun clear() = lock.withLock { store.delete(NAME) }

    private companion object {
        /** The stable name of the session (G-03); never renamed, or every signed-in BARS user signs in again. */
        const val NAME = "bars_tokens.enc"
    }
}

/**
 * Library-facing view of the store, the only writer of `bars_tokens.enc`: the client sets the owner before any BARS
 * work. The library reaches it from the client's IO dispatcher.
 */
class OwnerBoundBarsStorage(private val tokens: BarsTokenStore) : BarsStorage {
    @Volatile var owner: Int? = null

    override suspend fun getAuthorization(): String? = owner?.let { tokens.load(it) }

    override suspend fun setAuthorization(authorization: String?) {
        val current = owner ?: return
        if (authorization == null) tokens.clear() else tokens.install(current, authorization)
    }

    suspend fun clear() = tokens.clear()

    override fun toString(): String = "OwnerBoundBarsStorage(redacted)"
}
