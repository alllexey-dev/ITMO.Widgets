package dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.BlankPolicy
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetCellRef
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetColumnRef
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetFormula
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetNode
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetOp
import dev.alllexey.itmowidgets.shared.feature.recordbook.RecordbookStrings
import org.junit.Assert.assertEquals
import org.junit.Test

class SheetFormulaTextTest {
    private fun ref(path: String, column: Int = 0) = SheetCellRef(1, SheetColumnRef(path, column))
    private fun cell(path: String, column: Int = 0) = SheetNode.Cell(ref(path, column))
    private fun text(vararg parts: String) = parts.map(UiText::Dynamic)

    @Test fun `a sum names its columns by short title and keeps the ends of a long one`() {
        val sum = SheetFormula.sum((1..8).map { ref("ДЗ · ДЗ $it · 10", it) })

        val words = SheetFormulaText.describe(sum)

        val expected = UiText.Joined(text("ДЗ 1", "ДЗ 2", "ДЗ 3", "…", "ДЗ 7", "ДЗ 8"), " + ")
        assertEquals(UiText.Res(RecordbookStrings.sheet_formula_sum, listOf(expected)), words)
    }

    @Test fun `twin short titles keep their whole path and a column without one its letter`() {
        val sum = SheetFormula.sum(listOf(ref("Лаб 1 · Баллы", 2), ref("Лаб 2 · Баллы", 3), ref("", 4)))

        val words = SheetFormulaText.describe(sum) as UiText.Res

        val parts = (words.arguments.single() as UiText.Joined).parts
        assertEquals(UiText.Dynamic("Лаб 1 · Баллы"), parts[0])
        assertEquals(UiText.Res(RecordbookStrings.sheet_scores_column, listOf("E")), parts[2])
    }

    @Test fun `other operations use their signs and group a nested sum`() {
        val node = SheetNode.Apply(
            SheetOp.MULTIPLY,
            listOf(SheetNode.Apply(SheetOp.SUM, listOf(cell("А"), cell("Б", 1))), SheetNode.Number(0.5)),
        )

        val words = SheetFormulaText.describe(SheetFormula.Computed(node, BlankPolicy.PRESERVE))

        val grouped = UiText.Joined(listOf(UiText.Dynamic("("), UiText.Joined(text("А", "Б"), " + "), UiText.Dynamic(")")), "")
        assertEquals(UiText.Joined(listOf(grouped, UiText.Dynamic("0,5")), " × "), words)
    }
}
