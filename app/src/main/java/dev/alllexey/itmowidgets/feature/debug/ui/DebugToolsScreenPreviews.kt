package dev.alllexey.itmowidgets.feature.debug.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.feature.debug.presentation.DebugToolsUiState
import kotlinx.datetime.LocalDate

/*
 * The baselines keep LA-1c's reference names, `DebugToolsScreen_<state>`: a baseline is named after its preview
 * function, so each of the three previews is a private `DebugToolsScreen()` in a file of its own
 * (the dialogs are in `DebugToolsRefreshTokenDialogPreview.kt` and `DebugToolsSportScoreDialogPreview.kt`).
 */

/** A fresh install's stores, as the references show them: no token, no overrides. */
internal val PreviewDebugTools = DebugToolsUiState.Content(
    effectiveDate = LocalDate(2026, 2, 16),
    dateOverride = null,
    scoreOverride = null,
    lessonTemplatesEnabled = false,
    refreshTokenConfigured = false,
    refreshTokenUpdateInProgress = false,
    customServicesEnabled = false,
)

/** Debug tools that do nothing, for previews. */
internal object PreviewDebugToolsActions : DebugToolsActions {
    override fun back() = Unit
    override fun replaceRefreshToken(refreshToken: String) = Unit
    override fun setDateOverride(date: LocalDate?) = Unit
    override fun setScoreOverride(attendances: Int, bonus: Int) = Unit
    override fun clearScoreOverride() = Unit
    override fun setLessonTemplatesEnabled(enabled: Boolean) = Unit
    override fun setCustomServicesEnabled(enabled: Boolean) = Unit
    override fun checkScheduleChanges() = Unit
    override fun checkMarks() = Unit
    override fun probeBarsSession() = Unit
}

@Preview(name = "content")
@Composable
private fun DebugToolsScreen() = ItmoPreview {
    DebugToolsScreen(PreviewDebugTools, PreviewDebugToolsActions)
}
