package dev.alllexey.itmowidgets.feature.settings.ui

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.copyToClipboard
import dev.alllexey.itmowidgets.core.ui.spoiler.SpoilerCropResult
import dev.alllexey.itmowidgets.feature.settings.presentation.CustomSpoilerEvent
import dev.alllexey.itmowidgets.feature.settings.presentation.CustomSpoilerViewModel

// What only Android does for the settings pages and the error journal; the shell's settings entries call these.

/** The two permissions calendar sync needs, asked together. */
internal val CALENDAR_PERMISSIONS = arrayOf(
    android.Manifest.permission.READ_CALENDAR,
    android.Manifest.permission.WRITE_CALENDAR
)

/** How turning calendar sync on starts: straight on, the system dialog, or a short explanation first. */
internal enum class CalendarAccessStep { GRANTED, ASK, EXPLAIN }

/** Granted: straight on. Otherwise a short explanation first when Android suggests one, then the system dialog. */
internal fun Activity.calendarAccessStep(): CalendarAccessStep = when {
    CALENDAR_PERMISSIONS.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED } ->
        CalendarAccessStep.GRANTED
    !ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.WRITE_CALENDAR) ->
        CalendarAccessStep.ASK
    else -> CalendarAccessStep.EXPLAIN
}

/** What the system dialog's [results] mean: sync goes on, the locked explanation, or a short denial message. */
internal enum class CalendarAccessAnswer { GRANTED, LOCKED, DENIED }

internal fun Activity.calendarAccessAnswer(results: Map<String, Boolean>): CalendarAccessAnswer = when {
    results.isNotEmpty() && results.values.all { it } -> CalendarAccessAnswer.GRANTED
    // A refusal without a dialog means the permission is locked; only the app's system page can undo that.
    !ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.WRITE_CALENDAR) ->
        CalendarAccessAnswer.LOCKED
    else -> CalendarAccessAnswer.DENIED
}

/** Turning calendar sync on was refused once more; the dialog may still appear next time. */
internal fun View.showCalendarAccessDenied() {
    Snackbar.make(this, R.string.calendar_access_denied, Snackbar.LENGTH_SHORT).show()
}

internal fun View.showWidgetsRefreshStarted() {
    Snackbar.make(this, R.string.settings_refresh_widgets_started, Snackbar.LENGTH_SHORT).show()
}

/** What became of the custom spoiler image: saved, reset or failed. */
internal fun View.showCustomSpoilerEvent(event: CustomSpoilerEvent) {
    val message = when (event) {
        CustomSpoilerEvent.SAVED -> R.string.settings_qr_custom_image_saved
        CustomSpoilerEvent.RESET -> R.string.settings_qr_custom_image_reset
        CustomSpoilerEvent.FAILED -> R.string.settings_qr_custom_image_failed
    }
    Snackbar.make(this, message, Snackbar.LENGTH_LONG).show()
}

/** The picked and cropped spoiler image is saved; [onFailed] says a failed pick or crop; a cancel does nothing. */
internal fun CustomSpoilerViewModel.onCustomSpoilerCropped(result: SpoilerCropResult, onFailed: () -> Unit) {
    when (result) {
        is SpoilerCropResult.Image -> saveImage(result.uri.toString())
        SpoilerCropResult.Failed -> onFailed()
        SpoilerCropResult.Cancelled -> Unit
    }
}

/**
 * Puts the [journal] on the clipboard. Android 13 and later confirm a copy themselves; before that [onCopied] gets
 * the screen's own message.
 */
internal fun Context.copyDiagnosticsJournal(journal: String, onCopied: (message: String) -> Unit) {
    copyToClipboard(getString(R.string.diagnostics_title), journal) { onCopied(getString(R.string.diagnostics_copied)) }
}
