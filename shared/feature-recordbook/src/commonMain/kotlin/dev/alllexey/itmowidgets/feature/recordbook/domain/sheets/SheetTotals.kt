package dev.alllexey.itmowidgets.feature.recordbook.domain.sheets

/** A cell of the own row: its tab, column, header path and the value as the sheet shows it. */
data class SheetCell(val tab: SheetTab, val column: Int, val headerPath: String, val value: String) {
    override fun toString(): String = "SheetCell(tab=${tab.gid}, column=$column)"
}

/** How sure an automatic total is; [LOW] asks the viewer to check it («Проверьте итог»). */
enum class TotalConfidence { HIGH, LOW }

/** Why the automatic total was chosen ([TotalConfidence.HIGH]) or should be checked ([TotalConfidence.LOW]). */
enum class TotalReason(val confidence: TotalConfidence) {
    /** In most rows the value is the sum of columns to its left. */
    SUM_MATCHES(TotalConfidence.HIGH),
    /** The strongest total header of the row. */
    HEADER(TotalConfidence.HIGH),
    /** The own row is in several tabs whose totals differ. */
    SEVERAL_TABS(TotalConfidence.LOW),
    /** The column is empty or zero for everyone while the own row has points. */
    EMPTY_FOR_ALL(TotalConfidence.LOW),
    /** No «Итог», «Сумма», «∑» or «Total» header: a score, BARS or grade column. */
    WEAK_HEADER(TotalConfidence.LOW),
    /** The column holds text, percentages or errors rather than points. */
    NOT_POINTS(TotalConfidence.LOW),
}

/** The automatic total: [total], why it was chosen, and every candidate best first, [total] among them. */
data class SheetTotalDecision(val total: SheetCell, val reason: TotalReason, val candidates: List<SheetCell>) {
    val confidence: TotalConfidence get() = reason.confidence
}

/** The cells of the own row and which of them is the total. Pure. */
object SheetTotals {

    /**
     * Header words of a total, the strongest group first; a multi-word entry matches consecutive words and the last
     * one by its start, so «итог» also finds «Итоговый балл». Groups 0-3 name a total, 4-5 a score or the BARS
     * points, 6-7 a grade ([TIERS]).
     */
    val KEYWORDS: List<List<String>> = listOf(
        listOf("итог", "всего"),
        listOf("σ", "∑"),
        listOf("сумма", "сум", "sum"),
        listOf("total"),
        listOf("score"),
        listOf("bars credits", "барс"),
        listOf("оценка"),
        listOf("зачет"),
    )
    private val TIERS = intArrayOf(0, 0, 0, 0, 1, 1, 2, 2)

