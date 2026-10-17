package dev.alllexey.itmowidgets.feature.recordbook.domain.sheets

import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * A debugging entry point, not a check: runs the own-row search and [SheetTotals.decide] for every student of real
 * sheets downloaded to a local folder ([RealSheets] layout) and writes each decision to `decisions.txt` there.
 * Skipped without the folder:
 *
 * `./gradlew :shared:feature-recordbook:testAndroidHostTest --tests '*SheetRawSheetsTest*' --rerun -PsheetScores.raw=<dir>`
 *
 * Real sheets never leave `<dir>`. With a `truth.tsv` only the students of its tabs run and the accuracy closes the file.
 */
class SheetRawSheetsTest {
    @Test fun decisions() {
        val root = System.getProperty("sheetScores.raw")?.let(::File)
        assumeTrue(root?.isDirectory == true)
        val sheets = RealSheets(root!!)
        val report = StringBuilder()
        var right = 0
        var total = 0
        sheets.sheets.forEach { sheet ->
            val tabs = sheets.tabs(sheet)
            val truth = sheets.truth[sheet]
            report.appendLine("##### $sheet")
            sheets.students(sheet, tabs, truth?.map { it.first }?.toSet()).groupBy { it.tab }.forEach { (tab, students) ->
                report.appendLine("=== ${tab.tab.gid} ${tab.tab.name}")
                students.forEach { student ->
                    val ok = truth != null && sheets.right(student, tabs, truth)
                    if (truth != null) total++
                    if (ok) right++
                    report.appendLine((if (truth != null && !ok) "  WRONG" else "") + "  row ${student.row + 1}: " + describe(student))
                }
            }
        }
        if (sheets.truth.isNotEmpty()) report.appendLine("correct $right of $total")
        File(root, "decisions.txt").writeText(report.toString())
    }

    private fun describe(student: RealSheets.Student): String = when (val search = student.search) {
        RowSearch.NotFound -> "not found"
        is RowSearch.Ambiguous -> "ambiguous in ${search.candidates.size} rows"
        is RowSearch.Found -> student.decision?.let { decision ->
            val total = decision.total
            val others = decision.candidates.drop(1).take(3).joinToString { "${it.tab.gid}/${columnName(it.column)} '${it.value}'" }
            "-> ${total.tab.gid}/${columnName(total.column)} '${total.value}' [${total.headerPath}] " +
                "${decision.confidence} ${decision.reason}; also $others"
        } ?: "-> no total"
    }
}
