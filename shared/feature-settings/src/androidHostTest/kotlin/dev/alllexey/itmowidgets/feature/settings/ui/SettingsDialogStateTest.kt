package dev.alllexey.itmowidgets.feature.settings.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsDialogStateTest {

    @Test
    fun `every dialog comes back from its saved string`() {
        val dialogs = listOf(
            SettingsDialog.CalendarAccess(locked = false),
            SettingsDialog.CalendarAccess(locked = true),
        )

        for (dialog in dialogs) {
            val saved = SettingsDialogState(dialog).save()
            assertEquals(dialog, SettingsDialogState.restore(saved).dialog)
        }
    }

    @Test
    fun `nothing shown saves nothing and an unknown value restores nothing`() {
        assertNull(SettingsDialogState().save())
        // `choice:` was the choice dialog before the choices moved onto the page.
        for (saved in listOf(null, "", "choice:", "choice:QR_ANIMATION", "calendar")) {
            assertNull(SettingsDialogState.restore(saved).dialog)
        }
    }

    @Test
    fun `close hands the dialog out once`() {
        val state = SettingsDialogState()
        state.show(SettingsDialog.CalendarAccess(locked = false))

        assertEquals(SettingsDialog.CalendarAccess(locked = false), state.close())
        assertNull(state.close())
        assertNull(state.dialog)
    }
}
