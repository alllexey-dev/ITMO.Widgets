package dev.alllexey.itmowidgets.feature.sport.ui.my

import android.animation.ValueAnimator
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertNoTextOverflow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class SportScoreCardTest {

    @Test
    fun countersCountUpOver700MsAndTheRawBonusShowsAtTheEnd() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent { Card(score(90, 52)) }

        assertEquals(0, number(SportScoreCardTestTags.TOTAL))
        mainClock.advanceTimeBy(350)
        val total = number(SportScoreCardTestTags.TOTAL)
        assertTrue(total in 1 until 130, "mid-count total $total")
        assertTrue(number(SportScoreCardTestTags.BONUS) in 0..40, "a plain bonus count while running")

        mainClock.advanceTimeBy(400)
        onNodeWithTag(SportScoreCardTestTags.TOTAL).assertTextEquals("130")
        onNodeWithTag(SportScoreCardTestTags.ATTENDANCE).assertTextEquals("90")
        onNodeWithTag(SportScoreCardTestTags.BONUS).assertTextEquals("40 (52)")
    }

    @Test
    fun aNewScoreCountsOnFromWhatIsShown() = runComposeUiTest {
        mainClock.autoAdvance = false
        var score by mutableStateOf(score(48, 20))
        setContent { Card(score) }
        mainClock.advanceTimeBy(1_000)
        onNodeWithTag(SportScoreCardTestTags.TOTAL).assertTextEquals("68")

        score = score(60, 20)
        mainClock.advanceTimeBy(350)
        val total = number(SportScoreCardTestTags.TOTAL)
        assertTrue(total in 68 until 80, "counts on from 68, not from zero: $total")
        mainClock.advanceTimeBy(1_000)
        onNodeWithTag(SportScoreCardTestTags.TOTAL).assertTextEquals("80")
    }

    @Test
    fun withoutAnimationEverythingShowsItsEndStateAtOnce() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent { Card(score(90, 52), animated = false) }

        onNodeWithTag(SportScoreCardTestTags.TOTAL).assertTextEquals("130")
        onNodeWithTag(SportScoreCardTestTags.BONUS).assertTextEquals("40 (52)")
        onNodeWithText("Зачёт").assertExists()
    }

    @Test
    fun reducedMotionShowsTheEndStateAtOnce() {
        setDurationScale(0f)
        try {
            runComposeUiTest {
                mainClock.autoAdvance = false
                setContent { Card(score(48, 20)) }

                onNodeWithTag(SportScoreCardTestTags.TOTAL).assertTextEquals("68")
                onNodeWithTag(SportScoreCardTestTags.ATTENDANCE).assertTextEquals("48")
                onNodeWithTag(SportScoreCardTestTags.BONUS).assertTextEquals("20")
            }
        } finally {
            setDurationScale(1f)
        }
    }

    @Test
    fun theStatusGrowsInAfterTheCountersStartAndSettlesAtItsFullSize() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent { Card(score(48, 20)) }
        mainClock.advanceTimeByFrame()
        val start = statusWidth()

        mainClock.advanceTimeBy(2_000)
        val settled = statusWidth()
        assertTrue(start < settled * 0.9f, "the status starts smaller: $start of $settled")

        mainClock.advanceTimeBy(1_000)
        assertEquals(settled, statusWidth(), "the status rests at its full size")
    }

    @Test
    fun theStatusSaysHowManyPointsAreLeftUntilThePass() = runComposeUiTest {
        var score by mutableStateOf(score(48, 20))
        setContent { Card(score, animated = false) }
        onNodeWithText("Ещё 32 балла").assertExists()

        score = score(99, 0)
        waitForIdle()
        onNodeWithText("Ещё 1 балл").assertExists()

        score = score(100, 0)
        waitForIdle()
        onNodeWithText("Зачёт").assertExists()
    }

    @Test
    fun nothingOverflowsAt320DpAndFontScale13() = runComposeUiTest {
        var score by mutableStateOf(score(48, 20))
        setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, NARROW_FONT_SCALE)) {
                Card(score, Modifier.width(NARROW_CARD_WIDTH), animated = false)
            }
        }
        assertNoTextOverflow()

        score = score(100, 52)
        waitForIdle()
        onNodeWithTag(SportScoreCardTestTags.BONUS).assertTextEquals("40 (52)")
        assertNoTextOverflow()
    }

    @Composable
    private fun Card(score: SportScore, modifier: Modifier = Modifier, animated: Boolean = true) {
        ItmoTheme(platformStyle = ItmoPlatformStyle.Material) { SportScoreCard(score, modifier, animated = animated) }
    }

    private fun ComposeUiTest.number(tag: String): Int =
        onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].single().text.toInt()

    /** The status text's drawn width: the chip's scale shows in its bounds, its layout size does not change. */
    private fun ComposeUiTest.statusWidth(): Float =
        onNodeWithText("Ещё 32 балла").fetchSemanticsNode().boundsInRoot.width

    private fun score(attendances: Int, bonus: Int) = SportScore(attendances, bonus, emptyList())

    /** `ValueAnimator.setDurationScale` is hidden; android-all has it (the animator scale of reduced motion). */
    private fun setDurationScale(scale: Float) {
        ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, scale)
    }

    private companion object {
        const val NARROW_FONT_SCALE = 1.3f

        /** A 320 dp window less the screen margins. */
        val NARROW_CARD_WIDTH = 288.dp
    }
}
