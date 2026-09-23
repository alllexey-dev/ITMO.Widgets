package dev.alllexey.itmowidgets.feature.recordbook.ui

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.snackbar.Snackbar
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.feature.recordbook.domain.ControlGroup
import dev.alllexey.itmowidgets.feature.recordbook.domain.ControlGroupKind
import dev.alllexey.itmowidgets.feature.recordbook.domain.GradeStep
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookGradeScale
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookRate
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubjectStatus
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookAttentionReason
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
    // «Без оценки» says nothing next to the points; only an uncoded result such as a no-show reason is added.
    if (normalizedRate is RecordbookRate.Grade && compactGradeCode == null) add(displayRate(context))
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

fun RecordbookAttentionReason.text(context: Context): String = when (this) {
    RecordbookAttentionReason.Failed -> context.getString(R.string.recordbook_reason_failed)
    RecordbookAttentionReason.Absent -> context.getString(R.string.recordbook_absent)
    is RecordbookAttentionReason.BelowMinimum -> context.getString(R.string.recordbook_reason_below_minimum, controlName)
    is RecordbookAttentionReason.SportShort ->
        context.resources.getQuantityString(R.plurals.recordbook_reason_sport, remaining, remaining)
}

/** «до 4C ещё 3» or «до зачёта ещё 8»; whole points are shown without a fraction. */
fun GradeStep.text(context: Context): String {
    val points = formatRecordbookNumber(remaining)
    return if (target == RecordbookGradeScale.CREDIT_TARGET) context.getString(R.string.subject_credit_next, points)
        else context.getString(R.string.subject_grade_next, target, points)
}

fun ControlGroup.displayTitle(context: Context): String = when (kind) {
    ControlGroupKind.LABS -> context.getString(R.string.recordbook_group_labs)
    ControlGroupKind.TESTS -> context.getString(R.string.recordbook_group_tests)
    ControlGroupKind.PRACTICALS -> context.getString(R.string.recordbook_group_practicals)
    ControlGroupKind.HOMEWORK -> context.getString(R.string.recordbook_group_homework)
    null -> title
}

/** The final result as a short badge text: a grade code, «Зачёт», or «—» for anything longer. */
fun RecordbookSubject.badgeText(context: Context): String = compactGradeCode
    ?: if (normalizedRate == RecordbookRate.Credit) context.getString(R.string.recordbook_rate_credit)
    else context.getString(R.string.recordbook_score_pending)

/** Passed in green, failed in the error colour, anything else neutral; always on a quiet container of its colour. */
fun TextView.bindGradeBadge(subject: RecordbookSubject) {
    text = subject.badgeText(context)
    val color = when (subject.status) {
        RecordbookSubjectStatus.PASSED -> ContextCompat.getColor(context, R.color.recordbook_passed)
        RecordbookSubjectStatus.ATTENTION -> context.color.resolve(androidx.appcompat.R.attr.colorError)
        RecordbookSubjectStatus.IN_PROGRESS -> context.color.resolve(com.google.android.material.R.attr.colorOnSurfaceVariant)
    }
    setTextColor(color)
    background?.mutate()?.setTint(if (subject.status == RecordbookSubjectStatus.IN_PROGRESS)
        context.color.resolve(com.google.android.material.R.attr.colorSurfaceContainerHighest)
        else ColorUtils.setAlphaComponent(color, BADGE_ALPHA))
}

private const val BADGE_ALPHA = 0x29

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
