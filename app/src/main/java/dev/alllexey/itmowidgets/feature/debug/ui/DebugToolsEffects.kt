package dev.alllexey.itmowidgets.feature.debug.ui

import android.app.Activity
import android.widget.Toast
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.feature.debug.presentation.DebugToolsEvent
import dev.alllexey.itmowidgets.feature.debug.presentation.DebugToolsViewModel
import kotlinx.datetime.LocalDate

/**
 * What only Android does for a debug tools event: recreate the activity after an override, toast the refresh token
 * outcome. The Compose shell's debug-only entry calls it.
 */
fun Activity.handleDebugToolsEvent(event: DebugToolsEvent) {
    when (event) {
        DebugToolsEvent.RecreateActivity -> recreate()
        DebugToolsEvent.RefreshTokenUpdated -> {
            Toast.makeText(this, R.string.debug_refresh_token_updated, Toast.LENGTH_SHORT).show()
            recreate()
        }
        is DebugToolsEvent.RefreshTokenUpdateFailed -> Toast.makeText(
            this,
            getString(R.string.debug_refresh_token_failed, getString(event.error.messageRes())),
            Toast.LENGTH_LONG,
        ).show()
    }
}

/** The screen's actions over [viewModel]; [onBack] closes the screen in its host. */
fun debugToolsActions(viewModel: DebugToolsViewModel, onBack: () -> Unit): DebugToolsActions =
    object : DebugToolsActions {
        override fun back() = onBack()
        override fun replaceRefreshToken(refreshToken: String) = viewModel.replaceRefreshToken(refreshToken)
        override fun setDateOverride(date: LocalDate?) = viewModel.setDateOverride(date)
        override fun setScoreOverride(attendances: Int, bonus: Int) = viewModel.setScoreOverride(attendances, bonus)
        override fun clearScoreOverride() = viewModel.clearScoreOverride()
        override fun setLessonTemplatesEnabled(enabled: Boolean) = viewModel.setLessonTemplatesEnabled(enabled)
        override fun setCustomServicesEnabled(enabled: Boolean) = viewModel.setCustomServicesEnabled(enabled)
        override fun checkScheduleChanges() = viewModel.checkScheduleChanges()
        override fun checkMarks() = viewModel.checkMarks()
        override fun probeBarsSession() = viewModel.probeBarsSession()
    }
