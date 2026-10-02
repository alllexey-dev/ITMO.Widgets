package dev.alllexey.itmowidgets.core.navigation

import org.junit.Assert.assertNotNull
import org.junit.Test

class QuickSettingsTilesTest {

    @Test
    fun `the named tile service still exists`() {
        // A rename would otherwise fail only on the device, when the add request is made.
        assertNotNull(Class.forName(QuickSettingsTiles.QR_PASS))
    }
}
