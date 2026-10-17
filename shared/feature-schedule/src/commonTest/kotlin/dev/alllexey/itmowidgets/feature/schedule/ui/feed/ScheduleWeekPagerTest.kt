package dev.alllexey.itmowidgets.feature.schedule.ui.feed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.schedule.presentation.feed.FeedJump
import dev.alllexey.itmowidgets.feature.schedule.presentation.feed.ScheduleFeedPresenter
import dev.alllexey.itmowidgets.feature.schedule.presentation.feed.ScheduleFeedUiState
import dev.alllexey.itmowidgets.feature.schedule.ui.preview.ScheduleSamples
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ScheduleWeekPagerTest {
    @Test
    fun programmaticWeekChangesDoNotReportAUserSwipe() = runComposeUiTest {
        var state by mutableStateOf(ScheduleSamples.Feed)
        val picked = mutableListOf<LocalDate>()
        setContent { ItmoTheme { ScheduleWeekPager(state, ScheduleFeedActions(onWeek = { picked += it })) } }

        runOnIdle { state = selected(state, LocalDate(2026, 9, 16)) }
        waitForIdle()

        onNodeWithTag(ScheduleFeedTestTags.week(LocalDate(2026, 9, 14))).assertIsDisplayed()
        assertEquals(emptyList(), picked)
    }

    @Test
    fun prependingWeeksPreservesTheVisibleWeekWithoutReportingASwipe() = runComposeUiTest {
        var state by mutableStateOf(ScheduleSamples.Feed)
        val picked = mutableListOf<LocalDate>()
        setContent { ItmoTheme { ScheduleWeekPager(state, ScheduleFeedActions(onWeek = { picked += it })) } }

        runOnIdle {
            val first = state.weeks.first()
            val earlier = first.copy(
                monday = first.monday.minus(1, DateTimeUnit.WEEK),
                days = first.days.map { day ->
                    val date = day.date.minus(1, DateTimeUnit.WEEK)
                    day.copy(date = date, number = date.day.toString(), today = false, past = true)
                },
            )
            state = state.copy(weeks = listOf(earlier) + state.weeks, selectedWeek = state.selectedWeek + 1)
        }
        waitForIdle()

        onNodeWithTag(ScheduleFeedTestTags.week(LocalDate(2026, 9, 7))).assertIsDisplayed()
        assertEquals(emptyList(), picked)
    }

    @Test
    fun aSecondSwipeCanInterruptTheFirstFlingWithoutAFeedbackLoop() = runComposeUiTest {
        var state by mutableStateOf(ScheduleSamples.Feed)
        val picked = mutableListOf<LocalDate>()
        setContent {
            ItmoTheme {
                ScheduleWeekPager(state, ScheduleFeedActions(onWeek = {
                    picked += it
                    state = selected(state, it)
                }))
            }
        }
        mainClock.autoAdvance = false
        onNodeWithTag(ScheduleFeedTestTags.STRIP).performTouchInput { swipeLeft(durationMillis = 80) }
        mainClock.advanceTimeBy(32)
        onNodeWithTag(ScheduleFeedTestTags.STRIP).performTouchInput { swipeLeft(durationMillis = 80) }
        mainClock.advanceTimeBy(3_000)
        waitForIdle()

        assertTrue(picked.isNotEmpty())
        val finalWeek = picked.last()
        onNodeWithTag(ScheduleFeedTestTags.week(finalWeek)).assertIsDisplayed()
        val count = picked.size
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        assertEquals(count, picked.size)
        assertEquals(finalWeek, state.selected)
        mainClock.autoAdvance = true
    }

    @Test
    fun anExplicitTodayJumpInterruptsAFlingEvenWithinTheCurrentWeek() = runComposeUiTest {
        var state by mutableStateOf(ScheduleSamples.Feed)
        val picked = mutableListOf<LocalDate>()
        val today = state.selected
        setContent {
            ItmoTheme {
                ScheduleWeekPager(state, ScheduleFeedActions(
                    onWeek = {
                        if (state.jump == null) {
                            picked += it
                            state = selected(state, it)
                        }
                    },
                    onWeekPagingStarted = { state = state.copy(jump = null) },
                ))
            }
        }
        mainClock.autoAdvance = false
        onNodeWithTag(ScheduleFeedTestTags.STRIP).performTouchInput { swipeLeft(durationMillis = 80) }
        mainClock.advanceTimeBy(32)
        runOnIdle { state = selected(state, today).copy(jump = FeedJump(today, 999)) }
        mainClock.advanceTimeBy(3_000)
        waitForIdle()

        onNodeWithTag(ScheduleFeedTestTags.week(ScheduleFeedPresenter.monday(today))).assertIsDisplayed()
        assertEquals(today, state.selected)
        assertEquals(emptyList(), picked)
        mainClock.autoAdvance = true
    }

    private fun selected(state: ScheduleFeedUiState, date: LocalDate): ScheduleFeedUiState = state.copy(
        selected = date,
        selectedWeek = state.weeks.indexOfFirst { it.monday == ScheduleFeedPresenter.monday(date) },
    )
}
