package dev.alllexey.itmowidgets.feature.settings.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingRowId
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsUiState
import dev.alllexey.itmowidgets.feature.settings.ui.preview.SettingsPreviewData

/*
 * Baselines `SettingsDialog_<dialog>`: as for the pages, each dialog is a function called `SettingsDialog` in a holder
 * class of its own.
 */

/** [dialog] over the rows of [page], on the screen margin as the window centres it. */
@Composable
private fun SettingsDialogPreview(dialog: SettingsDialog, page: SettingsUiState = SettingsPreviewData.Root) = ItmoPreview {
    Box(Modifier.padding(ItmoTheme.spacing.section)) {
        SettingsDialogBody(
            dialog = dialog,
            sections = page.sections,
            windowed = false,
            onConfirm = {},
            onDismiss = {},
            actions = SettingsActions(),
        )
    }
}

internal class SettingsDialogTextSizePreview {
    @Preview(name = "text-size")
    @Composable
    fun SettingsDialog() = SettingsDialogPreview(
        SettingsDialog.Choice(SettingRowId.COMPACT_WIDGET_TEXT_SIZE),
        SettingsPreviewData.CompactScheduleWidget,
    )
}

internal class SettingsDialogQrAnimationPreview {
    @Preview(name = "qr-animation")
    @Composable
    fun SettingsDialog() = SettingsDialogPreview(
        SettingsDialog.Choice(SettingRowId.QR_ANIMATION),
        SettingsPreviewData.QrWidget,
    )
}

internal class SettingsDialogPrivacyPreview {
    @Preview(name = "privacy")
    @Composable
    fun SettingsDialog() = SettingsDialogPreview(
        SettingsDialog.Choice(SettingRowId.SCHEDULE_SHARING),
        SettingsPreviewData.Privacy,
    )
}

internal class SettingsDialogCustomServicesPreview {
    @Preview(name = "custom-services")
    @Composable
    fun SettingsDialog() = SettingsDialogPreview(SettingsDialog.CustomServicesConsent)
}

internal class SettingsDialogBackgroundWorkPreview {
    @Preview(name = "background-work")
    @Composable
    fun SettingsDialog() = SettingsDialogPreview(SettingsDialog.BackgroundWorkHint)
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
