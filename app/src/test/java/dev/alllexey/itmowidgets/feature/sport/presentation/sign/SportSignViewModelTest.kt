package dev.alllexey.itmowidgets.feature.sport.presentation.sign

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignLimits
import dev.alllexey.itmowidgets.feature.sport.cards.SportCardFixtures
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.domain.model.UnavailableReason
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportActionRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportDataRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportScheduleRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportSignPreferences
import dev.alllexey.itmowidgets.feature.sport.presentation.bookingDelegate
import dev.alllexey.itmowidgets.feature.sport.presentation.emptyCatalog
import java.time.LocalDateTime
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
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.datetime.LocalDate
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SportSignViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val schedule = FakeSportScheduleRepository()
    private val data = FakeSportDataRepository()
    private val actions = FakeSportActionRepository()
    private val time = FixedAcademicTime(LocalDateTime.of(2026, 9, 8, 12, 0))

    private fun TestScope.viewModel(): SportSignViewModel {
        val delegate = bookingDelegate(FakeSportBookingRepository(), schedule, data, this, actions)
        return SportSignViewModel(
            schedule, data, SportSignFilterController(time), SportSignStateFactory(time), delegate,
            SportAutoSignFlow(delegate, time), SportSharedLessonResolver(schedule, time), FakeSportSignPreferences, time
        )
    }

    private fun SportSignViewModel.selectedDate(): LocalDate =
        (uiState.value as SportSignUiState.Content).displayedWeek.single { it.isSelected }.date

    private suspend fun emitSnapshot() {
        schedule.filters.emit(AppResult.Success(emptyCatalog()))
        schedule.timeSlots.emit(AppResult.Success(emptyList()))
        schedule.schedule.emit(LoadState.Content(listOf(SportCardFixtures.lesson(1))))
    }

    @Test
    fun `a refresh keeps the catalogue on screen and only marks it refreshing`() = runTest(mainDispatcherRule.dispatcher) {
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()
        val before = viewModel.uiState.value as SportSignUiState.Content
        assertFalse(before.refreshing)

        schedule.gate = CompletableDeferred()
        viewModel.refresh(RefreshMode.Pull)
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
        viewModel.refresh(RefreshMode.Pull)
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

        schedule.schedule.emit(LoadState.Error(AppError.Network))
        advanceUntilIdle()

        val after = viewModel.uiState.value as SportSignUiState.Content
        assertTrue(after.hasPartialError)
        assertFalse(after.refreshing)
    }

    @Test
    fun `a failed source before any content is an error`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = viewModel()
        schedule.filters.emit(AppResult.Failure(AppError.Network))
        schedule.timeSlots.emit(AppResult.Failure(AppError.Network))
        schedule.schedule.emit(LoadState.Error(AppError.Network))
        advanceUntilIdle()
        assertEquals(SportSignUiState.Error(AppError.Network), viewModel.uiState.value)
    }

    @Test
    fun `a forced retry replaces the silent entry refresh and shows the indicator`() = runTest(mainDispatcherRule.dispatcher) {
        emitSnapshot()
        schedule.gate = CompletableDeferred()
        val viewModel = viewModel()
        runCurrent()
        assertEquals(1, schedule.scheduleRefreshCount)
        assertFalse((viewModel.uiState.value as SportSignUiState.Content).refreshing)

        viewModel.refresh(RefreshMode.Pull)
        runCurrent()
        assertEquals("a pull joins the refresh in flight", 1, schedule.scheduleRefreshCount)
        assertTrue((viewModel.uiState.value as SportSignUiState.Content).refreshing)

        viewModel.refresh(RefreshMode.Force)
        runCurrent()
        assertEquals("a retry starts a fresh request", 2, schedule.scheduleRefreshCount)
        assertTrue((viewModel.uiState.value as SportSignUiState.Content).refreshing)

        schedule.gate.complete(Unit)
        advanceUntilIdle()
        assertFalse((viewModel.uiState.value as SportSignUiState.Content).refreshing)
    }

    @Test
    fun `a second tap on a lesson in flight sends nothing and the lesson is busy until the answer`() =
        runTest(mainDispatcherRule.dispatcher) {
            emitSnapshot()
            val viewModel = viewModel()
            advanceUntilIdle()
            actions.gate = CompletableDeferred()

            viewModel.signUpForLesson(SportCardFixtures.lesson(1))
            viewModel.signUpForLesson(SportCardFixtures.lesson(1))
            viewModel.signUpForLesson(SportCardFixtures.lesson(2))
            runCurrent()
            assertEquals(listOf(1L, 2L), actions.signedInLessons)
            assertEquals(setOf(1L, 2L), (viewModel.uiState.value as SportSignUiState.Content).busyLessonIds)

            actions.gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(emptySet<Long>(), (viewModel.uiState.value as SportSignUiState.Content).busyLessonIds)
            val toast = SportSignEvent.ShowToast(UiText.Resource(R.string.sport_sign_success))
            assertEquals(toast, viewModel.events.first())
            assertEquals(toast, viewModel.events.first())
        }

    @Test
    fun `a failed sign-in waits for the view and frees the lesson`() = runTest(mainDispatcherRule.dispatcher) {
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()
        actions.result = AppResult.Failure(AppError.Network)

        viewModel.signUpForLesson(SportCardFixtures.lesson(1))
        advanceUntilIdle()

        assertEquals(emptySet<Long>(), (viewModel.uiState.value as SportSignUiState.Content).busyLessonIds)
        assertEquals(SportSignEvent.ShowError(AppError.Network), viewModel.events.first())
        viewModel.signUpForLesson(SportCardFixtures.lesson(1))
        runCurrent()
        assertEquals(listOf(1L, 1L), actions.signedInLessons)
    }

    @Test
    fun `a confirmed free sign reaches Backend once with the force-sign switch`() = runTest(mainDispatcherRule.dispatcher) {
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.executeAutoSignCommand(SportSignCommand.CreateFreeSign(1), forceSign = true)
        viewModel.executeAutoSignCommand(SportSignCommand.CreateFreeSign(1), forceSign = true)
        advanceUntilIdle()

        assertEquals(listOf(1L to true), actions.freeSignRequests)
    }

    private suspend fun emitCatalog(vararg lessons: SportLesson) {
        schedule.filters.emit(AppResult.Success(emptyCatalog()))
        schedule.timeSlots.emit(AppResult.Success(emptyList()))
        schedule.schedule.emit(LoadState.Content(lessons.toList()))
    }

    private fun lessonOn(id: Long, day: Int, hour: Int = 18) = SportCardFixtures.lesson(id).let { lesson ->
        val start = Instant.parse("2026-09-%02dT%02d:30:00+03:00".format(day, hour))
        lesson.copy(start = start, end = start + 90.minutes)
    }

    private fun SportLesson.predicted() = copy(isLessonReal = false, start = start + 14.days, end = end + 14.days)

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
        assertEquals(LocalDate(2026, 9, 10), viewModel.selectedDate())
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
        assertEquals(LocalDate(2026, 9, 8), viewModel.selectedDate())
    }

    @Test
    fun `a failed catalog reports the error`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = viewModel()
        schedule.schedule.emit(LoadState.Error(AppError.Network))
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
            assertEquals(LocalDate(2026, 9, 23), viewModel.selectedDate())
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

    @Test
    fun `the auto-sign limit date reads as a Russian device showed it`() = runTest(mainDispatcherRule.dispatcher) {
        val nextAvailable = "2026-09-12T09:00+03:00"
        // What java.time wrote on a "ru" device before the port; CLDR 42+ puts U+202F before "г.".
        val legacy = OffsetDateTime.parse(nextAvailable)
            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.forLanguageTag("ru")))
            .replace('\u202F', ' ').replace('\u00A0', ' ')
        assertEquals("12 сент. 2026 г., 09:00:00", legacy)
        data.limits = SportAutoSignLimits(limit = 2, available = 0, nextAvailableAt = Instant.parse("2026-09-12T09:00:00+03:00"))
        emitCatalog()
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.handleAutoSignClick(lessonOn(7, day = 9).predicted())
        advanceUntilIdle()

        val expected = UiText.Resource(R.string.sport_auto_sign_limit_reached, listOf(legacy))
        assertEquals(SportSignEvent.ShowInfoDialog(message = expected), viewModel.events.first())
    }
}
