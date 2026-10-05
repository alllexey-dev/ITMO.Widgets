package dev.alllexey.itmowidgets.upgrade.stores

import dev.alllexey.itmowidgets.core.diagnostics.AndroidAppLog
import dev.alllexey.itmowidgets.core.storage.AndroidAppDirectories
import dev.alllexey.itmowidgets.feature.qr.data.local.QrCodeLocalDataSourceImpl
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrBitmapCacheImpl
import dev.alllexey.itmowidgets.upgrade.Captured22
import dev.alllexey.itmowidgets.upgrade.Upgrade22Fixture
import java.io.File
import kotlin.time.Clock
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Caches `cache/qr_hex` and `cache/bitmap_cache/`: read or ignored, never a crash. The common `qr_hex` store reads
 * the 2.2 file, writes the same `<epochMillis>|<hex>` format and reads it back.
 */
object QrCacheUpgrade {

    fun check(fixture: Upgrade22Fixture) {
        val clock = object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(fixture.clock.millis())
        }
        val directories = AndroidAppDirectories(fixture.context)

        val hex = QrCodeLocalDataSourceImpl(directories, clock).get(allowExpired = true)
        assertTrue("qr_hex read as $hex", hex == null || hex == Captured22.QR_HEX)

        QrCodeLocalDataSourceImpl(directories, clock).save(Captured22.QR_HEX)
        assertEquals(
            "${fixture.clock.millis()}|${Captured22.QR_HEX}",
            File(fixture.cacheDir, "qr_hex").readText()
        )
        assertEquals(Captured22.QR_HEX, QrCodeLocalDataSourceImpl(directories, clock).get())

        val bitmap = QrBitmapCacheImpl(fixture.context, AndroidAppLog()).getBitmap("noise", "upgrade-2.2")
        assertTrue(bitmap == null || bitmap.width == 8 && bitmap.height == 8)
    }
}
