package dev.alllexey.itmowidgets.feature.recordbook.domain.sheets

import dev.alllexey.itmowidgets.core.resources.GoogleSheetUrl
import java.io.File

/**
 * Sheets laid out as on disk: a folder per sheet with `tabs.tsv` (`<gid>\t<name>`, the link's tab first) and a
 * `<gid>.csv` per tab, and `truth.tsv` (`<sheet>\t<gid>\t<column>`) naming each tab's real total column. The anonymized
 * fixtures in `resources/sheets/real` and the raw downloads of [SheetRawSheetsTest] share it.
 */
class RealSheets(private val root: File) {
    /** The real total columns by sheet folder name. */
    val truth: Map<String, Set<Pair<Long, Int>>> = File(root, "truth.tsv").takeIf { it.isFile }?.readLines()
        ?.filter { it.isNotBlank() }?.map { it.split('\t') }
        ?.groupBy({ it[0] }, { it[1].toLong() to it[2].toInt() })?.mapValues { it.value.toSet() }.orEmpty()

    val sheets: List<String> get() = root.listFiles().orEmpty().filter { File(it, "tabs.tsv").isFile }.map { it.name }.sorted()

    fun tabs(sheet: String): List<SheetTabGrid> =
        File(root, "$sheet/tabs.tsv").readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
            val (gid, name) = line.split('\t', limit = 2)
            SheetTabGrid(SheetTab(gid.toLong(), name), CsvGrid.parse(File(root, "$sheet/$gid.csv").readText()))
        }

    /** One student of a sheet: where the row is, who it is, what the app decides. */
    class Student(val tab: SheetTabGrid, val row: Int, val identity: OwnIdentity, val search: RowSearch, val decision: SheetTotalDecision?)

    /** Every student of [tabs] in the tabs of [only] (all tabs when null), searched for in the whole sheet. */
    fun students(sheet: String, tabs: List<SheetTabGrid>, only: Set<Long>? = null): List<Student> {
        val book = SheetWorkbook(GoogleSheetUrl(sheet, null), tabs)
        return tabs.filter { only == null || it.tab.gid in only }.flatMap { tab ->
            roster(tab).map { (row, identity) ->
                val search = SheetRows.find(book, identity)
                Student(tab, row, identity, search, (search as? RowSearch.Found)?.let { SheetTotals.decide(tabs, it.matches) })
            }
        }
    }

    /**
     * Whether [student]'s total is in a real total column of [tabs] and, for a row in several tabs, the largest of
     * the real totals of the row (the tab the student moved to).
     */
    fun right(student: Student, tabs: List<SheetTabGrid>, truth: Set<Pair<Long, Int>>): Boolean {
        val chosen = student.decision?.total ?: return false
        if ((chosen.tab.gid to chosen.column) !in truth) return false
        val values = (student.search as RowSearch.Found).matches.mapNotNull { match ->
            val column = truth.firstOrNull { it.first == match.tab.gid }?.second ?: return@mapNotNull null
            tabs.first { it.tab == match.tab }.grid.cell(match.row, column).trim()
        }
        return chosen.value == values.maxByOrNull { SheetTotals.number(it) ?: Double.NEGATIVE_INFINITY }
    }

    /** The roster of [tab]: rows with an ISU in its densest ISU column, else a name in its densest name column. */
    private fun roster(tab: SheetTabGrid): List<Pair<Int, OwnIdentity>> {
        val grid = tab.grid
        fun count(column: Int, kind: KeyKind) = (0 until grid.height).count { SheetIdentity.personKind(grid.cell(it, column)) == kind }
        fun densest(kind: KeyKind) = (0 until grid.width).maxByOrNull { count(it, kind) }?.takeIf { count(it, kind) > 0 }
        val isuColumn = densest(KeyKind.ISU)
        val nameColumn = densest(KeyKind.NAME)
        return (0 until grid.height).mapNotNull { row ->
            val isu = isuColumn?.let { grid.cell(row, it).filterNot(Char::isWhitespace) }
                ?.takeIf { SheetIdentity.personKind(it) == KeyKind.ISU }?.toInt()
            val name = nameColumn?.let { grid.cell(row, it) }?.takeIf { SheetIdentity.personKind(it) == KeyKind.NAME }
            if (if (isuColumn != null) isu == null else name == null) return@mapNotNull null
            row to identity(isu, name)
        }
    }

    /** ITMO.ID as it would be: the structured parts from «Фамилия Имя Отчество», `name` as «Имя Фамилия». */
    private fun identity(isu: Int?, name: String?): OwnIdentity {
        val words = name?.trim()?.split(Regex("""\s+""")).orEmpty()
        if (words.size < 2) return OwnIdentity(isu, name)
        return OwnIdentity(
            isu = isu,
            name = "${words[1]} ${words[0]}",
            familyName = words[0],
            givenName = words[1],
            middleName = words.drop(2).joinToString(" ").ifEmpty { null },
        )
    }
}
