package dev.alllexey.itmowidgets.feature.recordbook.domain.sheets

import dev.alllexey.itmowidgets.core.resources.GoogleSheetUrl
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkEventKind
import java.time.Instant

/** The total column: its header path, found again by text; [index] is the fallback. */
data class SheetColumnRef(val headerPath: String, val index: Int)

/** The last reading of a connection; every status but [OK] keeps the last value. */
enum class SheetStatus { OK, NETWORK, CLOSED, ROW_NOT_FOUND, COLUMN_NOT_FOUND, TOO_LARGE }

/**
 * The own total of [scope] from one tab of a public Google Sheet. [value] is the last value read (shown), [baseline]
 * the last non-empty one (compared); [tracked] is false until a read after the marks switch was turned back on.
 */
data class SheetScore(
    val scope: ResourceScope,
    val url: String,
    val tabGid: Long,
    val tabName: String,
    val rowKey: String,
    val keyColumn: Int,
    val keyKind: KeyKind,
    val column: SheetColumnRef,
    val value: String?,
    val baseline: String?,
    val tracked: Boolean,
    val status: SheetStatus,
    val updatedAt: Instant?,
    val connectedAt: Instant,
) {
    /** The connected tab in the browser. */
    val tabUrl: String get() = GoogleSheetUrl.parse(url)!!.tabUrl(tabGid)

    override fun toString(): String =
        "SheetScore(scope=${scope.key}, tab=$tabGid, kind=$keyKind, status=$status, tracked=$tracked)"
}

/** What one download of the connected tab shows; [value] is null for an empty cell or a failed read. */
data class SheetReading(val value: String?, val status: SheetStatus) {
    override fun toString(): String = "SheetReading(hasValue=${value != null}, status=$status)"
}

/** The connection after a reading and the mark event it makes, if any. */
data class SheetUpdate(val score: SheetScore, val change: MarkEventKind?)

/** Reading the connected total from a tab and comparing it with the last one. Pure. */
object SheetScoreRules {
    private val SPACES = Regex("""\s+""")

    /** The own row by its key, the column by its header path, the value as the sheet shows it. */
    fun read(grid: SheetGrid, score: SheetScore): SheetReading {
        val tab = SheetTabGrid(SheetTab(score.tabGid, score.tabName), grid)
        val match = SheetRows.locate(tab, score.rowKey, score.keyKind, score.keyColumn)
            ?: return SheetReading(null, SheetStatus.ROW_NOT_FOUND)
        val first = SheetHeaders.firstDataRow(grid, match.row, match.keyColumn, match.kind)
        val paths = SheetHeaders.paths(grid, first, match.keyColumn)
        val column = SheetTotals.find(paths, score.column, grid.width)
            ?: return SheetReading(null, SheetStatus.COLUMN_NOT_FOUND)
        return SheetReading(grid.cell(match.row, column).trim().ifEmpty { null }, SheetStatus.OK)
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
