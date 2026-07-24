package dev.alllexey.itmowidgets.feature.qr.data.local

import kotlinx.coroutines.flow.Flow

interface QrCodeLocalDataSource {

    fun observe(): Flow<String>

    fun get(): String?

    fun save(hex: String)

    fun clear()
}
