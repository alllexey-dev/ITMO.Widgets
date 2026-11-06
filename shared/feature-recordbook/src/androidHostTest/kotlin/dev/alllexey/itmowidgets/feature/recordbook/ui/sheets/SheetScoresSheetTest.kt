package dev.alllexey.itmowidgets.feature.recordbook.ui.sheets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performScrollToIndex
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.TotalOption
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRowMatch
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetStatus
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTab
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetScoresUiState
import dev.alllexey.itmowidgets.feature.recordbook.ui.sheets.preview.SheetScoresPreviewSamples
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertNoTextOverflow
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Compose sheet at 320 dp and font scale 1.3 in at most the sheet's 867 dp: loading and every failure in one area
 * with the retry only for the network, the own row among several, the tab and its rows with the name search, the total
 * on top with its variants and ways, the column and sum pages, the AI offer, wait, result and failure. Its looks live in
 * the `SheetScoresSheet_*` goldens; closing on `Done`, Back and the save snackbar stay in the shell's sheet entry.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class SheetScoresSheetTest {

    private var retries = 0
    private val rows = mutableListOf<SheetRowMatch>()
    private val tabs = mutableListOf<SheetTab>()
    private val calls = mutableListOf<Any>()
    private val actions = SheetScoresActions(
        onRetry = { retries++ },
        onPickRow = { rows += it },
        onPickTab = { tabs += it },
        onSelect = { calls += it },
        onOpenColumns = { calls += "columns" },
        onOpenSum = { calls += "sum" },
        onPickColumn = { calls += it },
        onToggleSum = { calls += "toggle ${it.headerPath}" },
        onFinishSum = { calls += "finish" },
        onAnalyze = { calls += "analyze" },
        onSave = { calls += "save" },
        onBack = { calls += "back" },
    )

    @Test
    fun loadingAndEveryFailureShareOneAreaAndOnlyTheNetworkRetries() = runComposeUiTest {
        var state: SheetScoresUiState by mutableStateOf(SheetScoresUiState.Loading)
        setContent { Sheet(state) }
        val height = sheetHeight()

        val failures = listOf(
            SheetStatus.NETWORK to "Нет связи",
            SheetStatus.CLOSED to "Баллы читаются только из таблиц, открытых по ссылке",
            SheetStatus.ROW_NOT_FOUND to "Возможно, её удалили или переименовали",
            SheetStatus.COLUMN_NOT_FOUND to "Возможно, столбец или лист удалили",
            SheetStatus.TOO_LARGE to "Каждый её лист больше 5 МБ",
        )
        failures.forEach { (status, text) ->
            state = SheetScoresUiState.Failed(status)
            waitForIdle()
            onNodeWithText(text, substring = true).assertExists()
            val retry = onAllNodesWithTag(SheetScoresSheetTestTags.RETRY)
            retry.assertCountEquals(if (status == SheetStatus.NETWORK) 1 else 0)
            assertEquals(height, sheetHeight(), status.name)
            assertTouchTargets()
            assertNoTextOverflow()
        }

        state = SheetScoresUiState.Failed(SheetStatus.NETWORK)
        waitForIdle()
        onNodeWithText("Повторить").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun severalOwnRowsAskWhichAndShowTheirTab() = runComposeUiTest {
        setContent { Sheet(SheetScoresPreviewSamples.pickRow) }

        assertShown("Какая строка ваша?")
        assertShown("Ваше имя есть в нескольких строках")
        onAllNodesWithText(SheetScoresPreviewSamples.OWN_NAME, substring = true).assertCountEquals(2)
        onAllNodesWithText("P3110", useUnmergedTree = true).assertCountEquals(2)
        onAllNodesWithTag(SheetScoresSheetTestTags.SEARCH).assertCountEquals(0)
        onAllNodesWithText("Назад").assertCountEquals(0)
        assertTouchTargets()

        onAllNodesWithText(SheetScoresPreviewSamples.OWN_NAME, substring = true).onLast().performClick()
        assertEquals(listOf(SheetScoresPreviewSamples.pickRow.candidates[1]), rows)
    }

    @Test
    fun theTabsToLookInNameAnUntitledTabAndSendThePickedOne() = runComposeUiTest {
        setContent { Sheet(SheetScoresPreviewSamples.pickTab) }

        assertShown("На каком листе ваша группа?")
        assertShown("Не нашли вас по имени и ИСУ")
        scrollTo("Лист без названия")
        onNodeWithText("P3110").assertExists()
        assertNoTextOverflow()
        assertTouchTargets()

        scrollTo(SheetScoresPreviewSamples.longTab.name)
        onNodeWithText(SheetScoresPreviewSamples.longTab.name).performClick()
        assertEquals(listOf(SheetScoresPreviewSamples.longTab), tabs)
    }

    @Test
    fun aLongRosterIsSearchedByNameKeepsTheQueryAndGoesBack() = runComposeUiTest {
        var state: SheetScoresUiState by mutableStateOf(SheetScoresPreviewSamples.pickTabRow)
        setContent { Sheet(state) }

        assertShown("Какая строка ваша?")
        onNodeWithText(SheetScoresPreviewSamples.LONG_NAME).assertExists()
        // The tab is known: no tab under every name.
        onAllNodesWithText(SheetScoresPreviewSamples.longTab.name, useUnmergedTree = true).assertCountEquals(0)
        assertNoTextOverflow()

        // Case and the letter yo do not matter.
        onNode(hasSetTextAction()).performTextInput("ТЕМКИН")
        waitForIdle()
        onNodeWithText("Тёмкин Пётр Алексеевич").assertExists()
        onAllNodesWithText(SheetScoresPreviewSamples.LONG_NAME).assertCountEquals(0)

        // The query survives a reading in between.
        state = SheetScoresUiState.Loading
        waitForIdle()
        state = SheetScoresPreviewSamples.pickTabRow
        waitForIdle()
        onAllNodesWithText(SheetScoresPreviewSamples.LONG_NAME).assertCountEquals(0)
        onNodeWithText("Тёмкин Пётр Алексеевич").performClick()
        assertEquals(listOf(SheetScoresPreviewSamples.roster[2]), rows)

        onNode(hasSetTextAction()).performTextInput("никто")
        waitForIdle()
        onNodeWithText("Никого не нашлось").assertExists()

        onNodeWithContentDescription("Очистить текст").performClick()
        waitForIdle()
        onNodeWithText(SheetScoresPreviewSamples.LONG_NAME).assertExists()
        assertTouchTargets()

        onNodeWithContentDescription("Назад").performClick()
        assertEquals(listOf<Any>("back"), calls)
    }

    @Test
    fun theTotalIsOnTopOnceWithItsSourceAndDoneClosesWhenNothingChanges() = runComposeUiTest {
        setContent { Sheet(SheetScoresPreviewSamples.pickTotal) }

        assertShown("Ваш итог")
        assertShown("Столбец «${SheetScoresPreviewSamples.TOTAL_HEADER}», лист «P3110»")
        // The chosen value is shown once; the grade is no variant.
        onAllNodesWithText("66,3", useUnmergedTree = true).assertCountEquals(1)
        onAllNodesWithText("5A", useUnmergedTree = true).assertCountEquals(0)
        scrollTo("Другой итог")
        scrollTo("ЛР › Итог")
        assertNoTextOverflow()
        assertTouchTargets()

        onNodeWithTag(SheetScoresSheetTestTags.SAVE).assertTextEquals("Готово").performClick()
        onNodeWithText("ЛР › Итог").performClick()
        val labs = SheetScoresPreviewSamples.ownRow.single { it.headerPath == "ЛР · Итог" }
        assertEquals(listOf<Any>("save", TotalOption.Column(labs)), calls)
    }

    @Test
    fun anUnsureTotalSaysWhatToCheckAndSaveConfirmsIt() = runComposeUiTest {
        setContent { Sheet(SheetScoresPreviewSamples.pickTotalCheck) }

        assertShown("Проверьте: на другом листе у вас другой итог")
        // The other tab's total is a variant named by its tab.
        scrollTo("Лист «${SheetScoresPreviewSamples.englishTab.name}»")
        onNodeWithTag(SheetScoresSheetTestTags.SAVE).assertTextEquals("Сохранить")
    }

    @Test
    fun aHandPickedTotalKeepsTheAutomaticOneAsAVariant() = runComposeUiTest {
        setContent { Sheet(SheetScoresPreviewSamples.pickTotalManual) }

        assertShown("Столбец «Тест к видеолекциям», лист «P3110»")
        scrollTo("Найден автоматически")
        onNodeWithText(SheetScoresPreviewSamples.TOTAL_HEADER, substring = true).performClick()
        assertEquals(listOf<Any>(TotalOption.Column(SheetScoresPreviewSamples.total)), calls)
    }

    @Test
    fun withoutATotalNothingIsOnTopAndTheWaysLeadOn() = runComposeUiTest {
        setContent { Sheet(SheetScoresPreviewSamples.pickTotalNone) }

        assertShown("Итог не найден")
        assertShown("В таблице нет столбца «Итог»")
        assertShown("Как найти итог")
        onAllNodesWithTag(SheetScoresSheetTestTags.SAVE).assertCountEquals(0)
        onNodeWithTag(SheetScoresSheetTestTags.LIST).performScrollToNode(hasTestTag(SheetScoresSheetTestTags.SUM))
        onNodeWithTag(SheetScoresSheetTestTags.COLUMNS).performClick()
        onNodeWithTag(SheetScoresSheetTestTags.SUM).performClick()
        assertEquals(listOf<Any>("columns", "sum"), calls)
        assertTouchTargets()
    }

    @Test
    fun aRowWithoutValuesSaysSo() = runComposeUiTest {
        setContent { Sheet(SheetScoresUiState.PickTotal(emptyList(), selection = null)) }

        assertShown("В вашей строке пока нет баллов")
    }

    @Test
    fun theColumnListGoesByTabAndAColumnIsPicked() = runComposeUiTest {
        setContent { Sheet(SheetScoresPreviewSamples.pickColumns) }

        assertShown("Выбрать столбец")
        onAllNodesWithText("Назад").assertCountEquals(0)
        // Values as the sheet has them; a column without a header is named by its letter; the group above a column.
        listOf("66,3", "100%", "5A", "Столбец E", "Тесты к видеолекциям", "Пустые у всех").forEach { text ->
            onNodeWithTag(SheetScoresSheetTestTags.LIST).performScrollToNode(hasText(text, substring = true))
            assertShown(text)
            assertNoTextOverflow()
        }
        assertTouchTargets()
        onAllNodesWithTag(SheetScoresSheetTestTags.SAVE).assertCountEquals(0)

        onNodeWithTag(SheetScoresSheetTestTags.LIST).performScrollToIndex(0)
        onNodeWithText(SheetScoresPreviewSamples.englishTab.name).performClick()
        waitForIdle()
        val last = SheetScoresPreviewSamples.manyCells.last()
        onNodeWithTag(SheetScoresSheetTestTags.LIST).performScrollToKey("column:${last.tab.gid}:${last.column}")
        onAllNodesWithText(last.headerPath).onLast().performClick()
        assertEquals(listOf<Any>(last), calls)

        onNodeWithContentDescription("Назад").performClick()
        assertEquals(listOf<Any>(last, "back"), calls)
    }

    @Test
    fun theEmptyColumnsFoldAndOpen() = runComposeUiTest {
        setContent { Sheet(SheetScoresPreviewSamples.pickColumns) }
        onNodeWithTag(SheetScoresSheetTestTags.LIST).performScrollToNode(hasTestTag(SheetScoresSheetTestTags.EMPTY_COLUMNS))
        onAllNodesWithText("ЛР2").assertCountEquals(0)

        onNodeWithTag(SheetScoresSheetTestTags.EMPTY_COLUMNS).performClick()
        waitForIdle()

        scrollTo("ЛР2")
    }

    @Test
    fun theSumOffersOnlyPointsShowsTheSumAndFinishes() = runComposeUiTest {
        setContent { Sheet(SheetScoresPreviewSamples.pickSum) }

        assertShown("21")
        assertShown("Сумма: ЛР3 + ЛР4 + ЛР5")
        // A percentage, a grade and a text are not added.
        listOf("100%", "5A").forEach { onAllNodesWithText(it, useUnmergedTree = true).assertCountEquals(0) }
        val first = SheetScoresPreviewSamples.pickSum.summed.first()
        onNodeWithTag(SheetScoresSheetTestTags.LIST).performScrollToKey("column:${first.tab.gid}:${first.column}")
        assertTrue(onAllNodes(isToggleable() and isOn()).fetchSemanticsNodes().isNotEmpty())
        assertTouchTargets()
        assertNoTextOverflow()
        onAllNodes(isToggleable() and isOn()).onFirst().performClick()
        onNodeWithTag(SheetScoresSheetTestTags.SAVE).assertTextEquals("Готово").assertIsEnabled().performClick()
        assertEquals(listOf<Any>("toggle ЛР3", "finish"), calls)
    }

    @Test
    fun aSumOfOneColumnCannotFinish() = runComposeUiTest {
        setContent { Sheet(SheetScoresPreviewSamples.pickSum.copy(summed = SheetScoresPreviewSamples.pickSum.summed.take(1), sum = "6,0", sumText = null)) }

        assertShown("Отметьте два столбца или больше")
        onNodeWithTag(SheetScoresSheetTestTags.SAVE).assertIsNotEnabled()
    }

    @Test
    fun aSharedSheetOffersAiItsResultGoesOnTopWithItsDetails() = runComposeUiTest {
        var state: SheetScoresUiState by mutableStateOf(SheetScoresPreviewSamples.pickTotalAiOffer)
        setContent { Sheet(state) }

        onNodeWithTag(SheetScoresSheetTestTags.LIST).performScrollToNode(hasTestTag(SheetScoresSheetTestTags.AI))
        assertShown("ИИ разберёт формулы таблицы")
        onNodeWithText("Посчитать с ИИ").performClick()
        state = SheetScoresPreviewSamples.pickTotalAiRunning
        waitForIdle()
        onNodeWithTag(SheetScoresSheetTestTags.LIST).performScrollToNode(hasTestTag(SheetScoresSheetTestTags.AI))
        assertShown("Разбираем таблицу, обычно до минуты")
        // A tap while it runs asks nothing again.
        onNodeWithTag(SheetScoresSheetTestTags.AI).performClick()

        state = SheetScoresPreviewSamples.pickTotalAi
        waitForIdle()
        onNodeWithTag(SheetScoresSheetTestTags.LIST).performScrollToIndex(0)
        listOf("45", "Сумма 5 столбцов", "Итог преподавателя: 0", "Посчитано по формуле ИИ — сверьте с таблицей").forEach { assertShown(it) }
        onAllNodesWithTag(SheetScoresSheetTestTags.AI).assertCountEquals(0)
        assertTrue(onAllNodesWithText("Итог может быть 0 до допуска").fetchSemanticsNodes().isEmpty())
        onNodeWithTag(SheetScoresSheetTestTags.LIST).performScrollToNode(hasTestTag(SheetScoresSheetTestTags.AI_DETAILS))
        onNodeWithTag(SheetScoresSheetTestTags.AI_DETAILS).performClick()
        scrollTo("Итог может быть 0 до допуска")
        assertNoTextOverflow()
        assertTouchTargets()
        onNodeWithTag(SheetScoresSheetTestTags.SAVE).assertTextEquals("Сохранить").performClick()
        assertEquals(listOf<Any>("analyze", "save"), calls)

        state = SheetScoresPreviewSamples.pickTotalAiFailed
        waitForIdle()
        onNodeWithTag(SheetScoresSheetTestTags.LIST).performScrollToNode(hasTestTag(SheetScoresSheetTestTags.AI))
        assertShown("ИИ не понял, как считаются баллы")
    }

    private fun ComposeUiTest.scrollTo(text: String) {
        onNodeWithTag(SheetScoresSheetTestTags.LIST).performScrollToNode(hasText(text, substring = true))
        assertShown(text)
    }

    private fun ComposeUiTest.assertShown(text: String) {
        assertTrue(onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), text)
    }

    private fun ComposeUiTest.sheetHeight(): Int =
        onNodeWithTag(SHEET).fetchSemanticsNode().size.height

    @Composable
    private fun Sheet(state: SheetScoresUiState) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, NARROW_FONT_SCALE)) {
            ItmoTheme {
                Box(Modifier.width(NARROW_WIDTH).heightIn(max = SHEET_MAX_HEIGHT)) {
                    SheetScoresSheet(SheetScoresPreviewSamples.SUBJECT, state, actions, Modifier.testTag(SHEET))
                }
            }
        }
    }

    private companion object {
        const val SHEET = "sheet"
        const val NARROW_FONT_SCALE = 1.3f
        val NARROW_WIDTH = 320.dp
        val SHEET_MAX_HEIGHT = 867.dp
    }
}
