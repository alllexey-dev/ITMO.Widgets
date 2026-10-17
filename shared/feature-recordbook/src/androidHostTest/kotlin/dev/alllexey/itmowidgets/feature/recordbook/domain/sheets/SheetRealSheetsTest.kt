package dev.alllexey.itmowidgets.feature.recordbook.domain.sheets

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [SheetTotals.decide] over the 13 real sheets of issue 419, anonymized in `resources/sheets/real` (names, ISUs,
 * groups and teachers made up; layout, headers and values kept), with the real total of each tab in its `truth.tsv`.
 */
class SheetRealSheetsTest {
    private val sheets = RealSheets(File(checkNotNull(javaClass.classLoader!!.getResource("sheets/real")).toURI()))

    /** A student in [tab] at sheet [row] (1-based) whose total is column [column] of tab [totalTab], shown as [value]. */
    private data class Case(
        val sheet: String,
        val tab: Long,
        val row: Int,
        val totalTab: Long,
        val column: String,
        val value: String,
        val confidence: TotalConfidence,
    )

    /** The first, middle and last student of every tab, an empty total, a total to check, a row in two tabs. */
    private val cases = listOf(
        Case("ai", 0, 4, 0, "Z", "39", TotalConfidence.HIGH),
        Case("ai", 0, 24, 0, "Z", "37", TotalConfidence.HIGH),
        Case("ai", 0, 44, 0, "Z", "41", TotalConfidence.HIGH),
        // Issue 419: these four are also in «Copy of Общий список» of an earlier run, under a stronger «Итого».
        Case("ak", 580192402, 43, 580192402, "Y", "10", TotalConfidence.HIGH),
        Case("ak", 580192402, 44, 580192402, "Y", "0", TotalConfidence.HIGH),
        Case("ak", 580192402, 45, 580192402, "Y", "0", TotalConfidence.HIGH),
        Case("ak", 580192402, 46, 580192402, "Y", "0", TotalConfidence.HIGH),
        Case("ak", 580192402, 3, 580192402, "Y", "0", TotalConfidence.HIGH),
        Case("ak", 580192402, 25, 580192402, "Y", "0", TotalConfidence.HIGH),
        Case("ak", 580192402, 47, 580192402, "Y", "0", TotalConfidence.HIGH),
        Case("career", 0, 4, 0, "N", "31,5", TotalConfidence.HIGH),
        Case("career", 0, 17, 0, "N", "6,5", TotalConfidence.HIGH),
        Case("career", 0, 32, 0, "N", "20,5", TotalConfidence.HIGH),
        Case("economics", 0, 3, 0, "U", "3", TotalConfidence.HIGH),
        Case("economics", 0, 17, 0, "U", "3", TotalConfidence.HIGH),
        Case("economics", 0, 31, 0, "U", "3", TotalConfidence.HIGH),
        Case("english-49", 1819564669, 7, 1819564669, "R", "0", TotalConfidence.HIGH),
        Case("english-49", 1819564669, 20, 1819564669, "R", "0", TotalConfidence.LOW),
        Case("english-49", 1819564669, 32, 1819564669, "R", "0", TotalConfidence.LOW),
        Case("english-49", 1819564669, 8, 1819564669, "R", "0", TotalConfidence.LOW),
        Case("english-49", 1819564669, 9, 1819564669, "R", "0", TotalConfidence.LOW),
        Case("english-59", 1819564669, 7, 1819564669, "R", "0", TotalConfidence.HIGH),
        Case("english-59", 1819564669, 16, 1819564669, "R", "0", TotalConfidence.LOW),
        Case("english-59", 1819564669, 25, 1819564669, "R", "0", TotalConfidence.HIGH),
        Case("english-59", 1819564669, 8, 1819564669, "R", "0", TotalConfidence.LOW),
        Case("english-59", 1819564669, 9, 1819564669, "R", "0", TotalConfidence.LOW),
        Case("english-68", 1819564669, 7, 1819564669, "R", "0", TotalConfidence.HIGH),
        Case("english-68", 1819564669, 18, 1819564669, "R", "0", TotalConfidence.HIGH),
        Case("english-68", 1819564669, 29, 1819564669, "R", "0", TotalConfidence.HIGH),
        Case("history", 0, 3, 0, "V", "25", TotalConfidence.HIGH),
        Case("history", 0, 18, 0, "V", "24,5", TotalConfidence.HIGH),
        Case("history", 0, 33, 0, "V", "26", TotalConfidence.HIGH),
        Case("history", 2136196208, 3, 2136196208, "W", "11", TotalConfidence.HIGH),
        Case("history", 2136196208, 19, 2136196208, "W", "16", TotalConfidence.HIGH),
        Case("history", 2136196208, 34, 2136196208, "W", "19", TotalConfidence.HIGH),
        Case("history", 272839308, 3, 272839308, "W", "22", TotalConfidence.HIGH),
        Case("history", 272839308, 17, 272839308, "W", "17", TotalConfidence.HIGH),
        Case("history", 272839308, 32, 272839308, "W", "4", TotalConfidence.HIGH),
        Case("informatics", 0, 4, 0, "AW", "7,00", TotalConfidence.HIGH),
        Case("informatics", 0, 24, 0, "AW", "2,85", TotalConfidence.HIGH),
        Case("informatics", 0, 43, 0, "AW", "2,85", TotalConfidence.HIGH),
        Case("linux", 2047532809, 9, 2047532809, "K", "15", TotalConfidence.HIGH),
        Case("linux", 2047532809, 20, 2047532809, "K", "0", TotalConfidence.HIGH),
        Case("linux", 2047532809, 30, 2047532809, "K", "20", TotalConfidence.HIGH),
        Case("linux", 1409320707, 9, 1409320707, "K", "20", TotalConfidence.HIGH),
        Case("linux", 1409320707, 22, 1409320707, "K", "16", TotalConfidence.HIGH),
        Case("linux", 1409320707, 34, 1409320707, "K", "0", TotalConfidence.HIGH),
        Case("opd", 792878054, 2, 792878054, "AQ", "0,00", TotalConfidence.HIGH),
        Case("opd", 792878054, 4, 792878054, "AQ", "0,00", TotalConfidence.HIGH),
        Case("opd", 792878054, 5, 792878054, "AQ", "0,00", TotalConfidence.HIGH),
        Case("opd", 1163413989, 3, 1163413989, "AM", "0,00", TotalConfidence.HIGH),
        Case("opd", 1163413989, 10, 1163413989, "AM", "0,00", TotalConfidence.HIGH),
        Case("opd", 1163413989, 16, 1163413989, "AM", "0,00", TotalConfidence.HIGH),
        Case("opd", 428132789, 3, 428132789, "AM", "0,00", TotalConfidence.HIGH),
        Case("opd", 428132789, 9, 428132789, "AM", "0,00", TotalConfidence.HIGH),
        Case("opd", 428132789, 15, 428132789, "AM", "0,00", TotalConfidence.HIGH),
        Case("opd", 2095873118, 3, 2095873118, "AM", "0,00", TotalConfidence.HIGH),
        Case("opd", 2095873118, 11, 2095873118, "AM", "46 302,00", TotalConfidence.HIGH),
        Case("opd", 2095873118, 19, 2095873118, "AM", "0,00", TotalConfidence.HIGH),
        Case("opd", 1365648186, 3, 1365648186, "AM", "0,00", TotalConfidence.HIGH),
        Case("opd", 1365648186, 10, 1365648186, "AM", "12,00", TotalConfidence.HIGH),
        Case("opd", 1365648186, 15, 1365648186, "AM", "0,00", TotalConfidence.HIGH),
        Case("prog", 0, 3, 0, "R", "10", TotalConfidence.HIGH),
        Case("prog", 0, 18, 0, "R", "0", TotalConfidence.HIGH),
        Case("prog", 0, 32, 0, "R", "10", TotalConfidence.HIGH),
        Case("prog", 314308399, 3, 314308399, "R", "6", TotalConfidence.HIGH),
        Case("prog", 314308399, 15, 314308399, "R", "0", TotalConfidence.HIGH),
        Case("prog", 314308399, 26, 314308399, "R", "11", TotalConfidence.HIGH),
        Case("web", 478773056, 2, 696774553, "BB", "0", TotalConfidence.HIGH),
        Case("web", 478773056, 4, 696774553, "BB", "0", TotalConfidence.HIGH),
        Case("web", 478773056, 6, 478773056, "BB", "13.7", TotalConfidence.HIGH),
        Case("web", 988076790, 2, 988076790, "BB", "0", TotalConfidence.HIGH),
        Case("web", 988076790, 8, 988076790, "BB", "11", TotalConfidence.HIGH),
        Case("web", 988076790, 14, 988076790, "BB", "0", TotalConfidence.HIGH),
        Case("web", 988076790, 6, 988076790, "BB", "", TotalConfidence.HIGH),
        Case("web", 827706332, 4, 1643004885, "BB", "11", TotalConfidence.LOW),
        Case("web", 827706332, 12, 827706332, "BB", "0", TotalConfidence.HIGH),
        Case("web", 827706332, 20, 827706332, "BB", "1", TotalConfidence.LOW),
        Case("web", 496725019, 4, 496725019, "BB", "10", TotalConfidence.HIGH),
        Case("web", 496725019, 11, 496725019, "BB", "11.9", TotalConfidence.HIGH),
        Case("web", 496725019, 18, 496725019, "BB", "0", TotalConfidence.HIGH),
        Case("web", 496725019, 12, 827706332, "BB", "1", TotalConfidence.LOW),
        Case("web", 696774553, 4, 696774553, "BB", "0", TotalConfidence.HIGH),
        Case("web", 696774553, 9, 696774553, "BB", "0", TotalConfidence.HIGH),
        Case("web", 696774553, 13, 696774553, "BB", "0", TotalConfidence.HIGH),
        Case("web", 696774553, 7, 696774553, "BB", "", TotalConfidence.HIGH),
        Case("web", 1643004885, 2, 1643004885, "BB", "0", TotalConfidence.HIGH),
        Case("web", 1643004885, 9, 1643004885, "BB", "0", TotalConfidence.HIGH),
        Case("web", 1643004885, 15, 1643004885, "BB", "11", TotalConfidence.LOW),
        Case("web", 1451203887, 2, 1451203887, "BB", "0", TotalConfidence.HIGH),
        Case("web", 1451203887, 12, 1451203887, "BB", "0", TotalConfidence.HIGH),
        Case("web", 1451203887, 21, 1451203887, "BB", "35.5", TotalConfidence.HIGH),
        Case("web", 1451203887, 14, 1191968564, "BB", "12", TotalConfidence.LOW),
        Case("web", 1191968564, 2, 1191968564, "BB", "0", TotalConfidence.HIGH),
        Case("web", 1191968564, 9, 1191968564, "BB", "12", TotalConfidence.HIGH),
        Case("web", 1191968564, 16, 1191968564, "BB", "12", TotalConfidence.LOW),
        Case("web", 1108424186, 2, 1108424186, "BB", "11", TotalConfidence.HIGH),
        Case("web", 1108424186, 8, 1108424186, "BB", "0", TotalConfidence.HIGH),
        Case("web", 1108424186, 13, 1108424186, "BB", "24", TotalConfidence.HIGH),
    )

