package dev.alllexey.itmowidgets.feature.recordbook.domain.sheets

/**
 * The header above the students of a tab and the path of every column in it. A group title spans the empty
 * cells to its right (a merged cell in CSV is its left cell and empty ones) up to the next title of the same row
 * or of a row above; the last header row does not span. A row whose only text is at or left of the key column is
 * the tab's title (a teacher, a course) and is not part of any path.
 */
object SheetHeaders {
    const val MAX_HEADER_ROWS = 6
    const val SEPARATOR = " · "
    private const val MAX_ODD_KEYS = 2

    /**
     * The topmost row of the students around [row]: upward from it while the key column holds the same kind of
     * key, skipping empty key cells and up to [MAX_ODD_KEYS] other texts in a row (a note, an unusual name); a
     * column title such as «ФИО» ends the students at once.
     */
    fun firstDataRow(grid: SheetGrid, row: Int, keyColumn: Int, kind: KeyKind): Int {
        var first = row
        var odd = 0
        for (above in row - 1 downTo 0) {
            val cell = grid.cell(above, keyColumn)
            when {
                cell.isBlank() -> continue
                SheetIdentity.personKind(cell) == kind -> {
                    first = above
                    odd = 0
                }
                SheetIdentity.isPeopleTitle(cell) || ++odd > MAX_ODD_KEYS -> break
            }
        }
        return first
    }

    /** One path per column of [grid], empty when the column has no header. */
    fun paths(grid: SheetGrid, firstDataRow: Int, keyColumn: Int): List<String> {
        val rows = (maxOf(0, firstDataRow - MAX_HEADER_ROWS) until firstDataRow)
            .filterNot { isTitle(grid, it, keyColumn) }
        val spans = Array(rows.size) { Array(grid.width) { "" } }
        rows.forEachIndexed { level, row ->
            var current = ""
            for (column in 0 until grid.width) {
                val text = SheetText.compact(grid.cell(row, column))
                val groupStarts = (0 until level).any { upper -> SheetText.compact(grid.cell(rows[upper], column)).isNotEmpty() }
                current = when {
                    text.isNotEmpty() -> text
                    level == rows.lastIndex || groupStarts -> ""
                    else -> current
                }
                spans[level][column] = if (text.isNotEmpty()) text else current
            }
        }
        return (0 until grid.width).map { column ->
            val segments = mutableListOf<String>()
            spans.forEach { level ->
                val text = level[column]
                if (text.isNotEmpty() && segments.lastOrNull() != text) segments += text
            }
            segments.joinToString(SEPARATOR)
        }
    }

    private fun isTitle(grid: SheetGrid, row: Int, keyColumn: Int): Boolean {
        val filled = (0 until grid.width).filter { grid.cell(row, it).isNotBlank() }
        return filled.size == 1 && filled.single() <= keyColumn
    }
}
