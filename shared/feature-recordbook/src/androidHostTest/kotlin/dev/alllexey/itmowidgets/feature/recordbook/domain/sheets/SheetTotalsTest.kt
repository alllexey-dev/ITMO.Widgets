package dev.alllexey.itmowidgets.feature.recordbook.domain.sheets

import dev.alllexey.itmowidgets.core.resources.GoogleSheetUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [SheetTotals] on small synthetic tabs, one per trap seen in real sheets (docs/features/sheet-scores.md). */
class SheetTotalsTest {
    private val identity = OwnIdentity(123456, "Тест Тестов", "Тестов", "Тест", "Тестович")
    private val url = GoogleSheetUrl("1TestSheetIdForUnitTests_0123456789-abc", null)

    private fun tab(gid: Long, name: String, vararg lines: String) =
        SheetTabGrid(SheetTab(gid, name), CsvGrid.parse(lines.joinToString("\n")))

    private fun decide(vararg tabs: SheetTabGrid): SheetTotalDecision? {
        val book = SheetWorkbook(url, tabs.toList())
        val found = SheetRows.find(book, identity) as RowSearch.Found
        return SheetTotals.decide(tabs.toList(), found.matches)
    }

    /** The own row's values among three other students' (the own ones when not given), for the column statistics. */
    private fun roster(own: String, vararg others: String): Array<String> = arrayOf(
        "100001,Тестова Анна Сергеевна,${others.getOrElse(0) { own }}",
        "123456,Тестов Тест Тестович,$own",
        "100002,Примеров Пример Примерович,${others.getOrElse(1) { own }}",
        "100003,Образцова Ольга Олеговна,${others.getOrElse(2) { own }}",
    )

    private val SheetTotalDecision.letter get() = columnName(total.column)

    @Test fun `the own cells skip the key and the name and keep values as the sheet shows them`() {
        val grid = SheetTabGrid(SheetTab(22, "P3110"), SheetFixtures.csv("grades_multiheader.csv"))
        val match = (SheetRows.find(SheetWorkbook(url, listOf(grid)), identity) as RowSearch.Found).matches.single()
        val cells = SheetTotals.cells(grid, match)

        assertEquals((2..12).toList(), cells.map { it.column })
        val byPath = cells.associate { it.headerPath to it.value }
        assertEquals("66,3", byPath["ИТОГО баллов"])
        assertEquals("5A", byPath["Оценка · 60-100"])
        assertEquals("P3110", byPath["Группа"])
    }

    @Test fun `no total header, no decision`() {
        val grid = SheetTabGrid(SheetTab(22, ""), SheetFixtures.csv("no_total.csv"))
        assertNull(decide(grid))
    }

    @Test fun `of several totals the whole «Итого» wins over a module's «ИТОГО практика» and the sum checks out`() {
        val decision = decide(
            tab(
                0, "",
                "ИСУ,ФИО,Занятие 1,Занятие 2,ИТОГО практика,Квиз,ИТОГО текущий контроль,Экзамен,Итого,Оценка",
                *roster(
                    "3,5,8,2,10,12,22,зачтено",
                    "1,2,3,1,4,10,14,зачтено", "2,2,4,0,4,0,4,", "3,3,6,1,7,5,12,",
                ),
            ),
        )!!

        assertEquals("I", decision.letter)
        assertEquals("22", decision.total.value)
        assertEquals(TotalReason.SUM_MATCHES, decision.reason)
        assertEquals(TotalConfidence.HIGH, decision.confidence)
    }

    @Test fun `a module's «∑» loses to the overall «∑» further right`() {
        val decision = decide(
            tab(
                0, "",
                ",,Лаб. 1,Лаб. 3,,,Экзамен,∑",
                "ИСУ,ФИО,,acc32,f32a,∑,,",
                *roster("5,10,10,20,,25", "0,10,8,18,,18", "5,,,0,,5"),
            ),
        )!!

        assertEquals("H", decision.letter)
        assertEquals("25", decision.total.value)
    }

