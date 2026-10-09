package dev.alllexey.itmowidgets.feature.qr.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeSnapshot
import dev.alllexey.itmowidgets.feature.qr.presentation.QrCodeUiState
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class QrPassScreenTest {

    @Test
    fun theAreaIsASquareOfAtMost300DpAndTheRefreshButtonStaysPutInEveryState() = runComposeUiTest {
        var state by mutableStateOf<QrCodeUiState>(QrCodeUiState.Loading)
        var width by mutableStateOf(NarrowWidth)
        setContent {
            // The sides below are Material's (16 dp screen margin); DS-IOS-06's QR pilot checks the iOS look.
            ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
                Box(Modifier.requiredSize(width, WindowHeight)) { QrPassScreen(state, onRefresh = {}, onBack = {}) }
            }
        }

        for ((window, expectedSide) in listOf(NarrowWidth to NarrowSide, WideWidth to 300.dp)) {
            width = window
            val buttons = States.map { (name, value) ->
                state = value
                waitForIdle()
                val area = onNodeWithTag(QrPassTestTags.AREA).getBoundsInRoot()
                assertClose(area.width, area.height, "$name at $window: not square")
                assertClose(expectedSide, area.width, "$name at $window: side")
                assertTrue(area.width <= 300.dp + Tolerance, "$name at $window: ${area.width} above 300 dp")
                assertTouchTargets()
                onNodeWithTag(QrPassTestTags.REFRESH).getBoundsInRoot()
            }
            assertEquals(1, buttons.distinct().size, "the refresh button moved at $window: $buttons")
        }
    }

    @Test
    fun contentShowsTheLabelledCodeAndLoadingBlocksTheRefresh() = runComposeUiTest {
        var state by mutableStateOf<QrCodeUiState>(QrCodeUiState.Loading)
        var refreshes = 0
        var backs = 0
        setContent {
            ItmoTheme { QrPassScreen(state, onRefresh = { refreshes++ }, onBack = { backs++ }) }
        }

        onNodeWithTag(QrPassTestTags.LOADING).assertExists()
        onNodeWithTag(QrPassTestTags.REFRESH).assertIsNotEnabled()

        state = QrCodeUiState.Content(Pass)
        waitForIdle()
        onNodeWithContentDescription("QR-код пропуска").assertExists()
        onNodeWithTag(QrPassTestTags.REFRESH).assertIsEnabled().performClick()
        onNodeWithContentDescription("Назад").performClick()

        assertEquals(1, refreshes)
        assertEquals(1, backs)
    }

    @Test
    fun aPassTooLongForTheCodeShowsTheErrorStateInsteadOfCrashing() = runComposeUiTest {
        setContent {
            ItmoTheme {
                QrPassScreen(QrCodeUiState.Content(QrCodeSnapshot("X".repeat(64), 1)), onRefresh = {}, onBack = {})
            }
        }

        onNodeWithTag(QrPassTestTags.STATE).assertExists()
        onNodeWithTag(QrPassTestTags.IMAGE).assertDoesNotExist()
    }

    @Test
    fun aCodeThatArrivesGrowsInAndOneThereFromTheStartShowsAtOnce() = runComposeUiTest {
        var state by mutableStateOf<QrCodeUiState>(QrCodeUiState.Content(Pass))
        mainClock.autoAdvance = false
        setContent { ItmoTheme { QrPassScreen(state, onRefresh = {}, onBack = {}) } }
        mainClock.advanceTimeByFrame()
        assertImageFillsTheArea("a code on the first frame")

        state = QrCodeUiState.Content(QrCodeSnapshot("ITMO-NEXT", 3_600_000))
        Snapshot.sendApplyNotifications()
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        val area = onNodeWithTag(QrPassTestTags.AREA).getBoundsInRoot()
        val growing = onNodeWithTag(QrPassTestTags.IMAGE).getBoundsInRoot()
        assertTrue(
            growing.width < area.width - Tolerance,
            "a new code starts smaller: ${growing.width} of ${area.width}",
        )
        onNodeWithContentDescription("QR-код пропуска").assertExists()

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
        val Pass = QrCodeSnapshot("ITMO-TEST", 3_600_000)
        val States = listOf(
            "loading" to QrCodeUiState.Loading,
            "content" to QrCodeUiState.Content(Pass),
            "refreshing" to QrCodeUiState.Content(Pass, refreshing = true),
            "dynamic" to QrCodeUiState.Content(Pass, useDynamicColors = true),
            "empty" to QrCodeUiState.Empty,
            "error" to QrCodeUiState.Error(AppError.Network),
        )
        val NarrowWidth = 320.dp
        val WideWidth = 600.dp
        val WindowHeight = 891.dp

        /** 80 % of the width inside the 16 dp screen margins. */
        val NarrowSide = (320.dp - 32.dp) * 0.8f

        /** Pixel rounding of the area's side. */
        val Tolerance = 1.dp

        /** Longer than the expressive spatial spring needs to settle. */
        const val REVEAL_SETTLE_MILLIS = 2_000L
    }
}
