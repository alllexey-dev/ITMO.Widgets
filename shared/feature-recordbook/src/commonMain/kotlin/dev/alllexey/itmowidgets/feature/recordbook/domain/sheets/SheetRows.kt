package dev.alllexey.itmowidgets.feature.recordbook.domain.sheets

/**
 * The own row in one tab: [key] is the ISU digits or the normalised name, found in [keyColumn]; [label] is the name
 * written in the row (the key when the row has no name). [occurrence] tells rows holding the same key in [keyColumn]
 * apart by their order from the top (0 for the topmost): a name a tab lists twice.
 */
data class SheetRowMatch(
    val tab: SheetTab,
    val row: Int,
    val keyColumn: Int,
    val key: String,
    val kind: KeyKind,
    val label: String,
    val occurrence: Int = 0,
) {
    override fun toString(): String =
        "SheetRowMatch(tab=${tab.gid}, row=$row, keyColumn=$keyColumn, kind=$kind, occurrence=$occurrence)"
}

/** Where the own row is: one key in at most one row per tab, several candidates, or nowhere. */
sealed interface RowSearch {
    data class Found(val matches: List<SheetRowMatch>) : RowSearch
    data class Ambiguous(val candidates: List<SheetRowMatch>) : RowSearch
    data object NotFound : RowSearch
}

/**
 * Finds the own row by the ISU, then by the forms of the name, in every tab. An ISU is one student, so its rows in one
 * tab are one row listed again lower (an attendance block under the scores): the topmost one is the own row. A name
 * in several rows of a tab may be namesakes, so those rows are a choice told apart by [SheetRowMatch.occurrence]. Pure.
 */
object SheetRows {

    fun find(workbook: SheetWorkbook, identity: OwnIdentity): RowSearch {
        val byIsu = identity.isu?.let { isu ->
            matches(workbook) { cell -> if (SheetIdentity.holds(cell, isu.toString(), KeyKind.ISU)) KeyKind.ISU else null }
        }.orEmpty().distinctBy { it.tab to it.key }
        val found = byIsu.ifEmpty {
            val variants = SheetIdentity.nameVariants(identity)
            if (variants.isEmpty()) emptyList()
            else matches(workbook) { cell -> if (SheetText.normalize(cell) in variants) KeyKind.NAME else null }
        }
        return when {
            found.isEmpty() -> RowSearch.NotFound
            found.groupBy { it.tab }.values.all { it.size == 1 } && found.map { it.key }.distinct().size == 1 ->
                RowSearch.Found(found)
            else -> RowSearch.Ambiguous(found)
        }
    }

    /**
     * The row of [key] again: in [keyColumn] first, then in any column. Of several rows holding it, an ISU is the
     * topmost and a name the [occurrence]-th from the top; null when there is no such row.
     */
    fun locate(tab: SheetTabGrid, key: String, kind: KeyKind, keyColumn: Int, occurrence: Int = 0): SheetRowMatch? {
        val grid = tab.grid
        val index = if (kind == KeyKind.ISU) 0 else occurrence
        val inColumn = (0 until grid.height).filter { SheetIdentity.holds(grid.cell(it, keyColumn), key, kind) }
        if (inColumn.isNotEmpty()) return inColumn.getOrNull(index)?.let { match(tab, it, keyColumn, key, kind) }
        val anywhere = (0 until grid.height).mapNotNull { row ->
            (0 until grid.width).firstOrNull { SheetIdentity.holds(grid.cell(row, it), key, kind) }?.let { row to it }
        }
        val (row, column) = anywhere.getOrNull(index) ?: return null
        return match(tab, row, column, key, kind)
    }

    /**
     * Every student row of [tab] for a manual choice: the rows whose name column holds a name; the key is the ISU of
     * the row when the tab has an ISU column, else the name. A student whose ISU the tab lists again lower is listed
     * once, at the topmost row.
     */
    fun options(tab: SheetTabGrid): List<SheetRowMatch> {
        val grid = tab.grid
        val nameColumn = densest(grid, KeyKind.NAME) ?: return emptyList()
        val isuColumn = densest(grid, KeyKind.ISU)
        return (0 until grid.height).mapNotNull { row ->
            val name = grid.cell(row, nameColumn)
            if (SheetIdentity.personKind(name) != KeyKind.NAME) return@mapNotNull null
            val isu = isuColumn?.let { grid.cell(row, it) }?.takeIf { SheetIdentity.personKind(it) == KeyKind.ISU }
            if (isu != null) {
                match(tab, row, isuColumn, SheetIdentity.keyOf(isu, KeyKind.ISU), KeyKind.ISU, name)
            } else {
                match(tab, row, nameColumn, SheetIdentity.keyOf(name, KeyKind.NAME), KeyKind.NAME, name)
            }
        }.filter { it.occurrence == 0 || it.kind == KeyKind.NAME }
    }

    private fun matches(workbook: SheetWorkbook, kindOf: (String) -> KeyKind?): List<SheetRowMatch> =
        workbook.tabs.flatMap { tab ->
            (0 until tab.grid.height).mapNotNull { row ->
                (0 until tab.grid.width).firstNotNullOfOrNull { column ->
                    val cell = tab.grid.cell(row, column)
                    kindOf(cell)?.let { kind -> match(tab, row, column, SheetIdentity.keyOf(cell, kind), kind) }
                }
            }
        }

    private fun match(
        tab: SheetTabGrid,
        row: Int,
        keyColumn: Int,
        key: String,
        kind: KeyKind,
        name: String? = if (kind == KeyKind.NAME) tab.grid.cell(row, keyColumn) else tab.grid.rows[row].firstOrNull {
            SheetIdentity.personKind(it) == KeyKind.NAME
        },
    ): SheetRowMatch {
        val occurrence = (0 until row).count { SheetIdentity.holds(tab.grid.cell(it, keyColumn), key, kind) }
        return SheetRowMatch(tab.tab, row, keyColumn, key, kind, name?.let(::label) ?: key, occurrence)
    }

    private fun densest(grid: SheetGrid, kind: KeyKind): Int? = (0 until grid.width)
        .map { column -> column to (0 until grid.height).count { SheetIdentity.personKind(grid.cell(it, column)) == kind } }
        .filter { it.second > 0 }
        .maxByOrNull { it.second }
        ?.first

    private fun label(cell: String): String = SheetText.compact(cell)
}
