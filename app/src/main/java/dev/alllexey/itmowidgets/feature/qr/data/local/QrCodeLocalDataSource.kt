package dev.alllexey.itmowidgets.feature.qr.data.local

import kotlinx.coroutines.flow.Flow

interface QrCodeLocalDataSource {

    fun observe(): Flow<String>

    fun get(allowExpired: Boolean = false): String?

    fun save(hex: String)

    fun clear()
}
