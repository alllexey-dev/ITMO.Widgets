package dev.alllexey.itmowidgets.feature.recordbook.domain.sheets

/**
 * The own row in one tab: [key] is the ISU digits or the normalised name, found in [keyColumn]; [label] is the name
 * written in the row (the key when the row has no name).
 */
data class SheetRowMatch(
    val tab: SheetTab,
    val row: Int,
    val keyColumn: Int,
    val key: String,
    val kind: KeyKind,
    val label: String,
) {
    override fun toString(): String = "SheetRowMatch(tab=${tab.gid}, row=$row, keyColumn=$keyColumn, kind=$kind)"
}

/** Where the own row is: one key in at most one row per tab, several candidates, or nowhere. */
sealed interface RowSearch {
    data class Found(val matches: List<SheetRowMatch>) : RowSearch
    data class Ambiguous(val candidates: List<SheetRowMatch>) : RowSearch
    data object NotFound : RowSearch
}

/** Finds the own row by the ISU, then by the forms of the name, in every tab. Pure. */
object SheetRows {

    fun find(workbook: SheetWorkbook, identity: OwnIdentity): RowSearch {
        val byIsu = identity.isu?.let { isu ->
            matches(workbook) { cell -> if (SheetIdentity.holds(cell, isu.toString(), KeyKind.ISU)) KeyKind.ISU else null }
        }.orEmpty()
        val found = byIsu.ifEmpty {
            val variants = identity.name?.let(SheetIdentity::nameVariants).orEmpty()
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

    /** The row of [key] again: in [keyColumn] first, then in any column; null unless exactly one row has it. */
    fun locate(tab: SheetTabGrid, key: String, kind: KeyKind, keyColumn: Int): SheetRowMatch? {
        val grid = tab.grid
        val inColumn = (0 until grid.height).filter { SheetIdentity.holds(grid.cell(it, keyColumn), key, kind) }
        if (inColumn.size == 1) return match(tab, inColumn.single(), keyColumn, key, kind)
        if (inColumn.isNotEmpty()) return null
        val anywhere = (0 until grid.height).mapNotNull { row ->
            (0 until grid.width).firstOrNull { SheetIdentity.holds(grid.cell(row, it), key, kind) }?.let { row to it }
        }
        val (row, column) = anywhere.singleOrNull() ?: return null
        return match(tab, row, column, key, kind)
    }

    /**
     * Every student row of [tab] for a manual choice: the rows whose name column holds a name; the key is the ISU of
     * the row when the tab has an ISU column, else the name.
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
                SheetRowMatch(tab.tab, row, isuColumn, SheetIdentity.keyOf(isu, KeyKind.ISU), KeyKind.ISU, label(name))
            } else {
                SheetRowMatch(tab.tab, row, nameColumn, SheetIdentity.keyOf(name, KeyKind.NAME), KeyKind.NAME, label(name))
            }
        }
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

    private fun match(tab: SheetTabGrid, row: Int, keyColumn: Int, key: String, kind: KeyKind): SheetRowMatch {
        val cell = tab.grid.cell(row, keyColumn)
        val name = if (kind == KeyKind.NAME) cell else tab.grid.rows[row].firstOrNull {
            SheetIdentity.personKind(it) == KeyKind.NAME
        }
        return SheetRowMatch(tab.tab, row, keyColumn, key, kind, name?.let(::label) ?: key)
    }

    private fun densest(grid: SheetGrid, kind: KeyKind): Int? = (0 until grid.width)
        .map { column -> column to (0 until grid.height).count { SheetIdentity.personKind(grid.cell(it, column)) == kind } }
        .filter { it.second > 0 }
        .maxByOrNull { it.second }
        ?.first

    private fun label(cell: String): String = SheetText.compact(cell)
}
