package dev.alllexey.itmowidgets.feature.recordbook.ui.sheets.preview

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.KeyKind
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetCell
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetCellRef
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetColumnRef
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetColumnStats
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetFormula
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetHeaders
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRowMatch
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTab
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTotalDecision
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTotals
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.TotalChoice
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.TotalReason
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetAiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetColumnOption
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetFormulaText
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetScoresUiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.TotalOption
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.TotalPage
import dev.alllexey.itmowidgets.shared.feature.recordbook.RecordbookStrings

/**
 * Synthetic sheets of the scores sheet's previews and host tests: no real sheet, person or ISU. The first states show
 * two rows of the own name and the connected tab's cells; the others add a long roster, several tabs and many cells, and
 * the total's step sure, unsure, hand-picked, missing, with the AI, and its column and sum pages.
 */
object SheetScoresPreviewSamples {
    const val SUBJECT = "Математический анализ (продвинутый уровень)"
    const val OWN_NAME = "Тестов Тест Тестович"
    const val LONG_NAME = "Константинопольская-Тестова Александра Владиславовна"
    const val TOTAL_HEADER = "ИТОГО баллов"

    val group = SheetTab(22, "P3110")
    val longTab = SheetTab(33, "Баллы за весь семестр по всем видам работ (поток лекций 2026, группы P3110–P3115)")
    val englishTab = SheetTab(44, "BARS (Fall semester 2026)")
    val untitledTab = SheetTab(55, "")

    /** Two rows carry the own name in one tab. */
    val pickRow = SheetScoresUiState.PickRow(listOf(1, 2).map { row(group, it, OWN_NAME) })

    /** More people than the search threshold, a long name first, all in a tab with a long name. */
    val roster: List<SheetRowMatch> = listOf(
        LONG_NAME,
        "Тестова Анна Сергеевна",
        "Тёмкин Пётр Алексеевич",
        "Образцов Иван Петрович",
        "Примерова Мария Олеговна",
        "Пробный Олег Игоревич",
        "Макетова Елена Викторовна",
        "Шаблонов Артём Денисович",
        "Демо Дарья Андреевна",
        OWN_NAME,
    ).mapIndexed { index, name -> row(longTab, index + 1, name) }

    val pickTabRow = SheetScoresUiState.PickTabRow(longTab, roster)

    /** The tabs with students when the own row was not found; one without a name. */
    val pickTab = SheetScoresUiState.PickTab(listOf(group, longTab, untitledTab))

    /** The own row of the connected tab: the total (connected), a module total, a test, the grade and four labs. */
    val ownRow: List<SheetCell> = listOf(
        "ЛР1" to "10",
        "ЛР2" to "9",
        "ЛР3" to "10",
        "ЛР4" to "8",
        "Тест к видеолекциям" to "8",
        "ЛР · Итог" to "37",
        TOTAL_HEADER to "66,3",
        "Оценка" to "5A",
    ).mapIndexed { index, (header, value) -> SheetCell(group, index + 2, header, value) }

    val total = ownRow.single { it.headerPath == TOTAL_HEADER }
    private val labs = ownRow.single { it.headerPath == "ЛР · Итог" }
    private val test = ownRow.single { it.headerPath == "Тест к видеолекциям" }

    /** The connected automatic total, sure of itself: on top, the module total as the other variant; nothing to save. */
    val pickTotal = SheetScoresUiState.PickTotal(
        cells = ownRow,
        selection = TotalOption.Column(total),
        current = TotalOption.Column(total),
        decision = SheetTotalDecision(total, TotalReason.SUM_MATCHES, listOf(total, labs, ownRow.last())),
        choice = TotalChoice.AUTO,
        columns = columns(ownRow),
    )

    /** The AI offer of a shared sheet beside the automatic total. */
    val pickTotalAiOffer = pickTotal.copy(ai = SheetAiState.Offer)

    /** Backend's analysis running. */
    val pickTotalAiRunning = pickTotal.copy(ai = SheetAiState.Running)

