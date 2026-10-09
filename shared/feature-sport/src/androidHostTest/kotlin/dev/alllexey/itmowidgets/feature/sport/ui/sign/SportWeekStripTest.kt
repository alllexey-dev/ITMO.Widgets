package dev.alllexey.itmowidgets.feature.sport.ui.sign

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Density
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignUiState
import dev.alllexey.itmowidgets.testkit.assertNoTextOverflow
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The week strip that replaces `SportSignWeekPagerAdapter` and `SportSignCalendarAdapter`. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp")
class SportWeekStripTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private var state by mutableStateOf(SportSignFiltersSamples.week(0))

    @Test
    fun `an arrow moves exactly one week each way`() {
        show()

        compose.onNodeWithTag(SportWeekStripTestTags.NEXT).performClick()
        compose.onNodeWithTag(SportWeekStripTestTags.day(monday(1))).assertIsDisplayed().assertIsSelected()
        assertDayOffScreen(monday(0))
        assertDayOffScreen(monday(2))

        compose.onNodeWithTag(SportWeekStripTestTags.PREVIOUS).performClick()
        compose.onNodeWithTag(SportWeekStripTestTags.day(monday(0))).assertIsDisplayed()
        assertDayOffScreen(monday(1))

        assertEquals(listOf("next", "previous"), calls)
    }

    @Test
    fun `the arrows follow the first and the last week`() {
        show()
        compose.onNodeWithTag(SportWeekStripTestTags.PREVIOUS).assertIsNotEnabled()
        compose.onNodeWithTag(SportWeekStripTestTags.NEXT).assertIsEnabled()

        state = SportSignFiltersSamples.week(SportSignFiltersSamples.LAST_WEEK)

        compose.onNodeWithTag(SportWeekStripTestTags.PREVIOUS).assertIsEnabled()
        compose.onNodeWithTag(SportWeekStripTestTags.NEXT).assertIsNotEnabled()
    }

    @Test
    fun `a swipe moves no week`() {
        show()

        compose.onNodeWithTag(SportWeekStripTestTags.PAGER).performTouchInput { swipeLeft() }
        compose.onNodeWithTag(SportWeekStripTestTags.PAGER).performTouchInput { swipeRight() }

        compose.onNodeWithTag(SportWeekStripTestTags.day(SportSignFiltersSamples.today)).assertIsDisplayed()
        assertDayOffScreen(monday(1))
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `a swipe on the strip inside an outer pager moves neither`() {
        val outer = PagerState(currentPage = 0) { 2 }
        compose.setContent {
            ItmoTheme {
                HorizontalPager(outer, Modifier.fillMaxSize()) { page ->
                    Box(Modifier.fillMaxSize()) { if (page == 0) Strip() }
                }
            }
        }

        compose.onNodeWithTag(SportWeekStripTestTags.PAGER).performTouchInput { swipeLeft() }
        compose.onNodeWithTag(SportWeekStripTestTags.MONTH).performTouchInput { swipeLeft() }

        compose.runOnIdle {
            assertEquals(0, outer.currentPage)
            assertEquals(0f, outer.currentPageOffsetFraction, 0f)
        }
        assertDayOffScreen(monday(1))
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `a tap on a day selects it once`() {
        show()
        val friday = monday(0).plus(4, DateTimeUnit.DAY)

        compose.onNodeWithTag(SportWeekStripTestTags.day(friday)).performClick()

        assertEquals(listOf("date $friday"), calls)
    }

    @Test
    fun `every day and arrow is a full touch target at 360 dp and font 1_3`() {
        show(fontScale = LARGE_FONT)

        compose.assertNoTextOverflow()
        compose.assertTouchTargets()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun `the month, the arrows and every day fit at 320 dp and font 1_3 with full touch targets`() {
        state = SportSignFiltersSamples.week(SportSignFiltersSamples.LAST_WEEK).copy(currentMonthName = "Сентябрь")
        show(fontScale = LARGE_FONT)

        compose.assertNoTextOverflow()
        compose.assertTouchTargets()
        compose.onNodeWithTag(SportWeekStripTestTags.PREVIOUS).assertIsEnabled()
        compose.onNodeWithTag(SportWeekStripTestTags.STRIP).assertIsDisplayed()
    }

    @Test
    fun `a day card without lessons fades the container colour, never through grey`() {
        val monday = monday(0)
        val sunday = monday.plus(6, DateTimeUnit.DAY)
        state = selecting(monday)
        var container = Color.Unspecified
        var surface = Color.Unspecified
        compose.setContent {
            ItmoTheme {
                container = ItmoTheme.colorScheme.primaryContainer
                surface = ItmoTheme.colorScheme.surface
                Box(Modifier.background(surface)) { Strip() }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        for ((from, to) in listOf(monday to sunday, sunday to monday)) {
            state = selecting(to)
            val fadedIn = mutableListOf<Float>()
            val fadedOut = mutableListOf<Float>()
            repeat(FADE_FRAMES) {
                compose.mainClock.advanceTimeByFrame()
                fadedIn += cardTint(to, container, surface)
                fadedOut += cardTint(from, container, surface)
            }
            assertTrue("$to fades in mid-way: $fadedIn", fadedIn.any { it in MID_FADE })
            assertTrue("$from fades out mid-way: $fadedOut", fadedOut.any { it in MID_FADE })
            assertEquals(1f, fadedIn.last(), END_TOLERANCE)
            assertEquals(0f, fadedOut.last(), END_TOLERANCE)
        }
    }

    private fun selecting(date: LocalDate): SportSignUiState.Content {
        val weeks = SportSignFiltersSamples.calendarWeeks(selected = date)
        return SportSignFiltersSamples.week(0).copy(displayedWeek = weeks[0], calendarWeeks = weeks)
    }

    /**
     * How much of [container] the day card shows over [surface] at the current frame, from 0 to 1. A pixel off the
     * line between the two colours (the grey a fade through transparent black leaves) fails the test.
     */
    private fun cardTint(date: LocalDate, container: Color, surface: Color): Float {
        val image = compose.onNodeWithTag(SportWeekStripTestTags.dayCard(date), useUnmergedTree = true)
            .captureToImage()
            .asAndroidBitmap()
        // Above the day number, inside the card even while a newly selected day scales in from 0.82.
        val pixel = Color(image.getPixel(image.width / 2, image.height / 5))
        val channels = listOf(Color::red, Color::green, Color::blue)
        val span = channels.map { it(container) - it(surface) }
        val offset = channels.map { it(pixel) - it(surface) }
        val tint = (span.zip(offset).sumOf { (s, o) -> (s * o).toDouble() } / span.sumOf { (it * it).toDouble() })
            .toFloat()
            .coerceIn(0f, 1f)
        channels.forEachIndexed { index, channel ->
            val expected = channel(surface) + span[index] * tint
            assertTrue(
                "$date at tint $tint: $pixel is not between $surface and $container",
                abs(channel(pixel) - expected) <= CHANNEL_TOLERANCE,
            )
        }
        return tint
    }

    private fun show(fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                ItmoTheme { Strip() }
            }
        }
    }

    /** The strip as the ViewModel drives it: an arrow moves the selected date to that week's Monday. */
    @Composable
    private fun Strip() {
        SportWeekStrip(
            state,
            onPreviousWeek = {
                calls += "previous"
                state = SportSignFiltersSamples.week(state.selectedWeekIndex - 1)
            },
            onNextWeek = {
                calls += "next"
                state = SportSignFiltersSamples.week(state.selectedWeekIndex + 1)
            },
            onSelectDate = { calls += "date $it" },
        )
    }

    /** The day is not in the strip's viewport: the pager may keep the week it left composed beside it. */
    private fun assertDayOffScreen(date: LocalDate) {
        val strip = compose.onNodeWithTag(SportWeekStripTestTags.PAGER).fetchSemanticsNode().boundsInRoot
        compose.onAllNodesWithTag(SportWeekStripTestTags.day(date)).fetchSemanticsNodes().forEach { day ->
            assertFalse("$date is on screen", day.boundsInRoot.overlaps(strip))
        }
    }

    private fun monday(week: Int): LocalDate = SportSignFiltersSamples.firstMonday.plus(week, DateTimeUnit.WEEK)

    private companion object {
        const val LARGE_FONT = 1.3f
        const val FADE_FRAMES = 30
        const val CHANNEL_TOLERANCE = 3f / 255
        const val END_TOLERANCE = 0.02f
        val MID_FADE = 0.2f..0.8f
    }
}
