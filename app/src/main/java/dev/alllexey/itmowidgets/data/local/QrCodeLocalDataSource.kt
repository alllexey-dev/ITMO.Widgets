package dev.alllexey.itmowidgets.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

interface QrCodeLocalDataSource {

    fun observe(): Flow<String>

    fun get(): String?

    fun save(hex: String)

    fun clear()
}