    @Test fun `bonus and semester columns are details, the «Сумма» is the total and the grade text is not`() {
        val decision = decide(
            tab(
                0, "",
                "ИСУ,ФИО,Лаб. р.,Дом. задания,Семестр,Бонусы,Экзамен,Сумма,Оценка",
                ",,38,22,80,0,\"20,00\",\"100,00\",5A",
                *roster(
                    "\"7,00\",0,\"7,00\",0,,\"7,00\",",
                    "\"3,00\",0,\"3,00\",0,,\"3,00\",", "\"13,50\",0,\"13,50\",1,,\"14,50\",",
                ),
            ),
        )!!

        assertEquals("H", decision.letter)
        assertEquals("7,00", decision.total.value)
    }

    @Test fun `a summary row under the students is not a student`() {
        assertNull(SheetIdentity.personKind("Итого по группе"))
        assertNull(SheetIdentity.personKind("Средний балл"))
        val decision = decide(
            tab(
                0, "",
                "ИСУ,ФИО,ЛР1,ЛР2,Сумма",
                *roster("10,5,15"),
                ",Итого по группе,40,20,60",
            ),
        )!!

        assertEquals("E", decision.letter)
        assertEquals("15", decision.total.value)
    }

    @Test fun `a total left of its details is found and checked against them`() {
        val decision = decide(
            tab(
                0, "",
                "ИСУ,ФИО,Итог,ЛР1,ЛР2,ЛР3",
                *roster("25,10,10,5", "12,6,6,0", "7,7,,", "20,10,,10"),
            ),
        )!!

        assertEquals("C", decision.letter)
        assertEquals(TotalReason.SUM_MATCHES, decision.reason)
    }

    @Test fun `points beat a percentage total, and a percentage alone asks to be checked`() {
        val both = decide(
            tab(0, "", "ИСУ,ФИО,ЛР1,Итог %,Итог", *roster("10,50%,10", "20,100%,20")),
        )!!
        assertEquals("E", both.letter)

        val percent = decide(tab(0, "", "ИСУ,ФИО,ЛР1,Итог %", *roster("10,50%", "20,100%")))!!
        assertEquals("D", percent.letter)
        assertEquals(TotalReason.NOT_POINTS, percent.reason)
        assertEquals(TotalConfidence.LOW, percent.confidence)
    }

    @Test fun `a merged total title over empty helper columns gives the filled one`() {
        val decision = decide(
            tab(
                0, "",
                ",,weeks 1-16,,Total for the semester,,,",
                "ISU ID,FULL NAME,H/W 1,Test 1,,,,",
                *roster("7,3,10,,,", "5,1,6,,,"),
            ),
        )!!

        assertEquals("E", decision.letter)
        assertEquals("10", decision.total.value)
    }

    @Test fun `formula errors and «TRUE» are not points, so a broken lookup tab loses to the group tab`() {
        val lookup = tab(
            1, "Общий список",
            "№ таб.,Ф.И.О.,Check,Итого",
            "100001,Тестова Анна Сергеевна,Not found,#REF!",
            "123456,Тестов Тест Тестович,Not found,#REF!",
            "100002,Примеров Пример Примерович,Found,#REF!",
        )
        val group = tab(2, "1.1", "ISU-ID,ФИО,ЛР1,Итого,Зачет", *roster("12,\"12,00\",TRUE"))

        val decision = decide(lookup, group)!!

        assertEquals(group.tab, decision.total.tab)
        assertEquals("D", decision.letter)
    }

    @Test fun `«Итого · Баллы» beats the grade under «Итого · Оценка» further right`() {
        val decision = decide(
            tab(
                0, "",
                ",,,Итог балл,Итого,",
                "ISU-ID,ФИО,ЛР1,,Баллы,Оценка",
                *roster("10,10,\"10,00\",Незачет", "0,0,\"0,00\",Незачет"),
            ),
        )!!

        assertEquals("E", decision.letter)
        assertEquals("10,00", decision.total.value)
    }

