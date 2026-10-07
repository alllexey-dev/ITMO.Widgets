package dev.alllexey.itmowidgets.feature.recordbook.ui

import androidx.compose.runtime.Composable
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
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
import kotlin.math.abs

/*
 * The recordbook list's texts in common code (LR-3). The hub keeps the View versions in `RecordbookPresentation.kt`
 * until its own port; both read the pure helpers here.
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

/**
 * Points as the Russian `NumberFormat` with at most one fraction digit wrote them: `48,5`, `72`, `1 234,5` with a
 * no-break space between thousands. Rounding is HALF_EVEN on the double's exact binary value, as `DecimalFormat`
 * does: `0.05` (a little above) gives `0,1`, `0.25` (exact) gives `0,2`, `0.35` (a little below) gives `0,3`.
 */
fun formatRecordbookNumber(value: Double): String {
    if (!value.isFinite()) return value.toString()
    val sign = if (value < 0 || (value == 0.0 && 1 / value < 0)) "-" else ""
    val tenths = roundedTenths(abs(value))
    val whole = groupThousands((tenths / 10).toString())
    val fraction = tenths % 10
    return if (fraction == 0L) "$sign$whole" else "$sign$whole$DECIMAL_SEPARATOR$fraction"
}

/** `value * 10` rounded half to even, decided on the exact value `mantissa * 2^exponent`, never on a rounded product. */
private fun roundedTenths(value: Double): Long {
    if (value >= EXACT_LIMIT) return value.toLong() * 10
    val bits = value.toRawBits()
    val biased = ((bits ushr MANTISSA_BITS) and EXPONENT_MASK).toInt()
    val fraction = bits and MANTISSA_MASK
    // A normal double is (2^52 + fraction) * 2^(biased - 1075); a subnormal one fraction * 2^-1074.
    val mantissa = if (biased == 0) fraction else fraction or IMPLICIT_BIT
    val exponent = if (biased == 0) MIN_EXPONENT else biased - EXPONENT_BIAS
    val scaled = mantissa * 10
    if (exponent >= 0) return scaled shl exponent
    val shift = -exponent
    // Below 2^-60 the value is under a twentieth of a point: it rounds to zero tenths.
    if (shift > MAX_SHIFT) return 0
    val whole = scaled ushr shift
    val rest = scaled and ((1L shl shift) - 1)
    val half = 1L shl (shift - 1)
    return when {
        rest > half -> whole + 1
        rest < half -> whole
        whole % 2 == 0L -> whole
        else -> whole + 1
    }
}

private fun groupThousands(digits: String): String =
    digits.reversed().chunked(GROUP_SIZE).joinToString(GROUP_SEPARATOR).reversed()

private val COMPACT_GRADE = Regex("[2345][A-FX]{0,2}")

/** The separator of a row's metadata parts. */
internal const val META_SEPARATOR = " \u00B7 "

private const val DECIMAL_SEPARATOR = ","
private const val GROUP_SEPARATOR = "\u00A0"
private const val GROUP_SIZE = 3
private const val MANTISSA_BITS = 52
private const val EXPONENT_MASK = 0x7FFL
private const val MANTISSA_MASK = (1L shl MANTISSA_BITS) - 1
private const val IMPLICIT_BIT = 1L shl MANTISSA_BITS
private const val EXPONENT_BIAS = 1075
private const val MIN_EXPONENT = -1074
private const val MAX_SHIFT = 60

/** From 2^52 on every double is whole; ten times a score still fits a Long. */
private const val EXACT_LIMIT = 4.503599627370496E15