    @Test fun `each case gets the real total`() {
        val failures = cases.mapNotNull { case ->
            val tabs = sheets.tabs(case.sheet)
            val student = sheets.students(case.sheet, tabs, setOf(case.tab)).single { it.row == case.row - 1 }
            val decision = student.decision
            val got = decision?.let { Case(case.sheet, case.tab, case.row, it.total.tab.gid, columnName(it.total.column), it.total.value, it.confidence) }
            if (got == case) null else "expected $case, got $got"
        }
        assertEquals(emptyList<String>(), failures)
    }

    /**
     * Every student of every tab with a real total: all but the known hard cases get it. Those are the same name
     * twice in one tab (the viewer picks the row) and one row whose total is empty in both of its tabs.
     */
    @Test fun `every student gets the real total but the known hard cases`() {
        var right = 0
        val ambiguous = mutableListOf<String>()
        val wrong = mutableListOf<String>()
        sheets.sheets.forEach { sheet ->
            val tabs = sheets.tabs(sheet)
            val truth = sheets.truth.getValue(sheet)
            sheets.students(sheet, tabs, truth.map { it.first }.toSet()).forEach { student ->
                val where = "$sheet/${student.tab.tab.gid}/${student.row + 1}"
                when {
                    student.search is RowSearch.Ambiguous -> ambiguous += where
                    sheets.right(student, tabs, truth) -> right++
                    else -> wrong += where
                }
            }
        }
        assertEquals(624, right)
        assertEquals(listOf("opd/1365648186/6"), wrong)
        assertEquals(10, ambiguous.size)
    }
}
