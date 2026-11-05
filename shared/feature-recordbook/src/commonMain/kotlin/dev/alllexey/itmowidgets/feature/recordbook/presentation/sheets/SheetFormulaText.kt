package dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.recordbook.domain.formatRecordbookNumber
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetCellRef
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetFormula
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetHeaders
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetNode
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetOp
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTotals
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.columnName
import dev.alllexey.itmowidgets.shared.feature.recordbook.RecordbookStrings

/**
 * A [SheetFormula] in words from the header paths it keeps: a column by its short title (the whole path when two
 * cells share one), a sum as `Сумма: ДЗ 1 + ДЗ 2 + … + Экзамен` (a long one keeps its ends), the other operations
 * with their signs.
 */
object SheetFormulaText {
    private const val LONG_SUM = 6
    private const val HEAD = 3
    private const val TAIL = 2

    fun describe(formula: SheetFormula): UiText = when (formula) {
        is SheetFormula.Direct -> column(formula.cell, unique = true)
        is SheetFormula.Computed -> {
            val shorts = formula.cells.groupingBy { short(it) }.eachCount()
            val node = formula.node
            val body = Writer { shorts[short(it)] == 1 }.write(node, top = true)
            if (node is SheetNode.Apply && node.op == SheetOp.SUM) UiText.Res(RecordbookStrings.sheet_formula_sum, listOf(body))
            else body
        }
    }

    /** A column as the sheet names it: its header path, or `Столбец D` without one. */
    fun column(cell: SheetCellRef, unique: Boolean): UiText {
        val text = if (unique) short(cell) else cell.column.headerPath
        return text.takeIf { it.isNotBlank() }?.let(UiText::Dynamic)
            ?: UiText.Res(RecordbookStrings.sheet_scores_column, listOf(columnName(cell.column.index)))
    }

    /** The last header segment that is not a number (a max-points row writes «10» under «ДЗ 1»). */
    private fun short(cell: SheetCellRef): String =
        cell.column.headerPath.split(SheetHeaders.SEPARATOR)
            .lastOrNull { it.isNotBlank() && SheetTotals.number(it) == null } ?: cell.column.headerPath

    private class Writer(val unique: (SheetCellRef) -> Boolean) {
        fun write(node: SheetNode, top: Boolean = false): UiText = when (node) {
            is SheetNode.Cell -> column(node.ref, unique(node.ref))
            is SheetNode.Number -> UiText.Dynamic(formatRecordbookNumber(node.value))
            is SheetNode.Round -> write(node.arg, top)
            is SheetNode.Apply -> when (node.op) {
                SheetOp.SUM -> grouped(top, UiText.Joined(shortened(node.args.map { write(it) }), " + "))
                SheetOp.SUBTRACT -> grouped(top, UiText.Joined(node.args.map { operand(it) }, " − "))
                SheetOp.MULTIPLY -> UiText.Joined(node.args.map { operand(it) }, " × ")
                SheetOp.DIVIDE -> UiText.Joined(node.args.map { operand(it) }, " ÷ ")
                SheetOp.MIN -> UiText.Res(RecordbookStrings.sheet_formula_min, listOf(UiText.Joined(node.args.map { write(it, top = true) }, "; ")))
                SheetOp.MAX -> UiText.Res(RecordbookStrings.sheet_formula_max, listOf(UiText.Joined(node.args.map { write(it, top = true) }, "; ")))
            }
        }

        private fun operand(node: SheetNode): UiText = write(node, top = false)

        private fun grouped(top: Boolean, text: UiText): UiText =
            if (top) text else UiText.Joined(listOf(UiText.Dynamic("("), text, UiText.Dynamic(")")), "")

        private fun shortened(parts: List<UiText>): List<UiText> =
            if (parts.size <= LONG_SUM) parts else parts.take(HEAD) + UiText.Dynamic("…") + parts.takeLast(TAIL)
    }
}
