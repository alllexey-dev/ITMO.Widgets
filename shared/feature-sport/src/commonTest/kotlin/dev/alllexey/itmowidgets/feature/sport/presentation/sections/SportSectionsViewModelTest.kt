package dev.alllexey.itmowidgets.feature.sport.presentation.sections

import dev.alllexey.itmowidgets.core.lesson.DayLesson
import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.lesson.LessonTarget
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.feature.sport.cards.SportCardFixtures
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignLimits
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeDayLessonsGateway
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeRecurringQueue
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportActionRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportDataRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportScheduleRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportSignPreferences
import dev.alllexey.itmowidgets.feature.sport.presentation.bookingDelegate
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportAutoSignFlow
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportRowAction
import dev.alllexey.itmowidgets.shared.feature.sport.SportStrings
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class SportSectionsViewModelTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUpMain() = main.install()

    @AfterTest
    fun tearDownMain() = main.reset()

    private val schedule = FakeSportScheduleRepository()
    private val data = FakeSportDataRepository()
    private val actions = FakeSportActionRepository()
    private val swimming = SectionName("Фитнес (функциональная тренировка)")
    private val preferences = FakeSportSignPreferences(setOf(swimming))
    private val timetable = FakeDayLessonsGateway()

    /** Monday, the day before the fixture lesson. */
    private val time = FixedAcademicTime(LocalDateTime(2026, 9, 7, 12, 0))

    private fun TestScope.viewModel(recurring: FakeRecurringQueue = FakeRecurringQueue(available = false)): SportSectionsViewModel {
        val delegate = bookingDelegate(FakeSportBookingRepository(), schedule, data, this, actions)
        return SportSectionsViewModel(schedule, data, preferences, recurring, timetable, time, delegate, SportAutoSignFlow(delegate, time))
    }

    private fun SportSectionsViewModel.cards() = (uiState.value.body as SportSectionsBody.Content).sections

    private suspend fun emitCatalog(vararg lessons: SportLesson) = schedule.schedule.emit(LoadState.Content(lessons.toList()))

    private fun weekLater(id: Long, weeks: Int) =
        SportCardFixtures.lesson(id).let { it.copy(start = it.start + (weeks * 7).days, end = it.end + (weeks * 7).days) }

    @Test
    fun nothingPinnedOffersThePickerOfEverySection() = runTest(main.dispatcher) {
        preferences.pinned.value = emptySet()
        emitCatalog(SportCardFixtures.lesson(1))
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(SportSectionsBody.Empty, viewModel.uiState.value.body)

        viewModel.openPicker()
        advanceUntilIdle()
        val option = viewModel.uiState.value.picker!!.single()
        assertFalse(option.selected)

        viewModel.togglePinned(option.value!!)
        advanceUntilIdle()
        assertEquals(setOf(swimming), preferences.pinned.value)
        assertTrue(viewModel.uiState.value.picker!!.single().selected)
    }

    @Test
    fun aPinnedSectionShowsItsNextLessonsWithTheirActionsAndTheLimitInTheSubtitle() = runTest(main.dispatcher) {
        emitCatalog(SportCardFixtures.lesson(1), weekLater(2, 1), weekLater(3, 2), weekLater(4, 3))
        val viewModel = viewModel()
        advanceUntilIdle()

        val card = viewModel.cards().single()
        assertEquals(listOf(1L, 2L, 3L), card.lessons.map { it.lessonId }, "the next three")
        assertEquals(UiText.Res(SportStrings.sport_day_tomorrow, listOf("8 сент.")), card.lessons[0].day)
        assertEquals(SportRowAction.Sign(busy = false), card.lessons[0].action)
        assertEquals(UiText.Res(SportStrings.sport_sections_limit, listOf(1, 3)), viewModel.uiState.value.subtitle)
        assertNull(card.repeat, "the repeat waits for Backend")
        assertNull((viewModel.uiState.value.body as SportSectionsBody.Content).note)
    }

    @Test
    fun theRepeatFollowsTheNextSlotEveryTwoWeeksAndStopsBeforeAClash() = runTest(main.dispatcher) {
        val recurring = FakeRecurringQueue()
        // The lesson in six weeks clashes with Физика.
        timetable.days.value = listOf(
            LessonDay(
                LocalDate(2026, 10, 20),
                listOf(
                    DayLesson(
                        LocalTime(18, 0), LocalTime(19, 30), "Физика", 3, null, null, null, online = false,
                        target = LessonTarget.PendingSport(PendingSportDetailsArgs(0, "", false, false, "", "", "", "")),
                    ),
                ),
                cachedAt = null,
            ),
        )
        emitCatalog(SportCardFixtures.lesson(1))
        val viewModel = viewModel(recurring)
        advanceUntilIdle()

        val off = viewModel.cards().single().repeat!!
        assertFalse(off.on)
        assertTrue(off.enabled)
        assertEquals(emptyList(), off.dates)

        viewModel.toggleRepeat(swimming.raw)
        advanceUntilIdle()
        val rule = recurring.rules.value.single()
        assertEquals(1L, rule.prototypeLessonId)
        assertEquals(LocalTime(18, 30), rule.start)

        val on = viewModel.cards().single().repeat!!
        assertTrue(on.on)
        assertEquals(listOf(UiText.Dynamic("вт, 22 сент."), UiText.Dynamic("вт, 6 окт.")), on.dates)
        assertEquals(UiText.Res(SportStrings.sport_repeat_clash_until, listOf("20 октября", "Физика", "6 октября")), on.clash)
        assertNotNull((viewModel.uiState.value.body as SportSectionsBody.Content).note)

        viewModel.toggleRepeat(swimming.raw)
        advanceUntilIdle()
        assertEquals(emptyList(), recurring.rules.value)
    }

    @Test
    fun aSpentLimitGatesANewRepeat() = runTest(main.dispatcher) {
        val recurring = FakeRecurringQueue()
        data.limits = SportAutoSignLimits(limit = 3, available = 0, nextAvailableAt = Instant.parse("2026-10-01T09:00:00+03:00"))
        emitCatalog(SportCardFixtures.lesson(1))
        val viewModel = viewModel(recurring)
        advanceUntilIdle()

        assertEquals(UiText.Res(SportStrings.sport_sections_limit_reason, listOf(3, "1 октября")), viewModel.uiState.value.limitGate?.reason)
        assertFalse(viewModel.cards().single().repeat!!.enabled)
    }

    @Test
    fun aRepeatBackendRefusedSaysWhy() = runTest(main.dispatcher) {
        val recurring = FakeRecurringQueue().apply { result = AppResult.Failure(AppError.Network) }
        emitCatalog(SportCardFixtures.lesson(1))
        val viewModel = viewModel(recurring)
        advanceUntilIdle()

        viewModel.toggleRepeat(swimming.raw)
        advanceUntilIdle()
        assertEquals(AppError.Network.toUiText(), viewModel.events.first())
        assertFalse(viewModel.cards().single().repeat!!.on)
    }

    @Test
    fun aRowActionSignsInAndAnUnpinnedSectionLeaves() = runTest(main.dispatcher) {
        emitCatalog(SportCardFixtures.lesson(1))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.act(1)
        advanceUntilIdle()
        assertEquals(listOf(1L), actions.signedInLessons)
        assertEquals(1L, viewModel.details(1)?.lessonId)

        viewModel.togglePinned(swimming.raw)
        advanceUntilIdle()
        assertEquals(SportSectionsBody.Empty, viewModel.uiState.value.body)
    }
}
