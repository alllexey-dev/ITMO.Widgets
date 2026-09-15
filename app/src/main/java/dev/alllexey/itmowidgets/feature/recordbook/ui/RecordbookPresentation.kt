package dev.alllexey.itmowidgets.feature.recordbook.ui

import android.content.Context
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookRate
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubjectStatus
import java.text.NumberFormat
import java.util.Locale

fun RecordbookSubject.displayRate(context: Context): String = if (absent) context.getString(R.string.recordbook_absent) else when (val value = normalizedRate) {
    RecordbookRate.Credit -> context.getString(R.string.recordbook_rate_credit)
    RecordbookRate.InProgress -> context.getString(R.string.recordbook_rate_in_progress)
    is RecordbookRate.Grade -> value.code
}

val RecordbookSubject.compactGradeCode: String?
    get() = (normalizedRate as? RecordbookRate.Grade)?.code?.takeIf { it.matches(Regex("[2345][A-FX]{0,2}")) }

fun RecordbookSubject.assessmentLabel(context: Context): String = buildList {
    add(controlType)
    if (normalizedRate != RecordbookRate.Credit && compactGradeCode == null) add(displayRate(context))
}.filter(String::isNotBlank).distinct().joinToString(" · ")

fun formatRecordbookNumber(value: Double): String = NumberFormat.getNumberInstance(Locale.forLanguageTag("ru")).apply {
    maximumFractionDigits = 1
}.format(value)

fun RecordbookSubjectStatus.progressColor(context: Context): Int = when (this) {
    RecordbookSubjectStatus.PASSED -> ContextCompat.getColor(context, R.color.recordbook_passed)
    RecordbookSubjectStatus.ATTENTION -> context.color.resolve(androidx.appcompat.R.attr.colorError)
    RecordbookSubjectStatus.IN_PROGRESS -> context.color.primary
}

fun RecordbookSportState.compactText(context: Context): String = when (this) {
    is RecordbookSportState.Content -> context.getString(R.string.recordbook_sport_source)
    RecordbookSportState.Unavailable -> context.getString(R.string.recordbook_sport_unavailable)
    RecordbookSportState.Error -> context.getString(R.string.recordbook_sport_error)
}

fun showRecordbookSourceInfo(context: Context) {
    MaterialAlertDialogBuilder(context)
        .setTitle(R.string.recordbook_source_title)
        .setMessage(R.string.recordbook_source_description)
        .setPositiveButton(R.string.common_close, null)
        .show()
}

/** BARS failed while MyITMO values are on screen; an ended ITMO.ID session offers the interactive login. */
fun recordbookBarsSnackbar(root: View, error: AppError, onRetry: () -> Unit, onLogin: () -> Unit): Snackbar {
    val login = error == AppError.Unauthorized
    val context = root.context
    val text = if (login) context.getString(R.string.recordbook_bars_login_required)
        else context.getString(R.string.recordbook_bars_error, context.getString(error.messageRes()))
    return Snackbar.make(root, text, Snackbar.LENGTH_LONG).setTextMaxLines(4)
        .setAction(if (login) R.string.recordbook_bars_login else R.string.common_retry) { if (login) onLogin() else onRetry() }
        .also(Snackbar::show)
}
