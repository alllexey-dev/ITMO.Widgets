package dev.alllexey.itmowidgets.feature.settings.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckedTextView
import android.widget.TextView
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.schedule.CalendarTarget
import dev.alllexey.itmowidgets.core.schedule.IcsFile
import dev.alllexey.itmowidgets.core.schedule.ScheduleExportRange
import dev.alllexey.itmowidgets.core.schedule.WritableCalendar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** The tag of the date range picker, so a recreated settings page listens to it again. */
internal const val ICS_DATES_TAG = "ics_dates"

/**
 * The app's own calendar first, then the phone's writable calendars under their accounts, then the hint about a
 * separate Google calendar. Accounts and the hint are not clickable.
 */
internal fun Fragment.showCalendarPicker(
    calendars: List<WritableCalendar>,
    selected: CalendarTarget?,
    onPick: (CalendarTarget) -> Unit
) {
    val rows = buildList {
        add(PickerRow.Header(getString(R.string.calendar_picker_this_phone)))
        add(PickerRow.Option(CalendarTarget.AppCalendar, getString(R.string.app_name)))
        calendars.groupBy(WritableCalendar::account).forEach { (account, inAccount) ->
            add(PickerRow.Header(account))
            inAccount.forEach { add(PickerRow.Option(CalendarTarget.PhoneCalendar(it.id), it.name)) }
        }
        add(PickerRow.Hint(getString(R.string.calendar_picker_hint)))
    }
    val adapter = PickerAdapter(rows, selected ?: CalendarTarget.AppCalendar)
    MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.settings_calendar_target_title)
        .setAdapter(adapter) { dialog, position ->
            (rows[position] as? PickerRow.Option)?.let { option ->
                dialog.dismiss()
                if (option.target != selected) onPick(option.target)
            }
        }
        .setNegativeButton(R.string.common_cancel, null)
        .show()
}

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

private sealed interface PickerRow {
    data class Header(val text: String) : PickerRow
    data class Option(val target: CalendarTarget, val title: String) : PickerRow
    data class Hint(val text: String) : PickerRow
}

private class PickerAdapter(private val rows: List<PickerRow>, private val selected: CalendarTarget) : BaseAdapter() {
    override fun getCount() = rows.size
    override fun getItem(position: Int) = rows[position]
    override fun getItemId(position: Int) = position.toLong()
    override fun getViewTypeCount() = 3
    override fun areAllItemsEnabled() = false
    override fun isEnabled(position: Int) = rows[position] is PickerRow.Option

    override fun getItemViewType(position: Int) = when (rows[position]) {
        is PickerRow.Header -> 0
        is PickerRow.Option -> 1
        is PickerRow.Hint -> 2
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = rows[position]
        val layout = when (row) {
            is PickerRow.Header -> R.layout.item_calendar_picker_header
            is PickerRow.Option -> R.layout.item_calendar_picker_option
            is PickerRow.Hint -> R.layout.item_calendar_picker_hint
        }
        val view = convertView ?: LayoutInflater.from(parent.context).inflate(layout, parent, false)
        when (row) {
            is PickerRow.Header -> (view as TextView).text = row.text
            is PickerRow.Hint -> (view as TextView).text = row.text
            is PickerRow.Option -> (view as CheckedTextView).apply {
                text = row.title
                isChecked = row.target == selected
            }
        }
        return view
    }
}
