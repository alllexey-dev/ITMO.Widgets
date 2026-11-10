package dev.alllexey.itmowidgets.feature.recordbook.domain.sheets

import dev.alllexey.itmowidgets.core.resources.GoogleSheetUrl
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkEventKind
import kotlin.time.Instant

/** The total column: its header path, found again by text; [index] is the fallback. */
data class SheetColumnRef(val headerPath: String, val index: Int)

/**
 * How the total was chosen: found by [SheetTotals.decide] ([AUTO]), found but worth a look ([AUTO_CHECK], the
 * page says «Проверьте итог»), picked or summed by the viewer ([MANUAL]), or Backend's rule the viewer accepted ([AI]).
 */
enum class TotalChoice { AUTO, AUTO_CHECK, MANUAL, AI }

/** The last reading of a connection; every status but [OK] keeps the last value. */
enum class SheetStatus { OK, NETWORK, CLOSED, ROW_NOT_FOUND, COLUMN_NOT_FOUND, TOO_LARGE }

/**
 * The own total of [scope] from a public Google Sheet: the own row is found by [rowKey] in [tabGid] (and in every other
 * tab [formula] reads; [rowOccurrence] picks one of the rows a repeated name holds), [formula] makes the total. [value] is the last value read (shown), [baseline] the last
 * non-empty one (compared); [tracked] is false until a read after the marks switch was turned back on. An accepted AI
 * rule adds the teacher's [official] total (read as [officialValue], shown beside, never compared) and the rule's
 * [summary]. [linkId] is the subject link the sheet came from when it shares the sheet with a flow or everybody.
 */
data class SheetScore(
    val scope: ResourceScope,
    val url: String,
    val tabGid: Long,
    val tabName: String,
    val rowKey: String,
    val keyColumn: Int,
    val keyKind: KeyKind,
    val formula: SheetFormula,
    val value: String?,
    val baseline: String?,
    val tracked: Boolean,
    val status: SheetStatus,
    val updatedAt: Instant?,
    val connectedAt: Instant,
    val choice: TotalChoice = TotalChoice.AUTO,
    val official: SheetFormula? = null,
    val officialValue: String? = null,
    val summary: String? = null,
    val linkId: String? = null,
    val rowOccurrence: Int = 0,
) {
    /** Every tab a reading downloads: the row's tab first. */
    val tabs: List<Long> get() = (listOf(tabGid) + formula.cells.map { it.gid } + official?.cells.orEmpty().map { it.gid }).distinct()

    /** The connected tab in the browser. */
    val tabUrl: String get() = GoogleSheetUrl.parse(url)!!.tabUrl(tabGid)

    override fun toString(): String =
        "SheetScore(scope=${scope.key}, tab=$tabGid, kind=$keyKind, choice=$choice, status=$status, tracked=$tracked)"
}

/**
 * What one download of the connected tabs shows; [value] is null for an empty cell, no computed value or a failed
 * read. [official] is the teacher's total of an AI rule, null when there is none or it cannot be read.
 */
data class SheetReading(val value: String?, val status: SheetStatus, val official: String? = null) {
    override fun toString(): String = "SheetReading(hasValue=${value != null}, status=$status)"
}

/** The connection after a reading and the mark event it makes, if any. */
data class SheetUpdate(val score: SheetScore, val change: MarkEventKind?)

/** Reading the connected total from a tab and comparing it with the last one. Pure. */
object SheetScoreRules {
    private val SPACES = Regex("""\s+""")

    /**
     * The connection's [SheetScore.formula] (and [SheetScore.official]) over the downloaded [tabs]: the own row by its
     * key in each tab, each column by its header path. A tab [tabs] lacks reads as [missing].
     */
    fun read(tabs: Map<Long, SheetGrid>, score: SheetScore, missing: SheetStatus = SheetStatus.COLUMN_NOT_FOUND): SheetReading {
        val cells = OwnCells(tabs, score.rowKey, score.keyKind, score.keyColumn, score.rowOccurrence, missing)
        if (cells.row(score.tabGid) == null) return SheetReading(null, cells.status(score.tabGid))
        val main = SheetFormulas.evaluate(score.formula, cells::read)
        val official = score.official?.let { SheetFormulas.evaluate(it, cells::read).value }
        return main.copy(official = official)
    }

    /** The own row's cells in [tabs], found again by [key]; rows and header paths are worked out once per tab. */
    class OwnCells(
        private val tabs: Map<Long, SheetGrid>,
        private val key: String,
        private val kind: KeyKind,
        private val keyColumn: Int,
        private val occurrence: Int = 0,
        private val missing: SheetStatus = SheetStatus.COLUMN_NOT_FOUND,
    ) {
        private val located = mutableMapOf<Long, Pair<SheetRowMatch, List<String>>?>()

        fun row(gid: Long): SheetRowMatch? = located(gid)?.first

        fun status(gid: Long): SheetStatus = if (gid in tabs) SheetStatus.ROW_NOT_FOUND else missing

        fun read(ref: SheetCellRef): SheetCellRead {
            val grid = tabs[ref.gid] ?: return SheetCellRead.Missing(missing)
            val (match, paths) = located(ref.gid) ?: return SheetCellRead.Missing(SheetStatus.ROW_NOT_FOUND)
            val column = SheetTotals.find(paths, ref.column, grid.width)
                ?: return SheetCellRead.Missing(SheetStatus.COLUMN_NOT_FOUND)
            return SheetCellRead.Text(grid.cell(match.row, column))
        }

        private fun located(gid: Long): Pair<SheetRowMatch, List<String>>? = located.getOrPut(gid) {
            val grid = tabs[gid] ?: return@getOrPut null
            val match = SheetRows.locate(SheetTabGrid(SheetTab(gid, ""), grid), key, kind, keyColumn, occurrence)
                ?: return@getOrPut null
            match to SheetHeaders.paths(grid, SheetHeaders.firstDataRow(grid, match.row, match.keyColumn, match.kind), match.keyColumn)
        }
    }

    /**
     * Stores [reading] taken [at]. Any successful read moves the baseline and tracks the connection; only a
     * background read ([notify]) of a tracked connection with a new non-empty value is news. A failed read changes
     * only the status.
     */
    fun apply(score: SheetScore, reading: SheetReading, at: Instant, notify: Boolean): SheetUpdate {
        if (reading.status != SheetStatus.OK) return SheetUpdate(score.copy(status = reading.status), null)
        val value = reading.value?.trim()?.ifEmpty { null }
        val change = when {
            !notify || !score.tracked || value == null || same(value, score.baseline) -> null
            score.baseline == null -> MarkEventKind.MARK_ADDED
            else -> MarkEventKind.MARK_CHANGED
        }
        val updated = score.copy(
            value = value,
            officialValue = reading.official?.trim()?.ifEmpty { null },
            baseline = value ?: score.baseline,
            tracked = true,
            status = SheetStatus.OK,
            updatedAt = at,
        )
        return SheetUpdate(updated, change)
    }

    private fun same(value: String, baseline: String?): Boolean =
        baseline != null && value.replace(SPACES, " ") == baseline.trim().replace(SPACES, " ")
}
