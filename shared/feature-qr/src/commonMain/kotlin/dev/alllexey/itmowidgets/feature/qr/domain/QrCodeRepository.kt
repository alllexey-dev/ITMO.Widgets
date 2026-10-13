package dev.alllexey.itmowidgets.feature.qr.domain

import dev.alllexey.itmowidgets.core.result.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * A cached pass and the wall time it was loaded. It counts as fresh for [FRESH_MILLIS] after that: a local cache
 * rule that decides when the app asks My ITMO again, not the pass's validity, which nobody outside ITMO knows.
 */
data class QrCodeSnapshot(val hex: String, val savedAtMillis: Long) {
    val expiresAtMillis: Long get() = savedAtMillis + FRESH_MILLIS

    companion object {
        const val FRESH_MILLIS: Long = 60 * 60 * 1000L
    }
}

interface QrCodeRepository {

    /** The cached pass while it is fresh, for the widgets; null once it is older than [QrCodeSnapshot.FRESH_MILLIS]. */
    suspend fun currentQr(): QrCodeSnapshot?

    /** The last pass loaded, whatever its age: the pass sheet shows it with its time when no newer one comes. */
    suspend fun lastQr(): QrCodeSnapshot?

    fun observeQrHex(): Flow<String>

    /** Cached code, or null when there is nothing to show yet. */
    suspend fun currentQrHex(allowExpired: Boolean = false): String?

    suspend fun refreshQrHex(force: Boolean = false): AppResult<Unit>

    fun clearCache()
}
