package dev.alllexey.itmowidgets.feature.qr.domain

import dev.alllexey.itmowidgets.core.result.AppResult
import kotlinx.coroutines.flow.Flow

/** Local validity deadline; expired passes must not remain visible on the screen. */
data class QrCodeSnapshot(val hex: String, val expiresAtMillis: Long)

interface QrCodeRepository {

    suspend fun currentQr(): QrCodeSnapshot?

    fun observeQrHex(): Flow<String>

    /** Cached code, or null when there is nothing to show yet. */
    suspend fun currentQrHex(allowExpired: Boolean = false): String?

    suspend fun refreshQrHex(force: Boolean = false): AppResult<Unit>

    fun clearCache()
}
