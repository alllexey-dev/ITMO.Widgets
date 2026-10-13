package dev.alllexey.itmowidgets.feature.qr.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.qr.presentation.QrPassCode
import dev.alllexey.itmowidgets.feature.qr.presentation.QrPassStatus
import dev.alllexey.itmowidgets.feature.qr.presentation.QrPassUiState
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class QrPassSheetTest {

    @Test
    fun theAreaIsASquareOfAtMost240DpInEveryState() = runComposeUiTest {
        var state by mutableStateOf(QrPassUiState())
        var width by mutableStateOf(NarrowWidth)
        setContent {
            // The sides below are Material's (24 dp sheet margin).
            ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
                Box(Modifier.requiredWidth(width)) { QrPassSheetContent(state, onRefresh = {}, onClose = {}) }
            }
        }

        for ((window, expectedSide) in listOf(NarrowWidth to NarrowSide, WideWidth to 240.dp)) {
            width = window
            States.forEach { (name, value) ->
                state = value
                waitForIdle()
                val area = onNodeWithTag(QrPassTestTags.AREA).getBoundsInRoot()
                assertClose(area.width, area.height, "$name at $window: not square")
                assertClose(expectedSide, area.width, "$name at $window: side")
                assertTouchTargets()
            }
        }
    }

    @Test
    fun theLineSaysWhenTheCodeLoadedAndRefreshWaitsWhileItLoads() = runComposeUiTest {
        var state by mutableStateOf(QrPassUiState(code = Code, status = QrPassStatus.Ready("11:44")))
        var refreshes = 0
        var closes = 0
        setContent { ItmoTheme { QrPassSheetContent(state, onRefresh = { refreshes++ }, onClose = { closes++ }) } }

        onNodeWithContentDescription("QR-код пропуска").assertExists()
        onNodeWithTag(QrPassTestTags.LINE).assertTextEquals("Обновлён в 11:44")
        onNodeWithTag(QrPassTestTags.REFRESH).assertIsEnabled().performClick()
        onNodeWithContentDescription("Закрыть").performClick()

        state = QrPassUiState(code = Code, status = QrPassStatus.Refreshing)
        waitForIdle()
        onNodeWithTag(QrPassTestTags.REFRESH).assertIsNotEnabled()

        state = QrPassUiState(code = Code, status = QrPassStatus.Offline("11:44"))
        waitForIdle()
        onNodeWithTag(QrPassTestTags.LINE).assertTextEquals("Нет сети, показан код от 11:44")
        onNodeWithContentDescription("QR-код пропуска").assertExists()

        assertEquals(1, refreshes)
        assertEquals(1, closes)
    }

    @Test
    fun withoutACodeAnErrorOffersARetry() = runComposeUiTest {
        var refreshes = 0
        val state = QrPassUiState(status = QrPassStatus.Failed(AppError.Network))
        setContent { ItmoTheme { QrPassSheetContent(state, onRefresh = { refreshes++ }, onClose = {}) } }

        onNodeWithTag(QrPassTestTags.STATE).assertExists()
        onNodeWithTag(QrPassTestTags.LINE).assertDoesNotExist()
        onNodeWithText("Повторить").performClick()
        assertEquals(1, refreshes)
    }

    @Test
    fun aPassTooLongForTheCodeShowsTheErrorStateInsteadOfCrashing() = runComposeUiTest {
        val state = QrPassUiState(code = QrPassCode("X".repeat(64), false), status = QrPassStatus.Ready("11:44"))
        setContent { ItmoTheme { QrPassSheetContent(state, onRefresh = {}, onClose = {}) } }

        onNodeWithTag(QrPassTestTags.STATE).assertExists()
        onNodeWithTag(QrPassTestTags.IMAGE).assertDoesNotExist()
    }

    @Test
    fun aCodeThatArrivesGrowsInAndOneThereFromTheStartShowsAtOnce() = runComposeUiTest {
        var state by mutableStateOf(QrPassUiState(code = Code, status = QrPassStatus.Ready("11:44")))
        mainClock.autoAdvance = false
        setContent { ItmoTheme { QrPassSheetContent(state, onRefresh = {}, onClose = {}) } }
        mainClock.advanceTimeByFrame()
        assertImageFillsTheArea("a code on the first frame")

        state = QrPassUiState(code = QrPassCode("ITMO-NEXT", false), status = QrPassStatus.Ready("11:45"))
        Snapshot.sendApplyNotifications()
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        val area = onNodeWithTag(QrPassTestTags.AREA).getBoundsInRoot()
        val growing = onNodeWithTag(QrPassTestTags.IMAGE).getBoundsInRoot()
        assertTrue(growing.width < area.width - Tolerance, "a new code starts smaller: ${growing.width} of ${area.width}")

        mainClock.advanceTimeBy(REVEAL_SETTLE_MILLIS)
        assertImageFillsTheArea("a revealed code")
    }

    private fun ComposeUiTest.assertImageFillsTheArea(message: String) {
        val area = onNodeWithTag(QrPassTestTags.AREA).getBoundsInRoot()
        val image = onNodeWithTag(QrPassTestTags.IMAGE).getBoundsInRoot()
        assertClose(area.width, image.width, "$message: width")
        assertClose(area.height, image.height, "$message: height")
    }

    private fun assertClose(expected: Dp, actual: Dp, message: String) =
        assertTrue(abs(expected.value - actual.value) <= Tolerance.value, "$message: expected $expected, got $actual")

    private companion object {
        val Code = QrPassCode("ITMO-TEST", useDynamicColors = false)
        val States = listOf(
            "loading" to QrPassUiState(),
            "ready" to QrPassUiState(code = Code, status = QrPassStatus.Ready("11:44")),
            "refreshing" to QrPassUiState(code = Code, status = QrPassStatus.Refreshing),
            "dynamic" to QrPassUiState(code = Code.copy(useDynamicColors = true), status = QrPassStatus.Ready("11:44")),
            "empty" to QrPassUiState(status = QrPassStatus.Empty),
            "error" to QrPassUiState(status = QrPassStatus.Failed(AppError.Network)),
        )
        val NarrowWidth = 320.dp
        val WideWidth = 600.dp

        /** 80 % of the width inside the 24 dp sheet margins. */
        val NarrowSide = (320.dp - 48.dp) * 0.8f

        /** Pixel rounding of the area's side. */
        val Tolerance = 1.dp

        /** Longer than the expressive spatial spring needs to settle. */
        const val REVEAL_SETTLE_MILLIS = 2_000L
    }
}
