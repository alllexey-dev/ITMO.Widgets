package dev.alllexey.itmowidgets.feature.recordbook.domain.sheets

import dev.alllexey.itmowidgets.core.resources.GoogleSheetUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetRowsTest {
    private val identity = OwnIdentity(123456, "Тестов Тест Тестович")
    private val url = GoogleSheetUrl("1TestSheetIdForUnitTests_0123456789-abc", null)
    private val grades = SheetTabGrid(SheetTab(22, "P3110"), SheetFixtures.csv("grades_multiheader.csv"))
    private val roster = SheetTabGrid(SheetTab(11, "All"), SheetFixtures.csv("roster.csv"))

    @Test fun `the ISU in the number column finds one row with its name`() {
        val search = SheetRows.find(SheetWorkbook(url, listOf(grades)), identity)

        val match = (search as RowSearch.Found).matches.single()
        assertEquals(5, match.row)
        assertEquals(0, match.keyColumn)
        assertEquals("123456", match.key)
        assertEquals(KeyKind.ISU, match.kind)
        assertEquals("Тестов Тест Тестович", match.label)
    }

    @Test fun `one key in two tabs is found in both`() {
        val search = SheetRows.find(SheetWorkbook(url, listOf(roster, grades)), identity)

        val matches = (search as RowSearch.Found).matches
        assertEquals(listOf(11L, 22L), matches.map { it.tab.gid })
        assertEquals(setOf("123456"), matches.mapTo(mutableSetOf()) { it.key })
        assertEquals(listOf(2, 5), matches.map { it.row })
    }

    @Test fun `without an ISU a form of the name finds the row`() {
        val tab = SheetTabGrid(SheetTab(0, "Лист1"), SheetFixtures.csv("name_only.csv"))

        val match = (SheetRows.find(SheetWorkbook(url, listOf(tab)), identity) as RowSearch.Found).matches.single()
        assertEquals(KeyKind.NAME, match.kind)
        assertEquals("тестов т.т.", match.key)
        assertEquals("Тестов Т. Т.", match.label)
        assertEquals(2, match.row)
    }

    @Test fun `two rows in one tab are a choice and no row is nothing`() {
        val duplicates = SheetTabGrid(SheetTab(0, "Лист1"), SheetFixtures.csv("duplicate_rows.csv"))

        val search = SheetRows.find(SheetWorkbook(url, listOf(duplicates)), identity)
        assertEquals(listOf(1, 3), (search as RowSearch.Ambiguous).candidates.map { it.row })
        assertEquals(
            RowSearch.NotFound,
            SheetRows.find(SheetWorkbook(url, listOf(grades)), OwnIdentity(999999, "Неизвестный Человек Иванович")),
        )
    }

    @Test fun `different keys in different tabs are a choice`() {
        val names = SheetTabGrid(SheetTab(0, "Лист1"), SheetFixtures.csv("name_only.csv"))
        val full = SheetTabGrid(SheetTab(1, "Лист2"), SheetFixtures.csv("duplicate_rows.csv").let { grid ->
            SheetGrid(grid.rows.take(3))
        })

        val search = SheetRows.find(SheetWorkbook(url, listOf(names, full)), OwnIdentity(null, "Тестов Тест Тестович"))

        assertTrue(search is RowSearch.Ambiguous)
    }

    @Test fun `the row is located again after rows and the key column move`() {
        val moved = SheetGrid(
            grades.grid.rows.let { rows -> rows.take(4) + rows.drop(4).reversed() }.map { it.drop(1) + it.first() }
        )
        val tab = SheetTabGrid(grades.tab, moved)

        val match = SheetRows.locate(tab, "123456", KeyKind.ISU, keyColumn = 0)

        assertEquals(6, match?.row)
        assertEquals(12, match?.keyColumn)
        assertEquals("66,3", moved.cell(6, 10))
    }

    @Test fun `two rows with the key are not located`() {
        val doubled = SheetTabGrid(grades.tab, SheetGrid(grades.grid.rows + listOf(grades.grid.rows[5])))

        assertNull(SheetRows.locate(doubled, "123456", KeyKind.ISU, keyColumn = 0))
        assertNull(SheetRows.locate(grades, "999999", KeyKind.ISU, keyColumn = 0))
    }

    @Test fun `the manual choice lists the students without the header and the teacher`() {
        val options = SheetRows.options(grades)

        assertEquals(listOf(4, 5, 6, 7), options.map { it.row })
        assertEquals(
            listOf("Тестова Анна Сергеевна", "Тестов Тест Тестович", "Примеров Пример Примерович", "Образцова Мария Ивановна"),
            options.map { it.label },
        )
        assertEquals("123456", options[1].key)
        assertEquals(KeyKind.ISU, options[1].kind)
        assertEquals(0, options[1].keyColumn)
    }

    @Test fun `without an ISU column the choice is keyed by the name`() {
        val options = SheetRows.options(SheetTabGrid(SheetTab(0, "Лист1"), SheetFixtures.csv("name_only.csv")))

        assertEquals(listOf("тестова а.с.", "тестов т.т.", "примеров п.п."), options.map { it.key })
        assertEquals(setOf(KeyKind.NAME), options.mapTo(mutableSetOf()) { it.kind })
        assertEquals(emptyList<SheetRowMatch>(), SheetRows.options(SheetTabGrid(SheetTab(0, ""), SheetGrid(emptyList()))))
    }
}
