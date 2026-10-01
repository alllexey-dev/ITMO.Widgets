package dev.alllexey.itmowidgets.feature.recordbook.data.sheets

import dev.alllexey.itmowidgets.core.resources.GoogleSheetUrl
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.OwnIdentity
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.RowSearch
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetFixtures
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetGrid
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetHeaders
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRows
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTab
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTabGrid
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SheetHtmlGridTest {
    private val html = SheetHtmlGrid.parse(SheetFixtures.text("grid_htmlview.html"))
    private val csv = SheetFixtures.csv("grades_multiheader.csv")

    @Test fun `the html tab has the size of the csv export`() {
        assertEquals(csv.height, html.height)
        assertEquals(csv.width, html.width)
    }

    @Test fun `merged cells keep the text in the top left cell`() {
        assertEquals("Преподаватель: Тестов Пётр Петрович", html.cell(0, 0))
        assertEquals((1..12).map { "" }, (1..12).map { html.cell(0, it) })
        assertEquals("Посещаемость", html.cell(1, 3))
        assertEquals(listOf("", ""), listOf(html.cell(1, 4), html.cell(1, 5)))
        assertEquals("ИТОГО баллов", html.cell(1, 11))
        assertEquals(listOf("", ""), listOf(html.cell(2, 11), html.cell(3, 11)))
        assertEquals("60-100", html.cell(3, 12))
    }

    @Test fun `row and column headers are not cells`() {
        assertFalse(html.rows.flatten().any { it == "A" || it == "M" })
        assertEquals("№", html.cell(2, 0))
        assertEquals("123456", html.cell(5, 0))
    }

    @Test fun `paths and the own row match the csv export`() {
        val identity = OwnIdentity(123456, "Тестов Тест Тестович")
        val url = GoogleSheetUrl("1TestSheetIdForUnitTests_0123456789-abc", null)
        fun own(grid: SheetGrid) = (SheetRows.find(SheetWorkbook(url, listOf(SheetTabGrid(SheetTab(22, "P3110"), grid))), identity)
            as RowSearch.Found).matches.single()

        assertEquals(SheetHeaders.paths(csv, 4, 0), SheetHeaders.paths(html, 4, 0))
        assertEquals("ИТОГО баллов", SheetHeaders.paths(html, 4, 0)[11])
        assertEquals(own(csv), own(html))
        assertEquals(csv.rows[5], html.rows[5])
    }

    @Test fun `a page without a table is an empty grid`() {
        assertEquals(0, SheetHtmlGrid.parse(SheetFixtures.text("login.html")).height)
    }
}
