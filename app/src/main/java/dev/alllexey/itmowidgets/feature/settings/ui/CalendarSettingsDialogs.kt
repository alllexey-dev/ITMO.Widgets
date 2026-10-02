package dev.alllexey.itmowidgets.feature.settings.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.schedule.IcsFile
import dev.alllexey.itmowidgets.core.schedule.ScheduleExportRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** The tag of the date range picker, so a recreated settings page listens to it again. */
internal const val ICS_DATES_TAG = "ics_dates"

/** The four ranges; «Свои даты» opens the date range picker instead of answering at once. */
internal fun Fragment.showIcsRanges(onRange: (ScheduleExportRange) -> Unit, onCustom: () -> Unit) {
    val ranges = listOf(
        R.string.ics_range_week to ScheduleExportRange.Week,
        R.string.ics_range_two_weeks to ScheduleExportRange.TwoWeeks,
        R.string.ics_range_semester to ScheduleExportRange.Semester,
        R.string.ics_range_custom to null
    )
    MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.settings_ics_export_title)
        .setItems(ranges.map { getString(it.first) }.toTypedArray()) { _, index ->
            ranges[index].second?.let(onRange) ?: onCustom()
        }
        .setNegativeButton(R.string.common_cancel, null)
        .show()
}

internal fun Fragment.showIcsDatePicker(onRange: (ScheduleExportRange) -> Unit) {
    MaterialDatePicker.Builder.dateRangePicker()
        .setTitleText(R.string.ics_range_custom)
        .build()
        .also { it.answerTo(onRange) }
        .show(childFragmentManager, ICS_DATES_TAG)
}

/** Listens again to a date range picker restored after recreation. */
@Suppress("UNCHECKED_CAST")
internal fun Fragment.listenToIcsDatePicker(onRange: (ScheduleExportRange) -> Unit) {
    (childFragmentManager.findFragmentByTag(ICS_DATES_TAG) as? MaterialDatePicker<androidx.core.util.Pair<Long, Long>>)
        ?.answerTo(onRange)
}

/** The picker answers in UTC midnights of the chosen days. */
private fun MaterialDatePicker<androidx.core.util.Pair<Long, Long>>.answerTo(onRange: (ScheduleExportRange) -> Unit) {
    clearOnPositiveButtonClickListeners()
    addOnPositiveButtonClickListener { selection ->
        val start = selection.first ?: return@addOnPositiveButtonClickListener
        val end = selection.second ?: start
        onRange(ScheduleExportRange.Custom(utcDate(start), utcDate(end)))
    }
}

/** «Отправить» shares the file; «Открыть в календаре» appears only when an app on the phone opens `.ics` files. */
internal fun Fragment.showIcsReady(file: IcsFile) {
    val context = requireContext()
    val uri = file.uri.toUri()
    val view = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, ICS_TYPE)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val builder = MaterialAlertDialogBuilder(context)
        .setTitle(R.string.ics_ready_title)
        .setMessage(resources.getQuantityString(R.plurals.schedule_lesson_count, file.lessons, file.lessons))
        .setPositiveButton(R.string.ics_send) { _, _ -> startSafely(Intent.createChooser(sendIntent(uri, file.name), null)) }
    if (view.resolveActivity(context.packageManager) != null) {
        builder.setNeutralButton(R.string.ics_open) { _, _ -> startSafely(view) }
    }
    builder.show()
}

/** The app's page in Android settings, where a permission refused for good can be given. */
internal fun Fragment.openAppSettings() {
    startSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", requireContext().packageName, null)))
}

private fun sendIntent(uri: Uri, name: String) = Intent(Intent.ACTION_SEND)
    .setType(ICS_TYPE)
    .putExtra(Intent.EXTRA_STREAM, uri)
    .putExtra(Intent.EXTRA_TITLE, name)
    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // The chooser passes the read grant on only through ClipData.
    .apply { clipData = ClipData.newRawUri(name, uri) }

private fun Fragment.startSafely(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Unit
    }
}

private fun utcDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

private const val ICS_TYPE = "text/calendar"
