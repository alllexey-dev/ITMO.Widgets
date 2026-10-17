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
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetCell
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
 * Every case of the deleted `SheetScoresVisualTest` on the Compose sheet, at 320 dp and font scale 1.3 in at most the
 * sheet's 867 dp: loading and every failure in one area with the retry only for the network, the own row among
 * several, the tab and its rows, the name search over a long roster, the total among many cells by tab. Its looks live
 * in the `SheetScoresSheet_*` goldens; closing on `Done` and the save snackbar stay in the shell's sheet entry.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class SheetScoresSheetTest {

    private var retries = 0
    private val rows = mutableListOf<SheetRowMatch>()
    private val tabs = mutableListOf<SheetTab>()
    private val totals = mutableListOf<SheetCell>()
    private val actions = SheetScoresActions(
        onRetry = { retries++ },
        onPickRow = { rows += it },
        onPickTab = { tabs += it },
        onPickTotal = { totals += it },
    )

    @Test
    fun loadingAndEveryFailureShareOneAreaAndOnlyTheNetworkRetries() = runComposeUiTest {
        var state: SheetScoresUiState by mutableStateOf(SheetScoresUiState.Loading)
        setContent { Sheet(state) }
        val height = sheetHeight()

        val failures = listOf(
            SheetStatus.NETWORK to "Нет связи",
            SheetStatus.CLOSED to "Таблица закрыта",
            SheetStatus.ROW_NOT_FOUND to "Строка не найдена",
            SheetStatus.COLUMN_NOT_FOUND to "Столбец не найден",
            SheetStatus.TOO_LARGE to "Таблица слишком большая",
        )
        failures.forEach { (status, text) ->
            state = SheetScoresUiState.Failed(status)
            waitForIdle()
            onNodeWithText(text).assertExists()
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
    fun severalOwnRowsShowTheirTabAndThePickedOneIsSent() = runComposeUiTest {
        setContent { Sheet(SheetScoresPreviewSamples.pickRow) }

        onNodeWithText("Выберите свою строку").assertExists()
        onAllNodesWithText(SheetScoresPreviewSamples.OWN_NAME).assertCountEquals(2)
        onAllNodesWithText("P3110", useUnmergedTree = true).assertCountEquals(2)
        onAllNodesWithTag(SheetScoresSheetTestTags.SEARCH).assertCountEquals(0)
        assertTouchTargets()

        onAllNodesWithText(SheetScoresPreviewSamples.OWN_NAME).onLast().performClick()
        assertEquals(listOf(SheetScoresPreviewSamples.pickRow.candidates[1]), rows)
    }

    @Test
    fun theTabsToLookInNameAnUntitledTabAndSendThePickedOne() = runComposeUiTest {
        setContent { Sheet(SheetScoresPreviewSamples.pickTab) }

        // The prompt and the untitled tab read the same.
        onAllNodesWithText("Выберите лист").assertCountEquals(2)
        onNodeWithText("P3110").assertExists()
        assertNoTextOverflow()
        assertTouchTargets()

        onNodeWithText(SheetScoresPreviewSamples.longTab.name).performClick()
        assertEquals(listOf(SheetScoresPreviewSamples.longTab), tabs)
    }

    @Test
    fun aLongRosterIsSearchedByNameAndKeepsTheQueryAcrossStates() = runComposeUiTest {
        var state: SheetScoresUiState by mutableStateOf(SheetScoresPreviewSamples.pickTabRow)
        setContent { Sheet(state) }

        onNodeWithText("Выберите свою строку").assertExists()
        onNodeWithText(SheetScoresPreviewSamples.LONG_NAME).assertExists()
        assertShown(SheetScoresPreviewSamples.longTab.name)
        assertNoTextOverflow()

        // Case and the letter yo do not matter.
        onNodeWithTag(SheetScoresSheetTestTags.SEARCH).performTextInput("ТЕМКИН")
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

        onNodeWithTag(SheetScoresSheetTestTags.SEARCH).performTextInput("никто")
        waitForIdle()
        onNodeWithText("Никого не нашлось").assertExists()

        onNodeWithTag(SheetScoresSheetTestTags.SEARCH_CLEAR).performClick()
        waitForIdle()
        onNodeWithText(SheetScoresPreviewSamples.LONG_NAME).assertExists()
        assertTouchTargets()
    }

    @Test
    fun withoutATotalHeaderEveryCellIsAGridByTabAndTheLastOneIsPicked() = runComposeUiTest {
        setContent { Sheet(SheetScoresPreviewSamples.pickManyTotals) }

        onNodeWithText("Выберите итог").assertExists()
        onAllNodes(isSelected()).assertCountEquals(0)
        // Values as the sheet has them; a column without a header is named by its letter.
        listOf("P3110", "66,3", "100%", "5A", "Столбец E", SheetScoresPreviewSamples.englishTab.name).forEach { text ->
            onNodeWithTag(SheetScoresSheetTestTags.LIST).performScrollToNode(hasText(text))
            assertShown(text)
            assertNoTextOverflow()
        }
        assertTouchTargets()

        val last = SheetScoresPreviewSamples.manyCells.last()
        onNodeWithTag(SheetScoresSheetTestTags.LIST).performScrollToKey("cells:${last.tab.gid}")
        onAllNodesWithText(last.headerPath).onLast().performScrollTo().performClick()
        assertEquals(listOf(last), totals)
    }

    @Test
    fun theReviewShowsTheTotalWhyAndTheChipsAndAChipIsPicked() = runComposeUiTest {
        setContent { Sheet(SheetScoresPreviewSamples.pickTotal) }

        assertShown("66,3")
        assertShown("Столбец «${SheetScoresPreviewSamples.TOTAL_HEADER}», сумма баллов сходится")
        // The total is selected in the row preview and among the chips.
        onAllNodes(isSelected()).assertCountEquals(2)
        onAllNodesWithText("Вернуть авто").assertCountEquals(0)
        assertTouchTargets()
        assertNoTextOverflow()

        onNodeWithTag(SheetScoresSheetTestTags.LIST).performScrollToNode(hasText("Итог · 37"))
        onNodeWithText("Итог · 37").performClick()
        assertEquals(listOf(SheetScoresPreviewSamples.ownRow.single { it.headerPath == "ЛР · Итог" }), totals)
    }

    @Test
    fun anotherCellOpensTheGridOfTheRow() = runComposeUiTest {
        setContent { Sheet(SheetScoresPreviewSamples.pickTotal) }
        onAllNodesWithText(SheetScoresPreviewSamples.group.name).assertCountEquals(0)

        onNodeWithTag(SheetScoresSheetTestTags.LIST).performScrollToNode(hasText("Другая ячейка"))
        onNodeWithText("Другая ячейка").performClick()
        waitForIdle()
        onNodeWithTag(SheetScoresSheetTestTags.LIST).performScrollToNode(hasText(SheetScoresPreviewSamples.group.name))
        assertShown(SheetScoresPreviewSamples.group.name)

        onNodeWithTag(SheetScoresSheetTestTags.LIST).performScrollToNode(hasText("5A"))
        onAllNodesWithText("Оценка").onLast().performClick()
        assertEquals(listOf(SheetScoresPreviewSamples.ownRow.last()), totals)
    }

    @Test
    fun aHandPickedTotalSaysSoAndResetsToTheAutomaticOne() = runComposeUiTest {
        var resets = 0
        setContent {
            SheetScoresSheet(
                SheetScoresPreviewSamples.SUBJECT,
                SheetScoresPreviewSamples.pickTotalManual,
                SheetScoresActions(onResetTotal = { resets++ }),
            )
        }

        assertShown("Выбрано вручную")
        onNodeWithTag(SheetScoresSheetTestTags.RESET).performClick()
        assertEquals(1, resets)
    }

    @Test
    fun anUnsureTotalSaysWhy() = runComposeUiTest {
        setContent { Sheet(SheetScoresPreviewSamples.pickTotalCheck) }

        assertShown("Столбец «${SheetScoresPreviewSamples.TOTAL_HEADER}», но строка есть и на других листах")
    }

    @Test
    fun aRowWithoutValuesSaysSo() = runComposeUiTest {
        setContent { Sheet(SheetScoresUiState.PickTotal(emptyList(), selected = null)) }

        onNodeWithText("В строке нет значений").assertExists()
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
            ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
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
