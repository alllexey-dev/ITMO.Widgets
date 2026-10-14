package dev.alllexey.itmowidgets.feature.schedule.presentation.month

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.lesson.LessonChange
import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.schedule.DaysRefresh
import dev.alllexey.itmowidgets.feature.schedule.FakeScheduleDays
import dev.alllexey.itmowidgets.feature.schedule.feedLesson
import dev.alllexey.itmowidgets.shared.feature.schedule.ScheduleStrings
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

class ScheduleMonthViewModelTest {

    private val main = TestMainDispatcher()
    private val time = FixedAcademicTime(LocalDateTime(2026, 9, 7, 12, 22))

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    private fun viewModel(days: FakeScheduleDays, date: String = "2026-09-17") =
        ScheduleMonthViewModel(SavedStateHandle(mapOf(ScheduleMonthViewModel.ARG_DATE to date)), days, time)

    @Test
    fun theMonthHasADotPerLessonThatTakesPlaceAndOpensTheChosenDay() = runTest(main.dispatcher) {
        val cancelled = feedLesson("11:40", "13:10").copy(change = LessonChange(LocalDateTime(2026, 9, 6, 20, 14), cancelled = true))
        val days = FakeScheduleDays(
            LessonDay(LocalDate(2026, 9, 17), listOf(feedLesson("10:00", "11:30"), cancelled), null),
            LessonDay(LocalDate(2026, 9, 8), List(6) { feedLesson("08:20", "09:50") }, null),
        )
        val vm = viewModel(days)

        val state = vm.uiState.first { month -> month.days.any { it.lessons > 0 } }

        assertEquals("Сентябрь 2026", state.title)
        assertEquals(1, state.leading)
        assertEquals(30, state.days.size)
        assertEquals(1, state.days[16].lessons)
        assertEquals(4, state.days[7].lessons)
        assertTrue(state.days[6].today)
        assertTrue(state.days[16].selected)
        assertEquals(UiText.Res(ScheduleStrings.schedule_month_open, listOf("четверг, 17 сентября")), state.open)
    }

    @Test
    fun eachMonthShownRefreshesOnceAndAChoiceMovesTheButton() = runTest(main.dispatcher) {
        val days = FakeScheduleDays()
        val vm = viewModel(days)
        vm.uiState.first()

        vm.nextMonth()
        assertEquals("Октябрь 2026", vm.uiState.first { it.title.startsWith("Октябрь") }.title)
        vm.previousMonth()
        vm.uiState.first { it.title.startsWith("Сентябрь") }
        vm.select(LocalDate(2026, 9, 15))

        assertEquals(LocalDate(2026, 9, 15), vm.uiState.first { it.selected == LocalDate(2026, 9, 15) }.selected)
        assertEquals(
            setOf(DaysRefresh(null, LocalDate(2026, 9, 1), LocalDate(2026, 9, 30)), DaysRefresh(null, LocalDate(2026, 10, 1), LocalDate(2026, 10, 31))),
            days.refreshes.toSet(),
        )
        assertEquals(2, days.refreshes.size)
    }
}