    @Test fun `in two group tabs the one with more of the own values wins, and the viewer is asked to check`() {
        val old = tab(1, "1.2", "ИСУ,ФИО,ЛР1,ЛР2,Итог", *roster(",,0"))
        val moved = tab(2, "2.4", "ИСУ,ФИО,ЛР1,ЛР2,Итог", *roster("6,5,11"))

        val decision = decide(old, moved)!!

        assertEquals(moved.tab, decision.total.tab)
        assertEquals("11", decision.total.value)
        assertEquals(TotalReason.SEVERAL_TABS, decision.reason)
        assertEquals(TotalConfidence.LOW, decision.confidence)
        assertEquals(listOf(moved.tab, old.tab), decision.candidates.map { it.tab }.distinct())
    }

    @Test fun `a copy of an old list loses to the current one despite its stronger «Итого» (issue 419)`() {
        val current = tab(1, "Общий список", ",,Лаб. 1,Экзамен,∑", "№,ФИО,,,", *roster("10,,10"))
        val copy = tab(2, "Copy of Общий список", ",,Лаб. 1,Итого", "№,ФИО,,", *roster("0,0.00"))

        val decision = decide(copy, current)!!

        assertEquals(current.tab, decision.total.tab)
        assertEquals("10", decision.total.value)
        assertEquals(TotalConfidence.HIGH, decision.confidence)
    }

    @Test fun `the BARS columns are a weaker total than «Итог», and alone they ask to be checked`() {
        val decision = decide(
            tab(0, "", ",,ЛР1,БАРС,,Итог", "ИСУ,ФИО,Защ.,ЛР1,Э,", *roster("9,9,0,9")),
        )!!
        assertEquals("F", decision.letter)

        val bars = decide(tab(0, "", "ИСУ,ФИО,ЛР1,БАРС", *roster("9,9")))!!
        assertEquals("D", bars.letter)
        assertEquals(TotalReason.WEAK_HEADER, bars.reason)
    }

    @Test fun `a max-points row under the header is not a student and «ВСЕГО» is a total`() {
        val decision = decide(
            tab(
                0, "",
                "№ п/п,№ таб.,Ф.И.О.,ЛР0,ЛР1,ЭКЗАМЕН,ВСЕГО",
                ",,max,7,12,20,104",
                "1,100001,Тестова Анна Сергеевна,7,,,7",
                "2,123456,Тестов Тест Тестович,7,12,,19",
            ),
        )!!

        assertEquals("G", decision.letter)
        assertEquals("19", decision.total.value)
        assertEquals("ВСЕГО · 104", decision.total.headerPath)
    }

    @Test fun `a total empty for everyone while the row has points asks to be checked`() {
        val decision = decide(
            tab(
                0, "",
                "ISU ID,FULL NAME,H/W 1,H/W 2,Speaking,Total for the semester",
                *roster("7,7,10,0", "7,0,12,0", "0,0,0,0", "6,7,10,0"),
            ),
        )!!

        assertEquals("F", decision.letter)
        assertEquals(TotalReason.EMPTY_FOR_ALL, decision.reason)
        assertEquals(TotalConfidence.LOW, decision.confidence)
    }

    @Test fun `a block of the next semester, empty for everyone, loses to this semester's sum`() {
        val decision = decide(
            tab(
                0, "",
                ",Блок «Осень»,,,Блок «Весна»,,,",
                "ФИО,Практика 1,Практика 2,Сумма баллов,Практика 1,Сумма баллов,Экзамен,Итог",
                "Тестова Анна,6,3,9,,0,,0",
                "Тестов Тест,6,4,10,,0,,0",
                "Примеров Пример,5,3,8,,0,,0",
            ),
        )!!

        assertEquals("D", decision.letter)
        assertEquals("10", decision.total.value)
    }

