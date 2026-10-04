package dev.alllexey.itmowidgets.upgrade.stores

import dev.alllexey.itmowidgets.core.qr.CustomSpoilerManager
import dev.alllexey.itmowidgets.upgrade.Upgrade22Fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** `files/qr_custom_spoiler/custom_spoiler.png`: the picked spoiler image stays. */
object CustomSpoilerUpgrade {

    fun check(fixture: Upgrade22Fixture) {
        val spoilers = CustomSpoilerManager(fixture.context)
        assertTrue(spoilers.hasCustomSpoiler())
        val bitmap = checkNotNull(spoilers.getCustomSpoilerBitmap())
        assertEquals(420, bitmap.width)
        assertEquals(420, bitmap.height)
    }
}
