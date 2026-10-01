package dev.alllexey.itmowidgets.feature.recordbook.data.sheets

import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetGrid
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * The cells of a tab from its HTML view, as the CSV export would give them: `td` cells of the sheet table (`th`
 * row and column headers and the freeze bars are not cells); a merged cell keeps its text in the top left cell and
 * leaves the others empty.
 */
internal object SheetHtmlGrid {

    fun parse(html: String): SheetGrid {
        val document = Jsoup.parse(html)
        val table = document.selectFirst("table.waffle") ?: document.selectFirst("table") ?: return SheetGrid(emptyList())
        val rows = table.select("tbody > tr").ifEmpty { table.select("tr") }
        val grid = mutableListOf<MutableList<String?>>()
        var rowIndex = 0
        rows.forEach { tr ->
            val cells = tr.children().filter { it.tagName() == "td" && !it.isFreezeBar() }
            if (cells.isEmpty()) return@forEach
            val row = grid.rowAt(rowIndex)
            var column = 0
            cells.forEach { td ->
                while (column < row.size && row[column] != null) column++
                val columns = td.span("colspan")
                val rowsSpanned = td.span("rowspan")
                for (dy in 0 until rowsSpanned) {
                    val target = grid.rowAt(rowIndex + dy)
                    for (dx in 0 until columns) {
                        target.put(column + dx, if (dy == 0 && dx == 0) td.text() else "")
                    }
                }
                column += columns
            }
            rowIndex++
        }
        return SheetGrid(grid.take(rowIndex).map { row -> row.map { it ?: "" } })
    }

    private fun Element.isFreezeBar(): Boolean = classNames().any { it.startsWith("freezebar") }

    private fun Element.span(name: String): Int = attr(name).toIntOrNull()?.coerceIn(1, MAX_SPAN) ?: 1

    private fun MutableList<MutableList<String?>>.rowAt(index: Int): MutableList<String?> {
        while (size <= index) add(mutableListOf())
        return this[index]
    }

    private fun MutableList<String?>.put(column: Int, text: String) {
        while (size <= column) add(null)
        if (this[column] == null) this[column] = text
    }

    private const val MAX_SPAN = 1000
}
