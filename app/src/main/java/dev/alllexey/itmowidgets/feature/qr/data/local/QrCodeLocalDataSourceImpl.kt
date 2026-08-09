package dev.alllexey.itmowidgets.feature.qr.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.alllexey.itmowidgets.core.time.WallClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapNotNull
import java.io.File
import java.time.Clock
import javax.inject.Inject

private const val QR_CACHE_EXPIRATION_MS = 60 * 60 * 1000L
internal data class QrCacheEntry(
    val hex: String,
    val timestamp: Long
)

class QrCodeLocalDataSourceImpl @Inject constructor(
    @ApplicationContext context: Context,
    @param:WallClock private val clock: Clock
) : QrCodeLocalDataSource {

    private val file = File(context.cacheDir, "qr_hex")

    private val _flow = MutableStateFlow(readFromDisk())
    private val flow = _flow.asStateFlow()

    override fun observe(): Flow<String> {
        return flow.mapNotNull { entry ->
            if (entry == null) return@mapNotNull null
            if (isExpired(entry)) return@mapNotNull null
            entry.hex
        }
    }

    override fun get(allowExpired: Boolean): String? {
        val entry = _flow.value ?: return null
        return if (allowExpired || !isExpired(entry)) entry.hex else null
    }

    override fun save(hex: String) {
        val entry = QrCacheEntry(
            hex = hex,
            timestamp = clock.millis()
        )

        try {
            file.writeText(serialize(entry))
            _flow.value = entry
        } catch (_: Exception) {}
    }

    override fun clear() {
        file.delete()
        _flow.value = null
    }

    internal fun isExpired(entry: QrCacheEntry): Boolean {
        return clock.millis() - entry.timestamp > QR_CACHE_EXPIRATION_MS
    }

    private fun serialize(entry: QrCacheEntry): String {
        return "${entry.timestamp}|${entry.hex}"
    }

    private fun deserialize(raw: String): QrCacheEntry? {
        return try {
            val value = raw.trim()
            val parts = value.split("|", limit = 2)
            if (parts.size == 1) {
                // v2.0 stored only the QR payload. Keep it during the refactor so an
                // app update does not blank an otherwise working home-screen pass.
                return value.takeIf { it.length >= MIN_QR_LENGTH }?.let { legacyHex ->
                    QrCacheEntry(hex = legacyHex, timestamp = clock.millis())
                }
            }
            QrCacheEntry(
                timestamp = parts[0].toLong(),
                hex = parts[1]
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun readFromDisk(): QrCacheEntry? {
        return try {
            if (!file.exists()) return null
            deserialize(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val MIN_QR_LENGTH = 6
    }
}
