package dev.alllexey.itmowidgets.feature.settings.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.google.android.material.color.MaterialColors
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.schedule.IcsFile
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** The tag of the date range picker, so a recreated sheet listens to it again. */
internal const val ICS_DATES_TAG = "ics_dates"

internal fun Fragment.showIcsDatePicker(onRange: (LocalDate, LocalDate) -> Unit) {
    MaterialDatePicker.Builder.dateRangePicker()
        .setTitleText(R.string.ics_range_custom)
        .build()
        .also { it.answerTo(onRange) }
        .show(childFragmentManager, ICS_DATES_TAG)
}

/** Listens again to a date range picker restored after recreation. */
@Suppress("UNCHECKED_CAST")
internal fun Fragment.listenToIcsDatePicker(onRange: (LocalDate, LocalDate) -> Unit) {
    (childFragmentManager.findFragmentByTag(ICS_DATES_TAG) as? MaterialDatePicker<androidx.core.util.Pair<Long, Long>>)
        ?.answerTo(onRange)
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
internal fun Fragment.shareIcs(file: IcsFile) {
    val uri = file.uri.toUri()
    startSafely(Intent.createChooser(sendIntent(uri, file.name), null))
}

/** An app on the phone opens `.ics` files: only then is «Открыть в календаре» shown. */
internal fun Fragment.canOpenIcs(file: IcsFile): Boolean =
    viewIntent(file.uri.toUri()).resolveActivity(requireContext().packageManager) != null

internal fun Fragment.openIcs(file: IcsFile) {
    startSafely(viewIntent(file.uri.toUri()))
}

/**
 * Why the app asks for the calendar: «Разрешить» asks Android, or, after a refusal for good ([locked]), «Открыть
 * настройки» opens the app's system page. «Не сейчас» and dismissing call [onCancel].
 */
internal fun Fragment.showCalendarAccessDialog(locked: Boolean, onAllow: () -> Unit, onCancel: () -> Unit) {
    val context = requireContext()
    // The Material 3 hero-icon dialog: the icon above a centred title, in the secondary colour.
    val icon = checkNotNull(AppCompatResources.getDrawable(context, R.drawable.ic_calendar_add)).mutate().apply {
        setTint(MaterialColors.getColor(context, com.google.android.material.R.attr.colorSecondary, 0))
    }
    MaterialAlertDialogBuilder(context, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog_Centered)
        .setIcon(icon)
        .setTitle(R.string.calendar_access_title)
        .setMessage(R.string.calendar_access_rationale)
        .setNegativeButton(R.string.calendar_access_later) { _, _ -> onCancel() }
        .setPositiveButton(if (locked) R.string.calendar_access_open_settings else R.string.calendar_access_allow) { _, _ ->
            if (locked) {
                onCancel()
                openAppSettings()
            } else {
                onAllow()
            }
        }
        .setOnCancelListener { onCancel() }
        .show()
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

private fun viewIntent(uri: Uri) = Intent(Intent.ACTION_VIEW)
    .setDataAndType(uri, ICS_TYPE)
    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

private const val ICS_TYPE = "text/calendar"
