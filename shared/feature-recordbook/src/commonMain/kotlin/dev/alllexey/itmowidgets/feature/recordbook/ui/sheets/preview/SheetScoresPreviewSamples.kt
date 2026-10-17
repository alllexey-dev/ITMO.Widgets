package dev.alllexey.itmowidgets.feature.recordbook.ui.sheets.preview

import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.KeyKind
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetCell
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetHeaders
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRowMatch
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTab
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTotalDecision
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.TotalChoice
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.TotalReason
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetScoresUiState

/**
 * Synthetic sheets of the scores sheet's previews and host tests: no real sheet, person or ISU. The first states show
 * two rows of the own name and the connected tab's cells; the others add a long roster, several tabs and many cells, and
 * the total's review sure, unsure and hand-picked.
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

    /** The automatic total, sure of itself: its sum checks out; the module total and the test are the other chips. */
    val pickTotal = SheetScoresUiState.PickTotal(
        cells = ownRow,
        selected = total,
        decision = SheetTotalDecision(total, TotalReason.SUM_MATCHES, listOf(total, labs, ownRow.last())),
        choice = TotalChoice.AUTO,
    )

    /** An automatic total to check: the row is in another tab too. */
    val pickTotalCheck = pickTotal.copy(
        decision = SheetTotalDecision(total, TotalReason.SEVERAL_TABS, listOf(total, SheetCell(englishTab, 14, TOTAL_HEADER, "52"))),
        choice = TotalChoice.AUTO_CHECK,
    )

    /** A total the viewer picked: «Выбрано вручную» and «Вернуть авто». */
    val pickTotalManual = pickTotal.copy(selected = test, choice = TotalChoice.MANUAL)

    /** Two tabs of fifteen cells: a long header path, a column without a header, values as the sheet has them. */
    val manyCells: List<SheetCell> = listOf(group, englishTab).flatMap { tab ->
        val values = listOf("66,3", "100%", "5A") + (4..15).map { "$it,0" }
        val ordered = if (tab == group) values else values.reversed()
        ordered.mapIndexed { index, value -> SheetCell(tab, index + 1, header(index), value) }
    }

    /** No total header: every cell of the row at once, the grid of both tabs. */
    val pickManyTotals = SheetScoresUiState.PickTotal(manyCells, selected = null)

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