    @Test fun `numbers are read as the sheet writes them`() {
        assertEquals(7.5, SheetTotals.number("7,5")!!, 0.0)
        assertEquals(-3.0, SheetTotals.number("−3")!!, 0.0)
        assertEquals(1234.0, SheetTotals.number("1 234")!!, 0.0)
        assertNull(SheetTotals.number("85%"))
        assertNull(SheetTotals.number("5A"))
    }

    @Test fun `an empty total column remains empty without silent fallback`() {
        val sheetTab = tab(
            0, "",
            "ISU ID,FULL NAME,H/W 1,H/W 2,Speaking,Total for the semester",
            *roster("7,7,10,", "7,7,10,", "7,7,10,", "6,7,10,"),
        )
        val decision = decide(sheetTab)!!

        assertEquals("F", decision.letter)
        assertEquals("", decision.total.value)
        assertEquals(TotalReason.EMPTY_FOR_ALL, decision.reason)
        assertEquals(TotalConfidence.LOW, decision.confidence)

        // Activity sum computes points across activity columns
        val match = SheetRowMatch(sheetTab.tab, 1, 0, "100000", KeyKind.ISU, "0")
        assertEquals("24", SheetTotals.activitySum(sheetTab.grid, match))
        val activityCell = SheetTotals.activityCell(sheetTab, match)!!
        assertEquals(SheetTotals.ACTIVITIES_SUM_PATH, activityCell.headerPath)
        assertEquals("24", activityCell.value)

        // Student with no points has null activity sum
        val noPointsMatch = SheetRowMatch(sheetTab.tab, 0, 0, "100000", KeyKind.ISU, "0")
        val noPointsTab = tab(
            0, "",
            "ISU ID,FULL NAME,H/W 1,H/W 2,Speaking,Total for the semester",
            *roster(",,,", "7,7,10,", "7,7,10,", "6,7,10,"),
        )
        assertNull(SheetTotals.activitySum(noPointsTab.grid, noPointsMatch))
        assertNull(SheetTotals.activityCell(noPointsTab, noPointsMatch))
    }

    @Test fun `matan journal without total formula leaves total empty and provides activity sum`() {
        val grid = SheetTabGrid(SheetTab(1, "МатАн"), SheetFixtures.csv("matan.csv"))
        val studentWithPoints = OwnIdentity(100004, "Тестов Тест Тестович", "Тестов", "Тест", "Тестович")
        val foundWithPoints = (SheetRows.find(SheetWorkbook(url, listOf(grid)), studentWithPoints) as RowSearch.Found).matches
        val decisionWithPoints = SheetTotals.decide(listOf(grid), foundWithPoints)!!

        assertEquals("Y", decisionWithPoints.letter)
        assertEquals("", decisionWithPoints.total.value)
        assertEquals(TotalReason.EMPTY_FOR_ALL, decisionWithPoints.reason)
        assertEquals(TotalConfidence.LOW, decisionWithPoints.confidence)

        assertEquals("3", SheetTotals.activitySum(grid.grid, foundWithPoints.first()))
        val activityCell = SheetTotals.activityCell(grid, foundWithPoints.first())!!
        assertEquals(SheetTotals.ACTIVITIES_SUM_PATH, activityCell.headerPath)
        assertEquals("3", activityCell.value)

        val studentNoPoints = OwnIdentity(100002, "Примеров Пример Примерович", "Примеров", "Пример", "Примерович")
        val foundNoPoints = (SheetRows.find(SheetWorkbook(url, listOf(grid)), studentNoPoints) as RowSearch.Found).matches
        val decisionNoPoints = SheetTotals.decide(listOf(grid), foundNoPoints)!!

        assertEquals("Y", decisionNoPoints.letter)
        assertEquals("", decisionNoPoints.total.value)
        assertNull(SheetTotals.activitySum(grid.grid, foundNoPoints.first()))
        assertNull(SheetTotals.activityCell(grid, foundNoPoints.first()))
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
