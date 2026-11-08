package dev.alllexey.itmowidgets.feature.finances.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.designsystem.kit.ContractTestTags
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.finances.presentation.TuitionSamples
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class TuitionScreenTest {

    @Test
    fun everyStateKeepsTouchTargets() = runComposeUiTest {
        var state by mutableStateOf(TuitionSamples.onTime)
        setContent { ItmoTheme { TuitionScreen(state, TuitionActions()) } }

        for (value in listOf(
            TuitionSamples.debt, TuitionSamples.pastYears, TuitionSamples.offline, TuitionSamples.none,
            TuitionSamples.failed, TuitionSamples.loading,
        )) {
            state = value
            waitForIdle()
            assertTouchTargets()
        }
    }

    @Test
    fun aDebtShowsEveryPaymentAndPayOpensTheBrowser() = runComposeUiTest {
        var pays = 0
        setContent { ItmoTheme { TuitionScreen(TuitionSamples.debt, TuitionActions(onPay = { pays++ })) } }

        onNodeWithText("Договор об обучении").assertExists()
        onNodeWithText("Долг").performScrollTo().assertExists()
        onNodeWithText("Просрочен").performScrollTo().assertExists()
        onNodeWithTag(ContractTestTags.PAY).performScrollTo().performClick()

        assertEquals(1, pays)
    }

    @Test
    fun pastYearsUnfoldOnATap() = runComposeUiTest {
        var toggles = 0
        setContent { ItmoTheme { TuitionScreen(TuitionSamples.pastYears, TuitionActions(onTogglePastYears = { toggles++ })) } }

        onNodeWithText("2025/2026 учебный год").performScrollTo().assertExists()
        onNodeWithTag(ContractTestTags.PAST_YEARS).performScrollTo().performClick()

        assertEquals(1, toggles)
    }

    @Test
    fun aBudgetStudentSeesThatThereIsNoContract() = runComposeUiTest {
        setContent { ItmoTheme { TuitionScreen(TuitionSamples.none, TuitionActions()) } }

        onNodeWithText("Договоров об обучении нет").assertExists()
    }
}
