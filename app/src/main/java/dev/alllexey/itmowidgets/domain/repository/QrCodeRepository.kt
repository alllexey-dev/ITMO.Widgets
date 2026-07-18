package dev.alllexey.itmowidgets.domain.repository

import kotlinx.coroutines.flow.Flow

interface QrCodeRepository {

    // returns QR code HEX value
    fun observeQrHex(): Flow<String>

    suspend fun refreshQrHex(force: Boolean = false)

    fun clearCaches()
}