    /** Words of a tab name that make it an old copy or a template rather than the current list. */
    private val STALE_TABS = listOf("copy", "копия", "template", "шаблон", "архив", "archive")
    private val ERROR = Regex("""#[\p{Lu}/0]+[!?]?""")
    private val NUMBER = Regex("""-?\d+(\.\d+)?""")
    private const val MIN_EVIDENCE = 3
    private const val MAX_POINTS = 100.0

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
     * The total of the own [rows] in [tabs] (the link's tab first), or null without a total header. Candidates are
     * the cells under a [KEYWORDS] header, ordered by: a column of points (not text, percentages or errors); a tab
     * that is not a copy or a template; not a column empty for every student while another total of the tab has
     * points (a semester not begun); the header tier; a filled cell; the tab where the own row has more values; a header segment
     * that is the keyword itself; the keyword group; an earlier tab; a column further right.
     */
    fun decide(tabs: List<SheetTabGrid>, rows: List<SheetRowMatch>): SheetTotalDecision? {
        val ranked = tabs.flatMapIndexed { order, tab ->
            rows.filter { it.tab == tab.tab }.flatMap { candidates(tab, it, order) }
        }.sortedWith(ORDER)
        val best = ranked.firstOrNull() ?: return null
        return SheetTotalDecision(best.cell, reason(best, ranked), ranked.map { it.cell })
    }

    private fun reason(best: Candidate, ranked: List<Candidate>): TotalReason = when {
        best.kind != ColumnKind.POINTS || ERROR.matches(best.cell.value) -> TotalReason.NOT_POINTS
        best.tier > 0 -> TotalReason.WEAK_HEADER
        !best.live && best.rowHasPoints -> TotalReason.EMPTY_FOR_ALL
        ranked.any { it.cell.tab != best.cell.tab && it.sameStanding(best) && it.differs(best) } -> TotalReason.SEVERAL_TABS
        sumMatches(best) -> TotalReason.SUM_MATCHES
        else -> TotalReason.HEADER
    }

    private enum class ColumnKind { POINTS, OTHER, BROKEN }

    private class Candidate(
        val cell: SheetCell,
        val tab: SheetTabGrid,
        val match: SheetRowMatch,
        val students: List<Int>,
        val subtotals: Set<Int>,
        val rank: Rank,
        val kind: ColumnKind,
        val live: Boolean,
        val stale: Boolean,
        val richness: Int,
        val rowHasPoints: Boolean,
        val order: Int,
    ) {
        val tier: Int get() = TIERS[rank.group]
        var demoted: Boolean = false

        fun sameStanding(other: Candidate): Boolean =
            kind == other.kind && stale == other.stale && demoted == other.demoted && tier == other.tier

        fun differs(other: Candidate): Boolean =
            cell.value.isNotBlank() && SheetText.normalize(cell.value) != SheetText.normalize(other.cell.value)
    }

    private val ORDER = compareBy<Candidate> { it.kind.ordinal }
        .thenBy { it.stale }
        .thenBy { it.demoted }
        .thenBy { it.tier }
        .thenByDescending { it.cell.value.isNotBlank() }
        .thenByDescending { it.richness }
        .thenByDescending { it.rank.exact }
        .thenBy { it.rank.group }
        .thenBy { it.order }
        .thenByDescending { it.cell.column }

    private fun candidates(tab: SheetTabGrid, match: SheetRowMatch, order: Int): List<Candidate> {
        val grid = tab.grid
        val first = SheetHeaders.firstDataRow(grid, match.row, match.keyColumn, match.kind)
        val students = (first until grid.height).filter {
            SheetIdentity.personKind(grid.cell(it, match.keyColumn)) == match.kind
        }
        val own = cells(tab, match)
        val ranks = own.associateWith { rank(it.headerPath) }
        val subtotals = own.filter { ranks[it]?.let { rank -> TIERS[rank.group] == 0 } == true }.map { it.column }.toSet()
        val stale = SheetText.tokens(tab.tab.name).any { word -> STALE_TABS.any(word::startsWith) }
        val withPoints = own.filter { isPoints(it.value) && holdsPoints(grid, students, it.column) }
        val found = own.mapNotNull { cell ->
            val rank = ranks[cell] ?: return@mapNotNull null
            val values = students.map { grid.cell(it, cell.column).trim() }
            Candidate(
                cell = cell,
                tab = tab,
                match = match,
                students = students,
                subtotals = subtotals - cell.column,
                rank = rank,
                kind = kindOf(values),
                live = values.any { number(it)?.let { value -> value != 0.0 } == true },
                stale = stale,
                richness = own.count { it.value.isNotBlank() && number(it.value) != 0.0 },
                rowHasPoints = withPoints.any { it.column != cell.column },
                order = order,
            )
        }
        val pointsLive = found.any { it.live && it.tier == 0 && it.kind == ColumnKind.POINTS }
        found.forEach { it.demoted = !it.live && pointsLive }
        return found
    }

    private fun isPoints(text: String): Boolean = number(text)?.let { it != 0.0 && kotlin.math.abs(it) <= MAX_POINTS } == true

    /**
     * Whether a column holds points rather than a variant, an ISU or a date: its non-zero numbers repeat across the
     * students (a score of 7 is common, a variant number is not) and none is above [MAX_POINTS].
     */
    private fun holdsPoints(grid: SheetGrid, students: List<Int>, column: Int): Boolean {
        val values = students.mapNotNull { number(grid.cell(it, column)) }.filter { it != 0.0 }
        return values.isNotEmpty() && values.all { kotlin.math.abs(it) <= MAX_POINTS } &&
            (values.size < MIN_EVIDENCE || values.distinct().size * 2 <= values.size)
    }

    /** Text such as a grade or «TRUE», percentages and formula errors are not points; empty cells are. */
    private fun kindOf(values: List<String>): ColumnKind {
        val filled = values.filter { it.isNotEmpty() }
        val numbers = filled.count { number(it) != null }
        val errors = filled.count { ERROR.matches(it) }
        return when {
            errors > 0 && errors >= numbers -> ColumnKind.BROKEN
            filled.size - numbers > numbers -> ColumnKind.OTHER
            else -> ColumnKind.POINTS
        }
    }

    /** A number as a sheet shows it («12», «7,5», «-3»), without percentages; null for anything else. */
    internal fun number(text: String): Double? {
        val compact = text.filterNot { it.isWhitespace() || it == '\u00A0' }.replace(',', '.').replace('\u2212', '-')
        return if (NUMBER.matches(compact)) compact.toDoubleOrNull() else null
    }

    /**
     * Whether in most student rows with a non-zero total ([MIN_EVIDENCE] at least, four in five) the total is the sum
     * of one run of at least two columns on one side of it: all of them, or only those not under a total header
     * themselves (a «Итог» of «Лаб. 3» inside the run is then not counted twice).
     */
    private fun sumMatches(candidate: Candidate): Boolean {
        val grid = candidate.tab.grid
        val column = candidate.cell.column
        val rows = candidate.students.mapNotNull { row -> number(grid.cell(row, column))?.takeIf { it != 0.0 }?.let { row to it } }
        if (rows.size < MIN_EVIDENCE) return false
        return listOf(emptySet(), candidate.subtotals).any { skipped ->
            val prefix = rows.map { (row, _) ->
                DoubleArray(grid.width + 1).also { sums ->
                    for (j in 0 until grid.width) {
                        val counted = j != candidate.match.keyColumn && j != column && j !in skipped
                        sums[j + 1] = sums[j] + if (counted) number(grid.cell(row, j)) ?: 0.0 else 0.0
                    }
                }
            }
            val runs = (0 until column - 1).flatMap { start -> (start + 2..column).map { start to it } } +
                (column + 1 until grid.width - 1).flatMap { start -> (start + 2..grid.width).map { start to it } }
            runs.any { (start, end) ->
                val hits = rows.indices.count { kotlin.math.abs(prefix[it][end] - prefix[it][start] - rows[it].second) < 0.005 }
                hits >= MIN_EVIDENCE && hits * 5 >= rows.size * 4
            }
        }
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
