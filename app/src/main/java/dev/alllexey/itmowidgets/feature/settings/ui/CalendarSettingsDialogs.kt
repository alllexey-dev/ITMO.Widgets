package dev.alllexey.itmowidgets.feature.settings.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.datepicker.MaterialDatePicker
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.schedule.IcsFile
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate

/** The tag of the date range picker, so a recreated sheet listens to it again. */
private const val ICS_DATES_TAG = "ics_dates"

/** The picker on the activity's fragment manager, for the Compose shell's `.ics` sheet. */
internal fun AppCompatActivity.showIcsDatePicker(onRange: (LocalDate, LocalDate) -> Unit) {
    MaterialDatePicker.Builder.dateRangePicker()
        .setTitleText(R.string.ics_range_custom)
        .build()
        .also { it.answerTo(onRange) }
        .show(supportFragmentManager, ICS_DATES_TAG)
}

/** Listens again to a date range picker restored after recreation. */
@Suppress("UNCHECKED_CAST")
internal fun AppCompatActivity.listenToIcsDatePicker(onRange: (LocalDate, LocalDate) -> Unit) {
    (supportFragmentManager.findFragmentByTag(ICS_DATES_TAG) as? MaterialDatePicker<androidx.core.util.Pair<Long, Long>>)
        ?.answerTo(onRange)
}

/** The picker belongs to the sheet: selecting a tab or Back closes both. */
internal fun AppCompatActivity.closeIcsDatePicker() {
    (supportFragmentManager.findFragmentByTag(ICS_DATES_TAG) as? MaterialDatePicker<*>)?.dismissAllowingStateLoss()
}

/** The picker answers in UTC midnights of the chosen days. */
private fun MaterialDatePicker<androidx.core.util.Pair<Long, Long>>.answerTo(onRange: (LocalDate, LocalDate) -> Unit) {
    clearOnPositiveButtonClickListeners()
    addOnPositiveButtonClickListener { selection ->
        val start = selection.first ?: return@addOnPositiveButtonClickListener
        val end = selection.second ?: start
        onRange(utcDate(start), utcDate(end))
    }
}

/** Sends the file through the system share sheet as `text/calendar`. */
internal fun Context.shareIcs(file: IcsFile) {
    val uri = file.uri.toUri()
    startSafely(Intent.createChooser(sendIntent(uri, file.name), null))
}

/** An app on the phone opens `.ics` files: only then is «Открыть в календаре» shown. */
internal fun Context.canOpenIcs(file: IcsFile): Boolean =
    viewIntent(file.uri.toUri()).resolveActivity(packageManager) != null

internal fun Context.openIcs(file: IcsFile) {
    startSafely(viewIntent(file.uri.toUri()))
}

private fun sendIntent(uri: Uri, name: String) = Intent(Intent.ACTION_SEND)
    .setType(ICS_TYPE)
    .putExtra(Intent.EXTRA_STREAM, uri)
    .putExtra(Intent.EXTRA_TITLE, name)
    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // The chooser passes the read grant on only through ClipData.
    .apply { clipData = ClipData.newRawUri(name, uri) }

private fun Context.startSafely(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Unit
    }
}

private fun utcDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toKotlinLocalDate()

private fun viewIntent(uri: Uri) = Intent(Intent.ACTION_VIEW)
    .setDataAndType(uri, ICS_TYPE)
    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

private const val ICS_TYPE = "text/calendar"
