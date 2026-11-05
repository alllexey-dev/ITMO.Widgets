package dev.alllexey.itmowidgets.feature.recordbook.domain.sheets

/** How full a column is among the students of its tab: [filled] of [students] have a value, [max] is its top number. */
data class SheetColumnStats(val filled: Int, val students: Int, val max: Double?)

/** Column summaries for the manual pick, computed from the downloaded tab and never stored. Pure. */
object SheetColumns {

    /** The stats of every column of [tab], by index, over the student rows around the own [row]. */
    fun stats(tab: SheetTabGrid, row: SheetRowMatch): List<SheetColumnStats> {
        val grid = tab.grid
        val first = SheetHeaders.firstDataRow(grid, row.row, row.keyColumn, row.kind)
        val students = (first until grid.height).filter { SheetIdentity.personKind(grid.cell(it, row.keyColumn)) == row.kind }
        return (0 until grid.width).map { column ->
            val values = students.map { grid.cell(it, column).trim() }.filter { it.isNotEmpty() }
            SheetColumnStats(values.size, students.size, values.mapNotNull(SheetTotals::number).maxOrNull())
        }
    }
}
