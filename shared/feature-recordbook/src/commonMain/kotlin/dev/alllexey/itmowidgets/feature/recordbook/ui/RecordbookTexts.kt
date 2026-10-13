package dev.alllexey.itmowidgets.feature.recordbook.ui

import androidx.compose.runtime.Composable
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import dev.alllexey.itmowidgets.feature.recordbook.domain.formatRecordbookNumber
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookRate
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookAttentionReason
import dev.alllexey.itmowidgets.shared.feature.recordbook.Res
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_absent
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_rate_credit
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_rate_in_progress
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_reason_below_minimum
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_reason_failed
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_reason_sport
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_score_pending
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_sport_error
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_sport_source
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_sport_unavailable
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/*
 * The recordbook's texts in common code: the list and the subject page read the helpers here.
 */

/** The official result in words: `Неявка`, `Зачёт`, `Без оценки` or the grade code as MyITMO wrote it. */
@Composable
fun RecordbookSubject.displayRate(): String = if (absent) {
    stringResource(Res.string.recordbook_absent)
} else {
    when (val value = normalizedRate) {
        RecordbookRate.Credit -> stringResource(Res.string.recordbook_rate_credit)
        RecordbookRate.InProgress -> stringResource(Res.string.recordbook_rate_in_progress)
        is RecordbookRate.Grade -> value.code
    }
}

/** A grade that fits a badge: `5A`, `4C`, `2FX`; anything longer (a no-show reason) is not one. */
val RecordbookSubject.compactGradeCode: String?
    get() = (normalizedRate as? RecordbookRate.Grade)?.code?.takeIf { it.matches(COMPACT_GRADE) }

/** The assessment kind; `Без оценки` says nothing next to the points, only an uncoded result is added. */
@Composable
fun RecordbookSubject.assessmentLabel(): String {
    val rate = if (normalizedRate is RecordbookRate.Grade && compactGradeCode == null) displayRate() else null
    return listOfNotNull(controlType, rate).filter(String::isNotBlank).distinct().joinToString(META_SEPARATOR)
}

/** Why a subject is under `Требуют внимания`, shown instead of its assessment kind. */
@Composable
fun RecordbookAttentionReason.text(): String = when (this) {
    RecordbookAttentionReason.Failed -> stringResource(Res.string.recordbook_reason_failed)
    RecordbookAttentionReason.Absent -> stringResource(Res.string.recordbook_absent)
    is RecordbookAttentionReason.BelowMinimum -> stringResource(Res.string.recordbook_reason_below_minimum, controlName)
    is RecordbookAttentionReason.SportShort ->
        pluralStringResource(Res.plurals.recordbook_reason_sport, remaining, remaining)
}

/** The sport source in a list row; a row shows it only while the sport points are missing. */
@Composable
fun RecordbookSportState.compactText(): String = when (this) {
    is RecordbookSportState.Content -> stringResource(Res.string.recordbook_sport_source)
    RecordbookSportState.Unavailable -> stringResource(Res.string.recordbook_sport_unavailable)
    RecordbookSportState.Error -> stringResource(Res.string.recordbook_sport_error)
}

/** The final result as a short badge text: a grade code, `Зачёт`, or a dash for anything longer. */
@Composable
fun RecordbookSubject.badgeText(): String = compactGradeCode
    ?: if (normalizedRate == RecordbookRate.Credit) {
        stringResource(Res.string.recordbook_rate_credit)
    } else {
        stringResource(Res.string.recordbook_score_pending)
    }

private val COMPACT_GRADE = Regex("[2345][A-FX]{0,2}")

/** The separator of a row's metadata parts. */
internal const val META_SEPARATOR = " \u00B7 "