    /** The AI rule applied to the own row on top: the labs and the test added up, the teacher's total beside. */
    val pickTotalAi = pickTotal.copy(
        selection = TotalOption.Ai,
        ai = SheetAiState.Proposal(
            formula = SheetFormulaText.describe(
                SheetFormula.sum(ownRow.take(5).map { SheetCellRef(it.tab.gid, SheetColumnRef(it.headerPath, it.column)) }),
            ),
            value = "45",
            official = "0",
            hasOfficial = true,
            summary = "Баллы — сумма лабораторных и теста; итог преподавателя ставится после допуска",
            warnings = listOf("Итог может быть 0 до допуска"),
        ),
    )

    /** The AI analysis found nothing; the automatic total stays on top. */
    val pickTotalAiFailed = pickTotal.copy(ai = SheetAiState.Failed(UiText.Res(RecordbookStrings.sheet_ai_insufficient)))

    /** An automatic total to check: the row is in another tab too, with another total there. */
    val pickTotalCheck = pickTotal.copy(
        decision = SheetTotalDecision(total, TotalReason.SEVERAL_TABS, listOf(total, SheetCell(englishTab, 14, TOTAL_HEADER, "52"))),
        choice = TotalChoice.AUTO_CHECK,
    )

    /** A total the viewer picked earlier: on top, the automatic one among the variants. */
    val pickTotalManual = pickTotal.copy(selection = TotalOption.Column(test), current = TotalOption.Column(test), choice = TotalChoice.MANUAL)

    /** Two tabs of fifteen cells: a long header path, a column without a header, values as the sheet has them. */
    val manyCells: List<SheetCell> = listOf(group, englishTab).flatMap { tab ->
        val values = listOf("66,3", "100%", "5A") + (4..15).map { "$it,0" }
        val ordered = if (tab == group) values else values.reversed()
        ordered.mapIndexed { index, value -> SheetCell(tab, index + 1, header(index), value) }
    }

    /** No total header while connecting: nothing on top, the ways to find the total. */
    val pickTotalNone = SheetScoresUiState.PickTotal(manyCells, selection = null, columns = columns(manyCells, empty = 4))

    /** `Выбрать столбец`: the first of two tabs, a long header path, a column letter, an empty column folded. */
    val pickColumns = pickTotalNone.copy(page = TotalPage.COLUMNS)

    /** `Сложить столбцы`: three labs checked and their own sum; the grade and the percentage are not offered. */
    val pickSum = manyCells.filter { it.tab == group }.drop(5).take(3).let { added ->
        pickTotalNone.copy(
            page = TotalPage.SUM,
            summed = added,
            sum = "21",
            sumText = SheetFormulaText.describe(SheetFormula.sum(added.map { SheetCellRef(it.tab.gid, SheetColumnRef(it.headerPath, it.column)) })),
        )
    }

    /** Every column filled by 23 of 30 students up to its own value, but [empty] (an index) filled by nobody. */
    private fun columns(cells: List<SheetCell>, empty: Int? = null): List<SheetColumnOption> = cells.map { cell ->
        val blank = cells.indexOf(cell) == empty
        SheetColumnOption(cell, SheetColumnStats(filled = if (blank) 0 else 23, students = 30, max = if (blank) null else SheetTotals.number(cell.value)))
    }

    private fun header(index: Int): String = when (index) {
        0 -> TOTAL_HEADER
        1 -> listOf("Тесты к видеолекциям", "Дополнительное задание по разделу «Ряды и интегралы с параметром»")
            .joinToString(SheetHeaders.SEPARATOR)
        2 -> "Оценка"
        // A column whose header is empty is named by its letter.
        3 -> ""
        else -> "ЛР${index - 2}"
    }

    private fun row(tab: SheetTab, row: Int, name: String) =
        SheetRowMatch(tab, row, 0, name.lowercase(), KeyKind.NAME, name)
}
