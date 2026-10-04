package dev.alllexey.itmowidgets.core.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardTest {

    @Test
    fun `Android 12 and older need the app's own copy confirmation`() {
        assertTrue(copyNeedsConfirmation(sdkInt = 26))
        assertTrue(copyNeedsConfirmation(sdkInt = 32))
    }

    @Test
    fun `Android 13 and newer confirm the copy themselves`() {
        assertFalse(copyNeedsConfirmation(sdkInt = 33))
        assertFalse(copyNeedsConfirmation(sdkInt = 35))
    }
}
