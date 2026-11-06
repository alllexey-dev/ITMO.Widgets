package dev.alllexey.itmowidgets.feature.recordbook.domain.sheets

import org.junit.Assert.assertEquals
import org.junit.Test

/** [SheetFormulas.evaluate] the way Backend's analyzer evaluates a route (`evidence.py`, `model_ast`). */
class SheetFormulaTest {
    /** The own row: column index to the text the sheet shows; a column not here is not in the sheet. */
    private val row = mapOf(0 to "10", 1 to "7,5", 2 to "", 3 to "  ", 4 to "н/я", 5 to "#REF!", 6 to "0", 7 to "-2", 8 to "2.675")

    private fun read(ref: SheetCellRef): SheetCellRead =
        row[ref.column.index]?.let(SheetCellRead::Text) ?: SheetCellRead.Missing(SheetStatus.COLUMN_NOT_FOUND)

    private fun cell(column: Int) = SheetNode.Cell(SheetCellRef(1, SheetColumnRef("", column)))
    private fun number(value: Double) = SheetNode.Number(value)
    private fun op(op: SheetOp, vararg args: SheetNode) = SheetNode.Apply(op, args.toList())

    private fun value(node: SheetNode, blanks: BlankPolicy = BlankPolicy.IGNORE_BLANK_INPUTS): SheetReading =
        SheetFormulas.evaluate(SheetFormula.Computed(node, blanks), ::read)

    private fun ok(value: String?) = SheetReading(value, SheetStatus.OK)

    @Test fun `a sum skips blank inputs when the rule ignores them`() {
        assertEquals(ok("17,5"), value(op(SheetOp.SUM, cell(0), cell(1), cell(2), cell(3))))
    }

    @Test fun `a sum with a blank input has no value when the rule preserves blanks`() {
        assertEquals(ok(null), value(op(SheetOp.SUM, cell(0), cell(2)), BlankPolicy.PRESERVE))
        assertEquals(ok("17,5"), value(op(SheetOp.SUM, cell(0), cell(1)), BlankPolicy.PRESERVE))
    }

    @Test fun `a sum of blanks only is 0, as the analyzer adds nothing`() {
        assertEquals(ok("0"), value(op(SheetOp.SUM, cell(2), cell(3))))
    }

    @Test fun `a lone blank cell has no value`() {
        assertEquals(ok(null), value(cell(2)))
    }

    @Test fun `text or an error cell gives no value, never a guessed 0`() {
        assertEquals(ok(null), value(op(SheetOp.SUM, cell(0), cell(4))))
        assertEquals(ok(null), value(op(SheetOp.SUM, cell(0), cell(5))))
    }

    @Test fun `a cell that is not there fails the reading with its status`() {
        assertEquals(SheetReading(null, SheetStatus.COLUMN_NOT_FOUND), value(op(SheetOp.SUM, cell(0), cell(40))))
        val noRow = SheetFormulas.evaluate(SheetFormula.Computed(cell(0), BlankPolicy.PRESERVE)) {
            SheetCellRead.Missing(SheetStatus.ROW_NOT_FOUND)
        }
        assertEquals(SheetReading(null, SheetStatus.ROW_NOT_FOUND), noRow)
    }

    @Test fun `nested nodes compute in order`() {
        val weighted = op(SheetOp.SUM, op(SheetOp.MULTIPLY, cell(0), number(0.5)), op(SheetOp.SUBTRACT, cell(1), cell(7)))

        assertEquals(ok("14,5"), value(weighted))
        assertEquals(ok("7,5"), value(op(SheetOp.MIN, cell(0), cell(1), number(20.0))))
        assertEquals(ok("20"), value(op(SheetOp.MAX, cell(0), number(20.0))))
        assertEquals(ok("150"), value(op(SheetOp.MULTIPLY, cell(0), cell(1), number(2.0))))
    }

    @Test fun `a binary operation with a blank operand has no value`() {
        assertEquals(ok(null), value(op(SheetOp.SUBTRACT, cell(0), cell(2))))
        assertEquals(ok(null), value(op(SheetOp.MULTIPLY, cell(0), cell(2))))
    }

    @Test fun `division by zero has no value`() {
        assertEquals(ok(null), value(op(SheetOp.DIVIDE, cell(0), cell(6))))
        assertEquals(ok("4"), value(op(SheetOp.DIVIDE, cell(0), number(2.5))))
    }

    @Test fun `round halves away from zero on the decimal the sheet shows`() {
        assertEquals(2.68, SheetFormulas.round(2.675, 2), 1e-12)
        assertEquals(-2.5, SheetFormulas.round(-2.45, 1), 1e-12)
        assertEquals(ok("2,7"), value(SheetNode.Round(cell(8), 1)))
        assertEquals(ok("0"), value(SheetNode.Round(cell(2), 0)))
    }

    @Test fun `a direct cell shows as the sheet shows it`() {
        val direct = { column: Int -> SheetFormulas.evaluate(SheetFormula.Direct(SheetCellRef(1, SheetColumnRef("", column))), ::read) }

        assertEquals(ok("7,5"), direct(1))
        assertEquals(ok("н/я"), direct(4))
        assertEquals(ok(null), direct(3))
        assertEquals(SheetReading(null, SheetStatus.COLUMN_NOT_FOUND), direct(40))
    }
}
