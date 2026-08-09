package dev.alllexey.itmowidgets.feature.qr.domain

import dev.alllexey.itmowidgets.core.result.AppResult
import kotlinx.coroutines.flow.Flow

interface QrCodeRepository {

    fun observeQrHex(): Flow<String>

    /** Cached code, or null when there is nothing to show yet. */
    suspend fun currentQrHex(allowExpired: Boolean = false): String?

    suspend fun refreshQrHex(force: Boolean = false): AppResult<Unit>

    fun clearCache()
}
