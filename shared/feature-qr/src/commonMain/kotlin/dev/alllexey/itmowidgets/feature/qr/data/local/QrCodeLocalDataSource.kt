package dev.alllexey.itmowidgets.feature.qr.data.local

import kotlinx.coroutines.flow.Flow
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeSnapshot

interface QrCodeLocalDataSource {

    /** The cached pass; an expired one only with [allowExpired]. */
    fun snapshot(allowExpired: Boolean = false): QrCodeSnapshot?

    fun observe(): Flow<String>

    fun get(allowExpired: Boolean = false): String?

    fun save(hex: String)

    fun clear()
}
