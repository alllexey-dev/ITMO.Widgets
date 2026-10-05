package dev.alllexey.itmowidgets.feature.qr.data.local

import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeSnapshot
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapNotNull

private const val QR_CACHE_EXPIRATION_MS = 60 * 60 * 1000L

internal data class QrCacheEntry(
    val hex: String,
    val timestamp: Long
)

/**
 * The last pass in `cacheDir/qr_hex` ([AppDirectories.cache]) as `<epochMillis>|<hex>`, the 2.2 format byte for
 * byte; the v2.0 file with the hex alone still reads. A pass expires 60 minutes after it was saved on the wall
 * [clock], never on academic time.
 */
class QrCodeLocalDataSourceImpl internal constructor(
    private val file: AtomicTextFile,
    private val clock: Clock
) : QrCodeLocalDataSource {

    constructor(directories: AppDirectories, clock: Clock) : this(AtomicTextFile(directories.cache / FILE_NAME), clock)

    private val _flow = MutableStateFlow(readFromDisk())
    private val flow = _flow.asStateFlow()

    override fun observe(): Flow<String> {
        return flow.mapNotNull { entry ->
            if (entry == null) return@mapNotNull null
            if (isExpired(entry)) return@mapNotNull null
            entry.hex
        }
    }

    override fun snapshot(): QrCodeSnapshot? {
        val entry = _flow.value ?: return null
        return if (isExpired(entry)) null else QrCodeSnapshot(entry.hex, entry.timestamp + QR_CACHE_EXPIRATION_MS)
    }

    override fun get(allowExpired: Boolean): String? {
        val entry = _flow.value ?: return null
        return if (allowExpired || !isExpired(entry)) entry.hex else null
    }

    override fun save(hex: String) {
        val entry = QrCacheEntry(
            hex = hex,
            timestamp = nowMillis()
        )

        try {
            file.write(serialize(entry))
            _flow.value = entry
        } catch (_: Exception) {}
    }

    override fun clear() {
        try {
            file.write(null)
        } catch (_: Exception) {}
        _flow.value = null
    }

    internal fun isExpired(entry: QrCacheEntry): Boolean {
        return nowMillis() - entry.timestamp >= QR_CACHE_EXPIRATION_MS
    }

    private fun nowMillis(): Long = clock.now().toEpochMilliseconds()

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
                    QrCacheEntry(hex = legacyHex, timestamp = nowMillis())
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
            file.read()?.let(::deserialize)
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val FILE_NAME = "qr_hex"
        const val MIN_QR_LENGTH = 6
    }
}
