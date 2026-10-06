package dev.alllexey.itmowidgets.feature.sport.ui.sign

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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    }
}
