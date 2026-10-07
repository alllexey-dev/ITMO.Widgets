package dev.alllexey.itmowidgets.feature.recordbook.domain.sheets

import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkEventKind
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetScoreRulesTest {
    private val grid = SheetFixtures.csv("grades_multiheader.csv")
    private val connected = Instant.parse("2026-09-01T09:00:00Z")
    private val earlier = Instant.parse("2026-09-07T09:00:00Z")
    private val now = Instant.parse("2026-09-08T09:00:00Z")
    private val score = SheetScore(
        scope = ResourceScope(1, "Тестовый предмет", "2026-1"),
        url = "https://docs.google.com/spreadsheets/d/1TestSheetIdForUnitTests_0123456789-abc/edit",
        tabGid = 22,
        tabName = "P3110",
        rowKey = "123456",
        keyColumn = 0,
        keyKind = KeyKind.ISU,
        column = SheetColumnRef("ИТОГО баллов", 11),
        value = "66,3",
        baseline = "66,3",
        tracked = true,
        status = SheetStatus.OK,
        updatedAt = earlier,
        connectedAt = connected,
    )

    private fun ok(value: String?) = SheetReading(value, SheetStatus.OK)

    @Test fun `the total is read as the sheet shows it`() {
        assertEquals(ok("66,3"), SheetScoreRules.read(grid, score))
        assertEquals(
            "https://docs.google.com/spreadsheets/d/1TestSheetIdForUnitTests_0123456789-abc/edit#gid=22",
            score.tabUrl,
        )
    }

    @Test fun `an empty cell is no value and still a successful read`() {
        val emptied = SheetGrid(grid.rows.mapIndexed { row, cells -> if (row == 5) cells.take(11) + "" + cells.drop(12) else cells })

        assertEquals(ok(null), SheetScoreRules.read(emptied, score))
    }

    @Test fun `a missing row or column is a status`() {
        assertEquals(SheetReading(null, SheetStatus.ROW_NOT_FOUND), SheetScoreRules.read(grid, score.copy(rowKey = "999999")))
        assertEquals(
            SheetReading(null, SheetStatus.COLUMN_NOT_FOUND),
            SheetScoreRules.read(grid, score.copy(column = SheetColumnRef("Нет такого", 40))),
        )
    }

    @Test fun `an untracked connection only takes the baseline`() {
        val update = SheetScoreRules.apply(score.copy(tracked = false), ok("70"), now, notify = true)

        assertNull(update.change)
        assertEquals("70", update.score.baseline)
        assertTrue(update.score.tracked)
        assertEquals(now, update.score.updatedAt)
    }

    @Test fun `a first value is added and another value is changed`() {
        val added = SheetScoreRules.apply(score.copy(value = null, baseline = null), ok("66,3"), now, notify = true)
        val changed = SheetScoreRules.apply(score, ok("70"), now, notify = true)

        assertEquals(MarkEventKind.MARK_ADDED, added.change)
        assertEquals(MarkEventKind.MARK_CHANGED, changed.change)
        assertEquals("70", changed.score.value)
        assertEquals("70", changed.score.baseline)
    }

    @Test fun `the same value up to spaces is no news`() {
        val seventy = score.copy(value = "70", baseline = "70")

        assertNull(SheetScoreRules.apply(seventy, ok("70"), now, notify = true).change)
        assertNull(SheetScoreRules.apply(seventy, ok(" 70 "), now, notify = true).change)
    }

    @Test fun `an emptied cell keeps the baseline and filling it again with the same value is no news`() {
        val seventy = score.copy(value = "70", baseline = "70")

        val emptied = SheetScoreRules.apply(seventy, ok(null), now, notify = true)
        val refilled = SheetScoreRules.apply(emptied.score, ok("70"), now, notify = true)

        assertNull(emptied.change)
        assertNull(emptied.score.value)
        assertEquals("70", emptied.score.baseline)
        assertNull(refilled.change)
        assertEquals("70", refilled.score.value)
    }

    @Test fun `a foreground read moves the baseline without news`() {
        val update = SheetScoreRules.apply(score, ok("70"), now, notify = false)

        assertNull(update.change)
        assertEquals("70", update.score.baseline)
        assertNull(SheetScoreRules.apply(update.score, ok("70"), now, notify = true).change)
    }

    @Test fun `a failed read changes only the status`() {
        listOf(SheetStatus.NETWORK, SheetStatus.CLOSED, SheetStatus.TOO_LARGE).forEach { status ->
            val update = SheetScoreRules.apply(score, SheetReading(null, status), now, notify = true)

            assertNull(update.change)
            assertEquals(score.copy(status = status), update.score)
            assertFalse(update.score.status == SheetStatus.OK)
        }
    }
}
