package dev.alllexey.itmowidgets.feature.recordbook.domain.sheets

import dev.alllexey.itmowidgets.core.resources.GoogleSheetUrl

/** A tab of a Google Sheet: its [gid] and the name shown on the tab. */
data class SheetTab(val gid: Long, val name: String)

/** The cells of one tab as text, rectangular: shorter rows are padded with empty cells. */
class SheetGrid(rows: List<List<String>>) {
    val width: Int = rows.maxOfOrNull { it.size } ?: 0
    val rows: List<List<String>> = rows.map { row -> if (row.size == width) row else row + List(width - row.size) { "" } }
    val height: Int get() = rows.size

    /** The text of a cell; outside the grid it is empty. */
    fun cell(row: Int, column: Int): String = rows.getOrNull(row)?.getOrNull(column) ?: ""

    override fun equals(other: Any?): Boolean = other is SheetGrid && other.rows == rows
    override fun hashCode(): Int = rows.hashCode()
    override fun toString(): String = "SheetGrid(${height}x$width)"
}

data class SheetTabGrid(val tab: SheetTab, val grid: SheetGrid)

/** Every downloaded tab of one sheet; lives only in memory. */
data class SheetWorkbook(val url: GoogleSheetUrl, val tabs: List<SheetTabGrid>) {
    override fun toString(): String = "SheetWorkbook(${tabs.size} tabs)"
}

/** The spreadsheet name of a column: `A`, `B`, …, `Z`, `AA`. */
fun columnName(index: Int): String {
    require(index >= 0)
    val name = StringBuilder()
    var rest = index + 1
    while (rest > 0) {
        val digit = (rest - 1) % 26
        name.append('A' + digit)
        rest = (rest - 1) / 26
    }
    return name.reverse().toString()
}
