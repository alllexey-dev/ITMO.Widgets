package dev.alllexey.itmowidgets.feature.recordbook.domain.sheets

/**
 * Backend's rule for a sheet a subject link shares with a flow or everybody (its `sheet-rules` contract): which tab
 * and column to read, or which cells of the own row to compute. It carries no student data; the device applies it
 * to the tabs it downloads ([SheetRuleRoute.formula]).
 */
data class SheetRule(val summary: String, val routes: List<SheetRuleRoute>) {
    /**
     * The route for the tab of the own [rows] in [book]: a workbook with a tab per group has one route per tab. The
     * accumulated points first, else any route of that tab; null when no route reads a tab holding the own row.
     */
    fun mainFor(book: SheetWorkbook, rows: List<SheetRowMatch>): SheetRuleRoute? {
        val own = rows.map { it.tab }.toSet()
        val onOwnTab = routes.filter { it.tabIn(book) in own }
        return onOwnTab.firstOrNull { it.purpose == SheetRulePurpose.CURRENT } ?: onOwnTab.firstOrNull()
    }

    /** The teacher's official total on the same tab as [main], if the rule has one. */
    fun officialFor(main: SheetRuleRoute, book: SheetWorkbook): SheetRuleRoute? =
        routes.firstOrNull { it.purpose == SheetRulePurpose.OFFICIAL && it != main && it.tabIn(book) == main.tabIn(book) }
}

/** [CURRENT] is `current_accumulated` (the points so far), [OFFICIAL] `official_total` (may be 0 before admission). */
enum class SheetRulePurpose { CURRENT, OFFICIAL }

/** A tab the rule names; [gid] is null when the analyzer did not know it, then the name finds it. */
data class SheetRuleTab(val name: String, val gid: Long?)

/** One route: a [direct] read of its single cell node, or a computation; [tab] holds the own row. */
data class SheetRuleRoute(
    val purpose: SheetRulePurpose,
    val tab: SheetRuleTab,
    val direct: Boolean,
    val node: SheetRuleNode,
    val blanks: BlankPolicy,
    val explanation: String,
    val warnings: List<String>,
) {
    /**
     * This route over [book] with the own [rows]: every tab found by gid or name, every column given the header path it
     * has there, so a later reading finds it again when columns move. Null when a tab, the own row in it or a column
     * is not there.
     */
    fun formula(book: SheetWorkbook, rows: List<SheetRowMatch>): SheetFormula? {
        val node = node.resolve(book, rows) ?: return null
        return when {
            !direct -> SheetFormula.Computed(node, blanks)
            node is SheetNode.Cell -> SheetFormula.Direct(node.ref)
            else -> null
        }
    }

    /** The tab of the own row this route reads in [book]. */
    fun tabIn(book: SheetWorkbook): SheetTab? = tab.find(book)?.tab
}

/** A calculation node with columns as indexes and tabs as the rule names them. */
sealed interface SheetRuleNode {
    data class Cell(val tab: SheetRuleTab, val column: Int) : SheetRuleNode
    data class Number(val value: Double) : SheetRuleNode
    data class Apply(val op: SheetOp, val args: List<SheetRuleNode>) : SheetRuleNode
    data class Round(val arg: SheetRuleNode, val digits: Int) : SheetRuleNode
}

/** Where a rule stands for one link; every failure leaves the manual pick. */
sealed interface SheetRuleResult {
    /** Queued or running on Backend: ask again in a few seconds. */
    data object Pending : SheetRuleResult
    data class Ready(val rule: SheetRule) : SheetRuleResult
    /** The analysis found no route it could check. */
    data object Insufficient : SheetRuleResult
    /** The analysis failed: the sheet is closed, too large, no longer shared, or the model failed. */
    data object Failed : SheetRuleResult
    /** Backend does not analyse this link (not shared, not a sheet, the feature off) or the services are off. */
    data object Unavailable : SheetRuleResult
    data object RateLimited : SheetRuleResult
    data object Offline : SheetRuleResult
}

/** Backend's sheet rules; every call needs the services opt-in, the demo session answers a fixture. */
interface SheetRulesRepository {
    /** Whether `Разобрать с ИИ` may be offered at all: the services opt-in, or the demo session. */
    suspend fun available(): Boolean

    /** Asks Backend to analyse the sheet of [linkId] (or answers the stored state). */
    suspend fun request(linkId: String): SheetRuleResult

    /** The stored state of [linkId]'s sheet; polled while [SheetRuleResult.Pending]. */
    suspend fun state(linkId: String): SheetRuleResult
}

private fun SheetRuleTab.find(book: SheetWorkbook): SheetTabGrid? =
    gid?.let { gid -> book.tabs.firstOrNull { it.tab.gid == gid } } ?: book.tabs.firstOrNull { it.tab.name == name }

private fun SheetRuleNode.resolve(book: SheetWorkbook, rows: List<SheetRowMatch>): SheetNode? = when (this) {
    is SheetRuleNode.Number -> SheetNode.Number(value)
    is SheetRuleNode.Round -> arg.resolve(book, rows)?.let { SheetNode.Round(it, digits) }
    is SheetRuleNode.Apply -> args.map { it.resolve(book, rows) ?: return null }.let { SheetNode.Apply(op, it) }
    is SheetRuleNode.Cell -> {
        val grid = tab.find(book)
        val row = grid?.let { found -> rows.firstOrNull { it.tab == found.tab } }
        if (grid == null || row == null || column >= grid.grid.width) {
            null
        } else {
            val first = SheetHeaders.firstDataRow(grid.grid, row.row, row.keyColumn, row.kind)
            val path = SheetHeaders.paths(grid.grid, first, row.keyColumn)[column]
            SheetNode.Cell(SheetCellRef(grid.tab.gid, SheetColumnRef(path, column)))
        }
    }
}
