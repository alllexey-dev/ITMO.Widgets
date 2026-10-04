package dev.alllexey.itmowidgets.upgrade.stores

import dev.alllexey.itmowidgets.feature.qr.data.local.QrCodeLocalDataSourceImpl
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrBitmapCacheImpl
import dev.alllexey.itmowidgets.upgrade.Captured22
import dev.alllexey.itmowidgets.upgrade.Upgrade22Fixture
import org.junit.Assert.assertTrue

/** Caches `cache/qr_hex` and `cache/bitmap_cache/`: read or ignored, never a crash. */
object QrCacheUpgrade {

    fun check(fixture: Upgrade22Fixture) {
        val hex = QrCodeLocalDataSourceImpl(fixture.context, fixture.clock).get(allowExpired = true)
        assertTrue("qr_hex read as $hex", hex == null || hex == Captured22.QR_HEX)

        val bitmap = QrBitmapCacheImpl(fixture.context).getBitmap("noise", "upgrade-2.2")
        assertTrue(bitmap == null || bitmap.width == 8 && bitmap.height == 8)
    }
}
