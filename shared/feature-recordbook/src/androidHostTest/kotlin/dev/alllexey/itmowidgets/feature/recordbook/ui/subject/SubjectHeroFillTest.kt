package dev.alllexey.itmowidgets.feature.recordbook.ui.subject

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The result card's scale fill (M3-04e): at once on the page's first frame, on the hero spring after that. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class SubjectHeroFillTest {

    private var score by mutableStateOf<Double?>(null)
    private var fill: () -> Double? = { null }

    @Test
    fun theScoreOnTheFirstFrameShowsAtOnce() = runComposeUiTest {
        score = START
        start()

        assertFill(START, "the first frame")
    }

    @Test
    fun aScoreThatArrivesAfterTheLoadFillsFromEmpty() = runComposeUiTest {
        start()
        assertNull(fill(), "no score while the page loads")

        change(TARGET)
        val growing = assertNotNull(fill(), "an arriving score")
        assertTrue(growing < TARGET - TOLERANCE, "the scale starts below the score: $growing")

        mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertFill(TARGET, "a settled fill")
    }

    @Test
    fun aChangedScoreFillsFromWhereTheScaleStood() = runComposeUiTest {
        score = START
        start()

        change(TARGET)
        val moving = assertNotNull(fill(), "a changed score")
        assertTrue(moving > START - TOLERANCE && moving < TARGET - TOLERANCE, "the fill leaves $START: $moving")

        mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertFill(TARGET, "a settled fill")
    }

    @Test
    fun aRemovedScoreEmptiesTheScaleAtOnce() = runComposeUiTest {
        score = START
        start()

        change(null)

        assertNull(fill(), "a removed score")
    }

    private fun ComposeUiTest.start() {
        mainClock.autoAdvance = false
        setContent { ItmoTheme { fill = rememberSubjectHeroFill(score) } }
        mainClock.advanceTimeByFrame()
    }

    private fun ComposeUiTest.change(value: Double?) {
        score = value
        Snapshot.sendApplyNotifications()
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
    }

    private fun assertFill(expected: Double, message: String) {
        val actual = assertNotNull(fill(), message)
        assertTrue(abs(expected - actual) <= TOLERANCE, "$message: expected $expected, got $actual")
    }

    private companion object {
        const val START = 58.5
        const val TARGET = 76.5

        /** Float rounding of the animated value. */
        const val TOLERANCE = 0.01

        /** Longer than the expressive spatial spring needs to settle. */
        const val SETTLE_MILLIS = 2_000L
    }
}
