package dev.alllexey.itmowidgets.feature.recordbook.domain.sheets

/** A cell of the own row: its tab, column, header path and the value as the sheet shows it. */
data class SheetCell(val tab: SheetTab, val column: Int, val headerPath: String, val value: String) {
    override fun toString(): String = "SheetCell(tab=${tab.gid}, column=$column)"
}

/** The cells of the own row and which of them is the total. Pure. */
object SheetTotals {

    /**
     * Header words of a total, the strongest group first; a multi-word entry matches consecutive words and the last
     * one by its start, so «итог» also finds «Итоговый балл».
     */
    val KEYWORDS: List<List<String>> = listOf(
        listOf("итог"),
        listOf("σ", "∑"),
        listOf("сумма", "сум", "sum"),
        listOf("total"),
        listOf("score"),
        listOf("bars credits", "барс"),
        listOf("оценка"),
        listOf("зачет"),
    )

    /** Every column of the own row but its key and the cells naming the viewer, with header paths. */
    fun cells(tab: SheetTabGrid, match: SheetRowMatch): List<SheetCell> {
        val grid = tab.grid
        val first = SheetHeaders.firstDataRow(grid, match.row, match.keyColumn, match.kind)
        val paths = SheetHeaders.paths(grid, first, match.keyColumn)
        val label = SheetText.normalize(match.label)
        return (0 until grid.width).mapNotNull { column ->
            if (column == match.keyColumn) return@mapNotNull null
            val cell = grid.cell(match.row, column)
            if (SheetIdentity.holds(cell, match.key, match.kind)) return@mapNotNull null
            if (label.isNotEmpty() && SheetText.normalize(cell) == label) return@mapNotNull null
            SheetCell(tab.tab, column, paths[column], cell.trim())
        }
    }

    /**
     * The total among [cells] (in tab order, the link's tab first): the strongest keyword group of the header path;
     * then a path segment equal to the keyword, a non-empty value, an earlier tab, a column further right.
     */
    fun detect(cells: List<SheetCell>): SheetCell? {
        val tabOrder = cells.map { it.tab }.distinct()
        return cells.mapNotNull { cell -> rank(cell.headerPath)?.let { cell to it } }
            .sortedWith(
                compareBy<Pair<SheetCell, Rank>> { it.second.group }
                    .thenByDescending { it.second.exact }
                    .thenByDescending { it.first.value.isNotBlank() }
                    .thenBy { tabOrder.indexOf(it.first.tab) }
                    .thenByDescending { it.first.column }
            )
            .firstOrNull()?.first
    }

    /**
     * The column of [ref] in a tab whose header [paths] may have moved: the same non-empty path (the nearest to the
     * old index when several), else the old index while the tab is that wide; null when the column is gone.
     */
    fun find(paths: List<String>, ref: SheetColumnRef, width: Int): Int? {
        val path = SheetText.normalize(ref.headerPath)
        if (path.isNotEmpty()) {
            val same = paths.indices.filter { SheetText.normalize(paths[it]) == path }
            same.minWithOrNull(compareBy<Int> { kotlin.math.abs(it - ref.index) }.thenBy { it })?.let { return it }
        }
        return ref.index.takeIf { it in 0 until width }
    }

    private class Rank(val group: Int, val exact: Boolean)

    private fun rank(path: String): Rank? {
        val words = SheetText.tokens(path)
        val segments = path.split(SheetHeaders.SEPARATOR).map(SheetText::normalize)
        KEYWORDS.forEachIndexed { group, keywords ->
            val hits = keywords.filter { keyword -> contains(words, keyword.split(' ')) }
            if (hits.isNotEmpty()) return Rank(group, segments.any { segment -> hits.any { isWhole(segment, it) } })
        }
        return null
    }

    /** A segment that is the keyword itself, or one word starting with a one-word keyword («ИТОГО» for «итог»). */
    private fun isWhole(segment: String, keyword: String): Boolean =
        segment == keyword || (' ' !in keyword && ' ' !in segment && segment.startsWith(keyword))

    private fun contains(words: List<String>, keyword: List<String>): Boolean =
        words.windowed(keyword.size).any { window ->
            window.dropLast(1) == keyword.dropLast(1) && window.last().startsWith(keyword.last())
        }
}
