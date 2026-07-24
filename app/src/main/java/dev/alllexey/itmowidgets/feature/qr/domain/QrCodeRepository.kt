package dev.alllexey.itmowidgets.feature.qr.domain

import dev.alllexey.itmowidgets.core.result.AppResult
import kotlinx.coroutines.flow.Flow

interface QrCodeRepository {

    fun observeQrHex(): Flow<String>

    suspend fun refreshQrHex(force: Boolean = false): AppResult<Unit>

    fun clearCache()
}
