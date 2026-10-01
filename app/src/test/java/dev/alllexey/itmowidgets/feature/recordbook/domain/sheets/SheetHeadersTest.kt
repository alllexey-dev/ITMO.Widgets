package dev.alllexey.itmowidgets.feature.recordbook.domain.sheets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SheetHeadersTest {
    private val grid = SheetFixtures.csv("grades_multiheader.csv")
    private val paths = SheetHeaders.paths(grid, firstDataRow = 4, keyColumn = 0)

    @Test fun `the students start under the header`() {
        assertEquals(4, SheetHeaders.firstDataRow(grid, row = 5, keyColumn = 0, kind = KeyKind.ISU))
        assertEquals(4, SheetHeaders.firstDataRow(grid, row = 7, keyColumn = 0, kind = KeyKind.ISU))
    }

    @Test fun `the tab title is not part of any path`() {
        assertFalse(paths.any { "Преподаватель" in it })
        assertEquals("№", paths[0])
    }

    @Test fun `group titles span their columns and sub-headers join with a dot`() {
        assertEquals("Посещаемость · 11.02 · [0-2]", paths[3])
        assertEquals("Посещаемость · 25.02 · [0-2]", paths[5])
        assertEquals("ЛР1 · Защ (%)", paths[7])
        assertEquals("Тесты к видеолекциям · Л2", paths[9])
        assertEquals("КР Итог · Min = 4", paths[10])
        assertEquals("ИТОГО баллов", paths[11])
        assertEquals("Оценка · 60-100", paths[12])
    }

    @Test fun `the last header row does not span`() {
        val small = SheetGrid(
            listOf(
                listOf("", "Группа", "", ""),
                listOf("", "a", "", ""),
                listOf("123456", "1", "2", "3"),
            )
        )

        assertEquals(listOf("", "Группа · a", "Группа", "Группа"), SheetHeaders.paths(small, 2, 0))
    }

    @Test fun `only the six nearest header rows count`() {
        val tall = SheetGrid((0 until 8).map { listOf("", "h$it") } + listOf(listOf("123456", "1")))

        assertEquals(8, SheetHeaders.firstDataRow(tall, 8, 0, KeyKind.ISU))
        assertEquals("h2 · h3 · h4 · h5 · h6 · h7", SheetHeaders.paths(tall, 8, 0)[1])
    }

    @Test fun `a column without a header has an empty path`() {
        val bare = SheetGrid(listOf(listOf("№", "A", ""), listOf("123456", "1", "2")))

        assertEquals(listOf("№", "A", ""), SheetHeaders.paths(bare, 1, 0))
    }
}
