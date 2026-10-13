package dev.alllexey.itmowidgets.feature.recordbook.presentation

import dev.alllexey.itmowidgets.feature.recordbook.domain.formatRecordbookNumber
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookRate
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import kotlin.time.Instant

/**
 * A connected sheet's total of a subject: [value] is null for an empty cell, [fresh] is false once the last reading
 * failed (no connection, closed, row or column not found) and [value] is the one read before.
 */
internal data class RecordbookSheetTotal(val value: String?, val updatedAt: Instant?, val fresh: Boolean = true)

/** The number a subject shows: [text] as written (null is a dash), [points] for the 0-100 bar, and its [source]. */
internal data class RecordbookPoints(val source: RecordbookSource, val text: String?, val points: Double?)

/**
 * Which number a subject shows, the one rule for the list, `Откуда баллы` and the subject page
 * (docs/features/recordbook.md#which-number-is-shown). The most current source with a value wins:
 *
 * 1. a connected sheet whose last reading succeeded and whose cell is not empty;
 * 2. BARS, when the subject's journal has a score;
 * 3. the last value of a sheet whose last reading failed;
 * 4. My ITMO's score.
 *
 * A source without a value never hides one with a value. With no value anywhere the dash is labelled by the first
 * source present in the same order: a connected sheet, a BARS journal, else My ITMO. The sheet counts only while the
 * subject is in progress; a final result or a no-show takes BARS's or My ITMO's number.
 */
internal object RecordbookPointsRule {

    /** [official] as My ITMO lists it, [shown] with BARS laid over it, [sheet] the connected total if any. */
    fun pick(official: RecordbookSubject, shown: RecordbookSubject, sheet: RecordbookSheetTotal?): RecordbookPoints {
        val inProgress = shown.normalizedRate == RecordbookRate.InProgress && !shown.absent
        val table = sheet?.takeIf { inProgress }
        val tableText = table?.value?.trim()?.takeIf(String::isNotEmpty)
        val tablePoints = tableText?.let { RecordbookPoints(RecordbookSource.SHEET, it, parse(it)) }
        val journal = shown.barsJournal != null
        val bars = shown.score.valid().takeIf { journal }
        val myItmo = official.score.valid()
        return when {
            tablePoints != null && table.fresh -> tablePoints
            bars != null -> RecordbookPoints(RecordbookSource.BARS, formatRecordbookNumber(bars), bars)
            tablePoints != null -> tablePoints
            myItmo != null -> RecordbookPoints(RecordbookSource.MY_ITMO, formatRecordbookNumber(myItmo), myItmo)
            table != null -> RecordbookPoints(RecordbookSource.SHEET, null, null)
            journal -> RecordbookPoints(RecordbookSource.BARS, null, null)
            else -> RecordbookPoints(RecordbookSource.MY_ITMO, null, null)
        }
    }

    /** The sheet's text as a number for the bar only (`66,3`, `1 234`); `5A` or `80%` leave the bar empty. */
    private fun parse(text: String): Double? = text.replace(',', '.').filterNot(Char::isWhitespace).toDoubleOrNull()

    private fun Double?.valid(): Double? = this?.takeIf { it.isFinite() && it >= 0 }
}
