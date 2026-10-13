package dev.alllexey.itmowidgets.feature.recordbook.presentation

import dev.alllexey.itmowidgets.feature.recordbook.barsJournal
import dev.alllexey.itmowidgets.feature.recordbook.recordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which number a subject shows: the most current source with a value, never one without. */
class RecordbookPointsRuleTest {
    private val official = recordbookSubject().copy(rate = null, score = null)

    private fun bars(score: Double?): RecordbookSubject = official.copy(score = score, barsJournal = barsJournal())

    private fun sheet(value: String?, fresh: Boolean = true) = RecordbookSheetTotal(value, updatedAt = null, fresh = fresh)

    private fun pick(official: RecordbookSubject = this.official, shown: RecordbookSubject = official, sheet: RecordbookSheetTotal? = null) =
        RecordbookPointsRule.pick(official, shown, sheet)

    @Test fun `sheet only`() {
        assertEquals(RecordbookPoints(RecordbookSource.SHEET, "41,5", 41.5), pick(sheet = sheet("41,5")))
    }

    @Test fun `BARS only`() {
        assertEquals(RecordbookPoints(RecordbookSource.BARS, "63,5", 63.5), pick(shown = bars(63.5)))
    }

    @Test fun `both with values, the sheet wins`() {
        assertEquals(RecordbookPoints(RecordbookSource.SHEET, "41,5", 41.5), pick(shown = bars(63.5), sheet = sheet("41,5")))
    }

    @Test fun `an empty BARS journal does not hide the sheet`() {
        assertEquals(RecordbookPoints(RecordbookSource.SHEET, "41,5", 41.5), pick(shown = bars(null), sheet = sheet("41,5")))
    }

    @Test fun `an empty sheet does not hide BARS`() {
        assertEquals(RecordbookPoints(RecordbookSource.BARS, "63,5", 63.5), pick(shown = bars(63.5), sheet = sheet(" ")))
    }

    @Test fun `both empty, My ITMO has the number, else a dash under the connected sheet`() {
        val withMyItmo = official.copy(score = 35.0)
        assertEquals(
            RecordbookPoints(RecordbookSource.MY_ITMO, "35", 35.0),
            pick(official = withMyItmo, shown = bars(null), sheet = sheet(null)),
        )
        assertEquals(RecordbookPoints(RecordbookSource.SHEET, null, null), pick(shown = bars(null), sheet = sheet(null)))
        assertEquals(RecordbookPoints(RecordbookSource.BARS, null, null), pick(shown = bars(null)))
        assertEquals(RecordbookPoints(RecordbookSource.MY_ITMO, null, null), pick())
    }

    @Test fun `a stale sheet yields to BARS but still beats My ITMO`() {
        val stale = sheet("41,5", fresh = false)
        assertEquals(RecordbookPoints(RecordbookSource.BARS, "63,5", 63.5), pick(shown = bars(63.5), sheet = stale))
        assertEquals(
            RecordbookPoints(RecordbookSource.SHEET, "41,5", 41.5),
            pick(official = official.copy(score = 35.0), shown = bars(null), sheet = stale),
        )
    }

    @Test fun `a final result ignores the sheet`() {
        val passed = official.copy(rate = "4/C", score = 79.0)
        assertEquals(RecordbookPoints(RecordbookSource.MY_ITMO, "79", 79.0), pick(official = passed, sheet = sheet("41,5")))
    }

    @Test fun `a sheet text that is not a number is shown as written with an empty bar`() {
        assertEquals(RecordbookPoints(RecordbookSource.SHEET, "5A", null), pick(sheet = sheet("5A")))
        assertEquals(RecordbookPoints(RecordbookSource.SHEET, "1 234,5", 1234.5), pick(sheet = sheet("1 234,5")))
    }
}
