package dev.alllexey.itmowidgets.feature.recordbook.domain.sheets

import dev.alllexey.itmowidgets.core.resources.GoogleSheetUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SheetTotalsTest {
    private val identity = OwnIdentity(123456, "Тестов Тест Тестович")
    private val url = GoogleSheetUrl("1TestSheetIdForUnitTests_0123456789-abc", null)
    private val tabA = SheetTab(22, "P3110")
    private val tabB = SheetTab(33, "BARS")

    private fun ownCells(name: String, tab: SheetTab = tabA): List<SheetCell> {
        val grid = SheetTabGrid(tab, SheetFixtures.csv(name))
        val match = (SheetRows.find(SheetWorkbook(url, listOf(grid)), identity) as RowSearch.Found).matches.single()
        return SheetTotals.cells(grid, match)
    }

    private fun cell(path: String, column: Int, value: String = "1", tab: SheetTab = tabA) =
        SheetCell(tab, column, path, value)

    @Test fun `the own cells skip the key and the name and keep values as the sheet shows them`() {
        val cells = ownCells("grades_multiheader.csv")

        assertEquals((2..12).toList(), cells.map { it.column })
        val byPath = cells.associate { it.headerPath to it.value }
        assertEquals("66,3", byPath["ИТОГО баллов"])
        assertEquals("5A", byPath["Оценка · 60-100"])
        assertEquals("100%", byPath["ЛР1 · Защ (%)"])
        assertEquals("P3110", byPath["Группа"])
    }

    @Test fun `the strongest header group wins and the further right column breaks a tie`() {
        assertEquals(11, SheetTotals.detect(ownCells("grades_multiheader.csv"))?.column)
        assertEquals("Σ", SheetTotals.detect(ownCells("name_only.csv"))?.headerPath)
        assertEquals("30", SheetTotals.detect(ownCells("name_only.csv"))?.value)
        assertNull(SheetTotals.detect(ownCells("no_total.csv")))
    }

    @Test fun `a lower keyword group loses`() {
        assertEquals(5, SheetTotals.detect(listOf(cell("Оценка", 2), cell("Сумма баллов", 5), cell("Зачёт", 7)))?.column)
        assertEquals(2, SheetTotals.detect(listOf(cell("Total", 2), cell("BARS credits", 5)))?.column)
    }

    @Test fun `a segment equal to the keyword beats a longer header`() {
        assertEquals(1, SheetTotals.detect(listOf(cell("Итог", 1), cell("КР Итог", 5)))?.column)
        assertEquals(1, SheetTotals.detect(listOf(cell("ЛР · Итог", 1), cell("Итог КР", 5)))?.column)
    }

    @Test fun `a total keyword matches the start of a word`() {
        assertEquals(4, SheetTotals.detect(listOf(cell("Оценка", 2), cell("Итоговый балл", 4)))?.column)
        assertEquals(3, SheetTotals.detect(listOf(cell("ИТОГО", 3), cell("Итоговый балл", 6)))?.column)
        assertEquals(2, SheetTotals.detect(listOf(cell("Total score", 2), cell("Subtotal", 5)))?.column)
    }

    @Test fun `a filled value, then an earlier tab, then a further right column break ties`() {
        assertEquals(2, SheetTotals.detect(listOf(cell("Итог", 2, "10"), cell("Итог", 5, "")))?.column)
        assertEquals(tabA, SheetTotals.detect(listOf(cell("Итог", 2), cell("Итог", 5, tab = tabB)))?.tab)
        assertEquals(tabB, SheetTotals.detect(listOf(cell("Итог", 5, tab = tabB), cell("Итог", 2)))?.tab)
        assertEquals(5, SheetTotals.detect(listOf(cell("Итог", 2), cell("Итог", 5)))?.column)
    }

    @Test fun `the column is found again by its path`() {
        val ref = SheetColumnRef("ИТОГО баллов", 11)

        assertEquals(12, SheetTotals.find(List(12) { "" } + "итого  баллов", ref, 13))
        assertEquals(9, SheetTotals.find(listOf("", "ИТОГО баллов") + List(7) { "" } + "ИТОГО баллов", ref, 10))
    }

    @Test fun `a lost path falls back to the old index while the tab is wide enough`() {
        val ref = SheetColumnRef("ИТОГО баллов", 11)

        assertEquals(11, SheetTotals.find(List(13) { "" }, ref, 13))
        assertNull(SheetTotals.find(List(11) { "" }, ref, 11))
        assertEquals(3, SheetTotals.find(List(5) { "x" }, SheetColumnRef("", 3), 5))
    }
}
