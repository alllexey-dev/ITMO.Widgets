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
        formula = SheetFormula.Direct(SheetCellRef(22, SheetColumnRef("ИТОГО баллов", 11))),
        value = "66,3",
        baseline = "66,3",
        tracked = true,
        status = SheetStatus.OK,
        updatedAt = earlier,
        connectedAt = connected,
    )

    private fun ok(value: String?) = SheetReading(value, SheetStatus.OK)
    private val tabs = mapOf(22L to grid)
    private fun cell(column: Int, gid: Long = 22) = SheetNode.Cell(SheetCellRef(gid, SheetColumnRef("", column)))
    private fun sum(vararg nodes: SheetNode) = SheetNode.Apply(SheetOp.SUM, nodes.toList())

    @Test fun `the total is read as the sheet shows it`() {
        assertEquals(ok("66,3"), SheetScoreRules.read(tabs, score))
        assertEquals(
            "https://docs.google.com/spreadsheets/d/1TestSheetIdForUnitTests_0123456789-abc/edit#gid=22",
            score.tabUrl,
        )
    }

    @Test fun `an empty cell is no value and still a successful read`() {
        val emptied = SheetGrid(grid.rows.mapIndexed { row, cells -> if (row == 5) cells.take(11) + "" + cells.drop(12) else cells })

        assertEquals(ok(null), SheetScoreRules.read(mapOf(22L to emptied), score))
    }

    @Test fun `a missing row or column is a status`() {
        assertEquals(SheetReading(null, SheetStatus.ROW_NOT_FOUND), SheetScoreRules.read(tabs, score.copy(rowKey = "999999")))
        assertEquals(
            SheetReading(null, SheetStatus.COLUMN_NOT_FOUND),
            SheetScoreRules.read(tabs, score.copy(formula = SheetFormula.Direct(SheetCellRef(22, SheetColumnRef("Нет такого", 40))))),
        )
    }

    @Test fun `a tab that was not downloaded reads as its status`() {
        assertEquals(SheetReading(null, SheetStatus.COLUMN_NOT_FOUND), SheetScoreRules.read(emptyMap(), score))
        val elsewhere = score.copy(formula = SheetFormula.Computed(sum(cell(3), cell(3, gid = 99)), BlankPolicy.PRESERVE))
        assertEquals(SheetReading(null, SheetStatus.NETWORK), SheetScoreRules.read(tabs, elsewhere, missing = SheetStatus.NETWORK))
    }

    @Test fun `a computed total adds the own row's cells and is written as the app writes numbers`() {
        val attendance = score.copy(formula = SheetFormula.Computed(sum(cell(3), cell(4), cell(5), cell(10)), BlankPolicy.PRESERVE))

        assertEquals(ok("18"), SheetScoreRules.read(tabs, attendance))
    }

    @Test fun `a percentage or a letter in a computed total gives no value, never a guessed number`() {
        listOf(7, 8, 12).forEach { column ->
            val formula = SheetFormula.Computed(sum(cell(3), cell(column)), BlankPolicy.IGNORE_BLANK_INPUTS)

            assertEquals("column $column", ok(null), SheetScoreRules.read(tabs, score.copy(formula = formula)))
        }
    }

    @Test fun `the official total of a rule is read beside the main one from another tab`() {
        val roster = SheetGrid(listOf(listOf("ИСУ", "Итог"), listOf("123456", "0")))
        val ruled = score.copy(
            formula = SheetFormula.Computed(sum(cell(3), cell(10)), BlankPolicy.PRESERVE),
            official = SheetFormula.Direct(SheetCellRef(11, SheetColumnRef("Итог", 1))),
        )

        assertEquals(listOf(22L, 11L), ruled.tabs)
        assertEquals(SheetReading("14", SheetStatus.OK, official = "0"), SheetScoreRules.read(tabs + (11L to roster), ruled))
        assertEquals(SheetReading("14", SheetStatus.OK), SheetScoreRules.read(tabs, ruled))
    }

    @Test fun `a reading keeps the official value it read`() {
        val update = SheetScoreRules.apply(score, SheetReading("70", SheetStatus.OK, official = "0"), now, notify = false)

        assertEquals("0", update.score.officialValue)
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
