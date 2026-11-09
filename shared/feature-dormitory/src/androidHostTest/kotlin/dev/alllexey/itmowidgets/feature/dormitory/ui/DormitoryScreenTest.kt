package dev.alllexey.itmowidgets.feature.dormitory.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.core.location.MapDestination
import dev.alllexey.itmowidgets.designsystem.kit.ContractTestTags
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.dormitory.presentation.DormitorySamples
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class DormitoryScreenTest {

    @Test
    fun everyStateKeepsTouchTargets() = runComposeUiTest {
        var state by mutableStateOf(DormitorySamples.queue)
        setContent { ItmoTheme { DormitoryScreen(state, DormitoryActions()) } }

        for (value in listOf(
            DormitorySamples.placeOffered, DormitorySamples.dateChosen, DormitorySamples.settled, DormitorySamples.debt,
            DormitorySamples.pastYears, DormitorySamples.contractOnly, DormitorySamples.offline, DormitorySamples.none,
            DormitorySamples.failed, DormitorySamples.loading,
        )) {
            state = value
            waitForIdle()
            assertTouchTargets()
        }
    }

    @Test
    fun anOfferedPlaceOpensMyItmoForTheDateAndTheDocumentsAndTheDormitoryOnTheMap() = runComposeUiTest {
        var myItmo = 0
        val maps = mutableListOf<MapDestination>()
        val actions = DormitoryActions(onOpenMyItmo = { myItmo++ }, onOpenMap = { maps += it })
        setContent { ItmoTheme { DormitoryScreen(DormitorySamples.placeOffered, actions) } }

        onNodeWithText("Место предоставлено").assertExists()
        onNodeWithTag(DormitoryTestTags.CHOOSE_DATE).performClick()
        onNodeWithTag(DormitoryTestTags.UPLOAD).performScrollTo().performClick()
        onNodeWithTag(DormitoryTestTags.MAP).performScrollTo().performClick()

        assertEquals(2, myItmo)
        assertEquals(listOf(MapDestination("Общежитие №1", "Санкт-Петербург, Примерный пер., 1")), maps)
    }

    @Test
    fun aDebtShowsEveryPaymentAndPayOpensTheBrowser() = runComposeUiTest {
        var pays = 0
        setContent { ItmoTheme { DormitoryScreen(DormitorySamples.debt, DormitoryActions(onPay = { pays++ })) } }

        onNodeWithText("Долг").performScrollTo().assertExists()
        onNodeWithText("Просрочен").performScrollTo().assertExists()
        onNodeWithText("Сегодня").performScrollTo().assertExists()
        onNodeWithTag(ContractTestTags.PAY).performScrollTo().performClick()

        assertEquals(1, pays)
    }

    @Test
    fun pastYearsUnfoldOnATap() = runComposeUiTest {
        var toggles = 0
        var state by mutableStateOf(DormitorySamples.settled)
        setContent { ItmoTheme { DormitoryScreen(state, DormitoryActions(onTogglePastYears = { toggles++ })) } }
        onNodeWithTag(ContractTestTags.PAST_YEARS).assertDoesNotExist()

        state = DormitorySamples.pastYears
        onNodeWithText("2025/2026 учебный год").performScrollTo().assertExists()
        onNodeWithTag(ContractTestTags.PAST_YEARS).performScrollTo().performClick()

        assertEquals(1, toggles)
    }

    @Test
    fun offlineTheStoredAnswerSaysHowOldItIs() = runComposeUiTest {
        setContent { ItmoTheme { DormitoryScreen(DormitorySamples.offline, DormitoryActions()) } }

        onNodeWithText("Обновлено 2 ч назад").assertExists()
        onNodeWithText("Заселён").assertExists()
    }

    @Test
    fun withoutAStoredAnswerAFailureOffersARetry() = runComposeUiTest {
        var retries = 0
        setContent { ItmoTheme { DormitoryScreen(DormitorySamples.failed, DormitoryActions(onRetry = { retries++ })) } }

        onNodeWithText("Повторить").performClick()
        assertEquals(1, retries)
    }
}
