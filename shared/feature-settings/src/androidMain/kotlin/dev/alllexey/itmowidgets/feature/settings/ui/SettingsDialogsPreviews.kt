package dev.alllexey.itmowidgets.feature.settings.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/*
 * Baselines `SettingsDialog_<dialog>`: as for the pages, each dialog is a function called `SettingsDialog` in a holder
 * class of its own.
 */

/** [dialog] on the screen margin as the window centres it. */
@Composable
private fun SettingsDialogPreview(dialog: SettingsDialog) = ItmoPreview {
    Box(Modifier.padding(ItmoTheme.spacing.section)) {
        SettingsDialogBody(dialog = dialog, windowed = false, onConfirm = {}, onDismiss = {}, actions = SettingsActions())
    }
}

internal class SettingsDialogCalendarAccessPreview {
    @Preview(name = "calendar-access")
    @Composable
    fun SettingsDialog() = SettingsDialogPreview(SettingsDialog.CalendarAccess(locked = false))
}

internal class SettingsDialogCalendarAccessLockedPreview {
    @Preview(name = "calendar-access-locked")
    @Composable
    fun SettingsDialog() = SettingsDialogPreview(SettingsDialog.CalendarAccess(locked = true))
}
