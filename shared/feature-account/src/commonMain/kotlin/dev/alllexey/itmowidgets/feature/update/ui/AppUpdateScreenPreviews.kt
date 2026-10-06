package dev.alllexey.itmowidgets.feature.update.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.feature.update.presentation.AppUpdateUiState
import dev.alllexey.itmowidgets.feature.update.ui.preview.AppUpdatePreviewData

/*
 * The harness names a baseline `<function>_<@Preview name>`, and LA-1c recorded the XML references as
 * `AppUpdateScreen_<state>`. Each state therefore is a function called `AppUpdateScreen` in a holder class of its
 * own; the scanner instantiates each holder by reflection.
 */

@Composable
private fun AppUpdatePreview(state: AppUpdateUiState) = ItmoPreview {
    AppUpdateScreen(state, onUpdate = {}, onLater = {}, onClose = {}, onSkip = {})
}

internal class AppUpdateScreenSupportedPreview {
    @Preview(name = "supported")
    @Composable
    fun AppUpdateScreen() = AppUpdatePreview(AppUpdatePreviewData.Supported)
}

internal class AppUpdateScreenUnsupportedPreview {
    @Preview(name = "unsupported")
    @Composable
    fun AppUpdateScreen() = AppUpdatePreview(AppUpdatePreviewData.Unsupported)
}

internal class AppUpdateScreenLongNotePreview {
    @Preview(name = "long-note")
    @Composable
    fun AppUpdateScreen() = AppUpdatePreview(AppUpdatePreviewData.LongNote)
}
