package dev.alllexey.itmowidgets.feature.settings.ui

import dev.alllexey.itmowidgets.feature.settings.presentation.SettingRowId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsDialogStateTest {

    @Test
    fun `every dialog comes back from its saved string`() {
        val dialogs = listOf(
            SettingsDialog.Choice(SettingRowId.FULL_WIDGET_TEXT_SIZE),
            SettingsDialog.Choice(SettingRowId.QR_ANIMATION),
            SettingsDialog.Choice(SettingRowId.FRIENDS_SHARING),
            SettingsDialog.CustomServicesConsent,
            SettingsDialog.BackgroundWorkHint,
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
        for (saved in listOf(null, "", "choice:", "choice:REMOVED_ROW", "calendar")) {
            assertNull(SettingsDialogState.restore(saved).dialog)
        }
    }

    @Test
    fun `close hands the dialog out once`() {
        val state = SettingsDialogState()
        state.show(SettingsDialog.BackgroundWorkHint)

        assertEquals(SettingsDialog.BackgroundWorkHint, state.close())
        assertNull(state.close())
        assertNull(state.dialog)
    }
}
