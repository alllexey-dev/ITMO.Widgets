package dev.alllexey.itmowidgets.feature.debug.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/** The refresh token dialog, empty, without its window (see `DebugToolsScreenPreviews.kt` for the name). */
@Preview(name = "refresh-token-dialog")
@Composable
private fun DebugToolsScreen() = ItmoPreview {
    Box(Modifier.padding(ItmoTheme.spacing.section)) {
        RefreshTokenDialog(onSave = {}, onDismiss = {})
    }
}
