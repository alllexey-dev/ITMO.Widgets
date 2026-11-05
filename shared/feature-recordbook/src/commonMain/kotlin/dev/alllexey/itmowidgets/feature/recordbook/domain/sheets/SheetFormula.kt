package dev.alllexey.itmowidgets.feature.recordbook.domain.sheets

import dev.alllexey.itmowidgets.feature.recordbook.domain.formatRecordbookNumber
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

/** A column of the own row in the tab [gid], found again by its header path ([SheetColumnRef]). */
data class SheetCellRef(val gid: Long, val column: SheetColumnRef)

/**
 * How a total is made from the own row: [Direct] shows one cell as the sheet shows it; [Computed] does arithmetic over
 * cells of the own row and shows a number, or no value when an input is missing or not a number (never a guessed 0).
 */
sealed interface SheetFormula {
    /** Every cell the formula reads, in order. */
    val cells: List<SheetCellRef>

    data class Direct(val cell: SheetCellRef) : SheetFormula {
        override val cells: List<SheetCellRef> get() = listOf(cell)
    }

    data class Computed(val node: SheetNode, val blanks: BlankPolicy) : SheetFormula {
        override val cells: List<SheetCellRef> get() = node.cells()
    }

    companion object {
        /** The hand-made sum of [cells]; an empty cell counts as nothing, as in the sheet's own `SUM`. */
        fun sum(cells: List<SheetCellRef>): Computed =
            Computed(SheetNode.Apply(SheetOp.SUM, cells.map(SheetNode::Cell)), BlankPolicy.IGNORE_BLANK_INPUTS)
    }
}

/**
 * What an empty input means (the rule's `blank_policy`): [PRESERVE] makes the result unavailable, with
 * [IGNORE_BLANK_INPUTS] a sum skips it.
 */
enum class BlankPolicy { PRESERVE, IGNORE_BLANK_INPUTS }

/** [SUM], [MULTIPLY], [MIN] and [MAX] take one or more arguments, [SUBTRACT] and [DIVIDE] exactly two. */
enum class SheetOp { SUM, MULTIPLY, MIN, MAX, SUBTRACT, DIVIDE }

/** A calculation node of a [SheetFormula.Computed]: the closed set of Backend's sheet rules. */
sealed interface SheetNode {
    data class Cell(val ref: SheetCellRef) : SheetNode
    data class Number(val value: Double) : SheetNode
    data class Apply(val op: SheetOp, val args: List<SheetNode>) : SheetNode
    data class Round(val arg: SheetNode, val digits: Int) : SheetNode

    fun cells(): List<SheetCellRef> = when (this) {
        is Cell -> listOf(ref)
        is Number -> emptyList()
        is Apply -> args.flatMap { it.cells() }
        is Round -> arg.cells()
    }
}

/** What reading one cell of the own row gave: its text, or why it could not be read. */
sealed interface SheetCellRead {
    data class Text(val value: String) : SheetCellRead
    data class Missing(val status: SheetStatus) : SheetCellRead
}

/**
 * Evaluates a [SheetFormula] over the cells [read] gives, the way Backend's analyzer checked the rule
 * (`sheet-analyzer/evidence.py`, `model_ast`). Pure.
 */
object SheetFormulas {

    /**
     * The value of [formula]: a direct cell as the sheet shows it (null when empty), a computed one as a number in
     * the app's format, null when an input is not a number or missing. A cell that cannot be read at all (row or
     * column gone, the tab not downloaded) makes the whole reading fail with its status.
     */
    fun evaluate(formula: SheetFormula, read: (SheetCellRef) -> SheetCellRead): SheetReading = when (formula) {
        is SheetFormula.Direct -> when (val cell = read(formula.cell)) {
            is SheetCellRead.Missing -> SheetReading(null, cell.status)
            is SheetCellRead.Text -> SheetReading(cell.value.trim().ifEmpty { null }, SheetStatus.OK)
        }
        is SheetFormula.Computed -> when (val result = Evaluation(formula.blanks, read).of(formula.node)) {
            is Outcome.Value -> SheetReading(formatRecordbookNumber(result.number), SheetStatus.OK)
            Outcome.Blank, Outcome.NoValue -> SheetReading(null, SheetStatus.OK)
            is Outcome.Failed -> SheetReading(null, result.status)
        }
    }

    /** Excel's `ROUND`: halves away from zero, on the decimal the sheet shows rather than the binary double. */
    internal fun round(value: Double, digits: Int): Double {
        val scale = 10.0.pow(digits)
        val rounded = floor(abs(value) * scale + 0.5 + ROUNDING_SLACK) / scale
        return if (value < 0) -rounded else rounded
    }

    private const val ROUNDING_SLACK = 1e-9

    private sealed interface Outcome {
        data class Value(val number: Double) : Outcome
        /** An empty input under [BlankPolicy.IGNORE_BLANK_INPUTS]: a sum skips it. */
        data object Blank : Outcome
        /** A non-number, an error cell, a blank under [BlankPolicy.PRESERVE], a division by zero. */
        data object NoValue : Outcome
        data class Failed(val status: SheetStatus) : Outcome
    }

    private class Evaluation(val blanks: BlankPolicy, val read: (SheetCellRef) -> SheetCellRead) {
        fun of(node: SheetNode): Outcome = when (node) {
            is SheetNode.Number -> Outcome.Value(node.value)
            is SheetNode.Cell -> cell(node.ref)
            // The analyzer rounds an ignored blank as 0.
            is SheetNode.Round -> when (val arg = of(node.arg)) {
                is Outcome.Value -> Outcome.Value(round(arg.number, node.digits))
                Outcome.Blank -> Outcome.Value(0.0)
                else -> arg
            }
            is SheetNode.Apply -> apply(node.op, node.args.map(::of))
        }

        private fun cell(ref: SheetCellRef): Outcome {
            val text = when (val cell = read(ref)) {
                is SheetCellRead.Missing -> return Outcome.Failed(cell.status)
                is SheetCellRead.Text -> cell.value.trim()
            }
            return when {
                text.isEmpty() -> if (blanks == BlankPolicy.IGNORE_BLANK_INPUTS) Outcome.Blank else Outcome.NoValue
                else -> SheetTotals.number(text)?.let(Outcome::Value) ?: Outcome.NoValue
            }
        }

        private fun apply(op: SheetOp, args: List<Outcome>): Outcome {
            args.firstOrNull { it is Outcome.Failed }?.let { return it }
            if (args.isEmpty() || Outcome.NoValue in args) return Outcome.NoValue
            if (op == SheetOp.SUM) return Outcome.Value(args.filterIsInstance<Outcome.Value>().sumOf { it.number })
            val numbers = args.map { (it as? Outcome.Value)?.number ?: return Outcome.NoValue }
            return when (op) {
                SheetOp.SUM -> error("handled above")
                SheetOp.MULTIPLY -> Outcome.Value(numbers.reduce(Double::times))
                SheetOp.MIN -> Outcome.Value(numbers.min())
                SheetOp.MAX -> Outcome.Value(numbers.max())
                SheetOp.SUBTRACT -> numbers.takeIf { it.size == 2 }?.let { Outcome.Value(it[0] - it[1]) } ?: Outcome.NoValue
                SheetOp.DIVIDE -> numbers.takeIf { it.size == 2 && it[1] != 0.0 }?.let { Outcome.Value(it[0] / it[1]) }
                    ?: Outcome.NoValue
            }
        }
    }
}
