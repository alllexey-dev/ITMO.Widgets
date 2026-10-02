package dev.alllexey.itmowidgets.feature.sport.presentation.sign

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.util.DataState
import dev.alllexey.itmowidgets.core.util.MergedDataState
import dev.alllexey.itmowidgets.feature.sport.cards.SportCardFixtures
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.domain.model.UnavailableReason
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeAcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportDataRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportScheduleRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportSignPreferences
import dev.alllexey.itmowidgets.feature.sport.presentation.bookingDelegate
import dev.alllexey.itmowidgets.feature.sport.presentation.emptyCatalog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SportSignViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val schedule = FakeSportScheduleRepository()
    private val data = FakeSportDataRepository()
    private val time = FakeAcademicTimeProvider()

    private fun TestScope.viewModel() = SportSignViewModel(
        schedule, data, SportSignFilterController(time), SportSignStateFactory(time),
        bookingDelegate(FakeSportBookingRepository(), schedule, data, this), FakeSportSignPreferences, time
    )

    private suspend fun emitSnapshot() {
        schedule.filters.emit(DataState.Success(emptyCatalog()))
        schedule.timeSlots.emit(DataState.Success(emptyList()))
        schedule.schedule.emit(MergedDataState.Success(listOf(SportCardFixtures.lesson(1))))
    }

    @Test
    fun `a refresh keeps the catalogue on screen and only marks it refreshing`() = runTest(mainDispatcherRule.dispatcher) {
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()
        val before = viewModel.uiState.value as SportSignUiState.Content
        assertFalse(before.refreshing)

        schedule.gate = CompletableDeferred()
        viewModel.refreshAllData()
        runCurrent()
        val during = viewModel.uiState.value as SportSignUiState.Content
        assertTrue(during.refreshing)
        assertEquals(before.displayedWeek, during.displayedWeek)

        schedule.gate.complete(Unit)
        advanceUntilIdle()
        assertFalse((viewModel.uiState.value as SportSignUiState.Content).refreshing)
    }

    @Test
    fun `before the catalogue answers the calendar shows over a placeholder without an indicator`() = runTest(mainDispatcherRule.dispatcher) {
        schedule.gate = CompletableDeferred()
        val viewModel = viewModel()
        runCurrent()
        val initial = viewModel.uiState.value as SportSignUiState.Content
        assertTrue(initial.initialLoading)
        assertFalse(initial.refreshing)
        assertEquals(7, initial.displayedWeek.size)
        assertTrue(initial.displayedLessons.isEmpty())
        schedule.gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(SportSignUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `the entry refresh is silent and a pull shows the indicator`() = runTest(mainDispatcherRule.dispatcher) {
        emitSnapshot()
        val viewModel = viewModel()
        runCurrent()
        assertFalse((viewModel.uiState.value as SportSignUiState.Content).refreshing)
        advanceUntilIdle()
        schedule.gate = CompletableDeferred()
        viewModel.refreshAllData()
        runCurrent()
        assertTrue((viewModel.uiState.value as SportSignUiState.Content).refreshing)
        schedule.gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `a failed source after content keeps the content with a partial error`() = runTest(mainDispatcherRule.dispatcher) {
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()

        schedule.schedule.emit(MergedDataState.Error(AppError.Network))
        advanceUntilIdle()

        val after = viewModel.uiState.value as SportSignUiState.Content
        assertTrue(after.hasPartialError)
        assertFalse(after.refreshing)
    }

    @Test
    fun `a failed source before any content is an error`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = viewModel()
        schedule.filters.emit(DataState.Error(AppError.Network))
        schedule.timeSlots.emit(DataState.Error(AppError.Network))
        schedule.schedule.emit(MergedDataState.Error(AppError.Network))
        advanceUntilIdle()
        assertEquals(SportSignUiState.Error(AppError.Network), viewModel.uiState.value)
    }

    private suspend fun emitCatalog(vararg lessons: SportLesson) {
        schedule.filters.emit(DataState.Success(emptyCatalog()))
        schedule.timeSlots.emit(DataState.Success(emptyList()))
        schedule.schedule.emit(MergedDataState.Success(lessons.toList()))
    }

    private fun lessonOn(id: Long, day: Int, hour: Int = 18) = SportCardFixtures.lesson(id).let { lesson ->
        val start = lesson.start.withDayOfMonth(day).withHour(hour)
        lesson.copy(start = start, end = start.plusMinutes(90))
    }

    private fun SportLesson.predicted() = copy(isLessonReal = false, start = start.plusDays(14), end = end.plusDays(14))

    @Test
    fun `a shared lesson hidden by the filters selects its day and opens its card`() = runTest(mainDispatcherRule.dispatcher) {
        val hidden = lessonOn(5, day = 10).copy(
            available = 0, canSignIn = false, unavailableReasons = listOf(UnavailableReason.DailyLimitReached)
        )
        emitCatalog(SportCardFixtures.lesson(1), hidden)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.openSharedLesson(5)
        advanceUntilIdle()

        assertEquals(SportSignEvent.OpenLessonDetails(hidden), viewModel.events.first())
        assertEquals(LocalDate.of(2026, 9, 10), viewModel.userFiltersFlow.value.selectedDate)
        assertTrue((viewModel.uiState.value as SportSignUiState.Content).displayedLessons.isEmpty())
        assertEquals(hidden, viewModel.linkedLesson(5))
        assertNull(viewModel.linkedLesson(1))
    }

    @Test
    fun `an ended or missing shared lesson is unavailable`() = runTest(mainDispatcherRule.dispatcher) {
        emitCatalog(lessonOn(3, day = 8, hour = 9))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.openSharedLesson(3)
        advanceUntilIdle()
        assertEquals(SportSignEvent.ShowLinkUnavailable, viewModel.events.first())

        viewModel.openSharedLesson(404)
        advanceUntilIdle()
        assertEquals(SportSignEvent.ShowLinkUnavailable, viewModel.events.first())
        assertEquals(LocalDate.of(2026, 9, 8), viewModel.userFiltersFlow.value.selectedDate)
    }

    @Test
    fun `a failed catalog reports the error`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = viewModel()
        schedule.schedule.emit(MergedDataState.Error(AppError.Network))
        advanceUntilIdle()

        viewModel.openSharedLesson(1)
        advanceUntilIdle()

        assertEquals(SportSignEvent.ShowError(AppError.Network), viewModel.events.first())
    }

    @Test
    fun `a request before the catalog answers runs once it does`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = viewModel()
        viewModel.openSharedLesson(1)
        advanceUntilIdle()

        emitCatalog(SportCardFixtures.lesson(1))
        advanceUntilIdle()

        assertEquals(SportSignEvent.OpenLessonDetails(SportCardFixtures.lesson(1)), viewModel.events.first())
    }

    @Test
    fun `a predicted link opens the prediction and a real link with the same id opens the prototype`() =
        runTest(mainDispatcherRule.dispatcher) {
            val prototype = lessonOn(7, day = 9)
            emitCatalog(prototype, prototype.predicted())
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.openSharedLesson(7, predicted = true)
            advanceUntilIdle()
            assertEquals(SportSignEvent.OpenLessonDetails(prototype.predicted()), viewModel.events.first())
            assertEquals(LocalDate.of(2026, 9, 23), viewModel.userFiltersFlow.value.selectedDate)
            assertEquals(prototype.predicted(), viewModel.linkedLesson(7))

            viewModel.openSharedLesson(7)
            advanceUntilIdle()
            assertEquals(SportSignEvent.OpenLessonDetails(prototype), viewModel.events.first())
        }

    @Test
    fun `a predicted link opens the real repeat once it is in the catalog`() = runTest(mainDispatcherRule.dispatcher) {
        val prototype = lessonOn(7, day = 9)
        val repeat = lessonOn(8, day = 23)
        val otherTeacher = lessonOn(9, day = 23).copy(teacherIsu = 200)
        emitCatalog(prototype, otherTeacher, repeat)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.openSharedLesson(7, predicted = true)
        advanceUntilIdle()

        assertEquals(SportSignEvent.OpenLessonDetails(repeat), viewModel.events.first())
    }

    @Test
    fun `a predicted link whose prototype left the catalog is unavailable`() = runTest(mainDispatcherRule.dispatcher) {
        emitCatalog(lessonOn(8, day = 23))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.openSharedLesson(7, predicted = true)
        advanceUntilIdle()

        assertEquals(SportSignEvent.ShowLinkUnavailable, viewModel.events.first())
    }
}
