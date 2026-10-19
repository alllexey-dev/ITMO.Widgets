package dev.alllexey.itmowidgets.feature.sport.presentation.my

import dev.alllexey.itmowidgets.core.lesson.LessonPillKind
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.result.valueOrNull
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.shortPersonName
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.feature.sport.cards.SportCardFixtures
import dev.alllexey.itmowidgets.feature.sport.domain.model.MySportSection
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAttempts
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntryStatus
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportSectionDate
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportSectionDateState
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportPlace
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportSectionSlot
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportActionRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportDataRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportScheduleRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportSectionRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.applicationScope
import dev.alllexey.itmowidgets.feature.sport.presentation.bookingDelegate
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingsHolder
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportSectionsHolder
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.sport.SportStrings
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class SportMyViewModelTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUpMain() = main.install()

    @AfterTest
    fun tearDownMain() = main.reset()

    private val bookings = FakeSportBookingRepository()
    private val data = FakeSportDataRepository()
    private val actions = FakeSportActionRepository()
    private val sections = FakeSportSectionRepository()

    /** Monday, the day before the fixture lesson, at noon. */
    private val time = FixedAcademicTime(LocalDateTime(2026, 9, 7, 12, 0))

    private fun TestScope.viewModel() = SportMyViewModel(
        bookings,
        data,
        SportBookingsHolder(
            bookings, data,
            bookingDelegate(bookings, FakeSportScheduleRepository(), data, backgroundScope, actions),
            time,
            applicationScope(),
        ),
        SportSectionsHolder(sections, data, applicationScope()),
        time,
    )

    private fun SportMyViewModel.content() = uiState.value.body as SportMyBody.Content

    private suspend fun emitSnapshot() {
        data.attempts.emit(LoadState.Content(SportAttempts(total = 3, used = 1, free = 2, canSignIn = true)))
        data.score.emit(LoadState.Content(SportScore(attendances = 40, other = 10, attendancesData = emptyList())))
        bookings.merged.emit(LoadState.Content(listOf(SportCardFixtures.booking(7))))
    }

    @Test
    fun theScoreIsARingWithItsFormulaAndWhatIsLeft() = runTest(main.dispatcher) {
        data.score.emit(LoadState.Content(SportScore(attendances = 40, other = 52, attendancesData = emptyList())))
        bookings.merged.emit(LoadState.Content(emptyList()))
        val viewModel = viewModel()
        advanceUntilIdle()

        val score = viewModel.uiState.value.score!!
        assertEquals(80, score.total, "the bonus counts up to 40")
        assertEquals(UiText.Res(SportStrings.sport_score_formula, listOf(40, 40)), score.formula)
        assertEquals(UiText.Res(SportStrings.sport_score_remaining, listOf(20)), score.status)
        assertEquals(0.4f, score.visitsShare)
        assertEquals(0.4f, score.bonusShare)
        assertEquals(emptyList(), viewModel.content().days, "no bookings: the empty state")
    }

    @Test
    fun bookingsAndQueuesAreLessonCardsByDayAndPastOnesAreGone() = runTest(main.dispatcher) {
        val later = SportCardFixtures.start + 2.days
        val queued = SportCardFixtures.booking(8).copy(signed = false, signEntry = SportCardFixtures.entry(), start = later, end = later + 1.days / 16)
        val past = SportCardFixtures.booking(9).copy(start = SportCardFixtures.start - 3.days, end = SportCardFixtures.start - 3.days)
        val expired = SportCardFixtures.booking(10).copy(signed = false, signEntry = SportCardFixtures.entry(SportQueueEntryStatus.EXPIRED))
        data.score.emit(LoadState.Content(SportScore(attendances = 40, other = 10, attendancesData = emptyList())))
        bookings.merged.emit(LoadState.Content(listOf(queued, past, SportCardFixtures.booking(7), expired)))
        val viewModel = viewModel()
        advanceUntilIdle()

        val days = viewModel.content().days
        assertEquals(
            listOf(UiText.Res(SportStrings.sport_day_tomorrow, listOf("8 сент.")), UiText.Dynamic("Чт, 10 сент.")),
            days.map { it.title },
        )
        val booked = days[0].bookings.single()
        assertEquals(7L, booked.lessonId)
        assertEquals(LessonPillKind.BOOKED, booked.lesson.pills.single().kind)
        assertEquals(UiText.Res(SportStrings.sport_queue_position, listOf(3, 12)), days[1].bookings.single().lesson.pills.single().text)
    }

    @Test
    fun aBookedLessonOffersToBecomeASectionAndThenShowsItsPlace() = runTest(main.dispatcher) {
        sections.slots[7] = SportSectionSlot(7, subscribers = 4, cap = 12, full = false, subscriptionId = null, place = null)
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()

        val offer = viewModel.sectionToggle(7)!!
        assertFalse(offer.on)
        assertTrue(offer.enabled)
        assertEquals(UiText.Res(SportStrings.sport_section_make), offer.label)
        assertEquals(UiText.Res(SportStrings.sport_section_chosen, listOf(4)), offer.detail)

        viewModel.toggleSection(7)
        advanceUntilIdle()

        assertEquals(listOf("create 7"), sections.requests)
        val on = viewModel.sectionToggle(7)!!
        assertTrue(on.on)
        assertEquals(UiText.Res(SportStrings.sport_section_place, listOf(5, 5)), on.detail)
        assertEquals(UiText.Res(SportStrings.sport_section_made), viewModel.events.first())
        assertEquals(listOf(107L), viewModel.uiState.value.sections.map { it.id })
    }

    @Test
    fun everySectionChangeThatWentThroughReloadsTheQueueEntriesAtOnce() = runTest(main.dispatcher) {
        sections.slots[7] = SportSectionSlot(7, subscribers = 4, cap = 12, full = false, subscriptionId = null, place = null)
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()
        val before = data.entriesRefreshCount

        viewModel.toggleSection(7)
        advanceUntilIdle()
        assertEquals(before + 1, data.entriesRefreshCount, "Backend queued the dates inside the request")

        val section = sections.sections.value.valueOrNull()!!.single()
        val start = section.lesson.start + 14.days
        sections.sections.value = LoadState.Content(listOf(section.copy(dates = listOf(SportSectionDate(start, start + 90.minutes, SportSectionDateState.QUEUED, 21L)))))
        advanceUntilIdle()
        viewModel.setSectionDate(section.id, 0, on = false)
        advanceUntilIdle()
        assertEquals(before + 2, data.entriesRefreshCount, "the skipped date's entry is cancelled")

        viewModel.stopSection(section.id)
        advanceUntilIdle()
        assertEquals(before + 3, data.entriesRefreshCount)

        sections.failure = AppError.Network
        viewModel.toggleSection(7)
        advanceUntilIdle()
        assertEquals(before + 3, data.entriesRefreshCount, "a refused change has nothing new to show")
    }

    @Test
    fun aFullSlotRefusesANewSectionAndAQueueHasNoSwitch() = runTest(main.dispatcher) {
        sections.slots[7] = SportSectionSlot(7, subscribers = 12, cap = 12, full = true, subscriptionId = null, place = null)
        val later = SportCardFixtures.start + 2.days
        val queued = SportCardFixtures.booking(8).copy(signed = false, signEntry = SportCardFixtures.entry(), start = later, end = later + 1.days / 16)
        data.score.emit(LoadState.Content(SportScore(attendances = 40, other = 10, attendancesData = emptyList())))
        bookings.merged.emit(LoadState.Content(listOf(SportCardFixtures.booking(7), queued)))
        val viewModel = viewModel()
        advanceUntilIdle()

        val full = viewModel.sectionToggle(7)!!
        assertFalse(full.enabled)
        assertEquals(UiText.Res(SportStrings.sport_section_full), full.detail)
        assertNull(viewModel.sectionToggle(8), "a queue is no booking")

        viewModel.toggleSection(7)
        advanceUntilIdle()
        assertEquals(emptyList(), sections.requests)
    }

    @Test
    fun aSectionListsItsSlotPlaceAndDatesEachWithItsSwitch() = runTest(main.dispatcher) {
        val lesson = sections.queueLesson(7).copy(roomName = "Кронверкский пр., 49, спортивный зал")
        val dates = listOf(SportSectionDateState.QUEUED, SportSectionDateState.LIMIT, null).mapIndexed { index, state ->
            val shift = 14.days * (index + 1)
            SportSectionDate(lesson.start + shift, lesson.end + shift, state, 21L.takeIf { state == SportSectionDateState.QUEUED })
        }
        sections.sections.value = LoadState.Content(listOf(MySportSection(31, lesson, dates, place = 3, subscribers = 7)))
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()

        val card = viewModel.uiState.value.sections.single()
        assertEquals("Волейбол", card.title)
        assertEquals(UiText.Res(SportStrings.sport_section_slot, listOf("Среда", "18:30")), card.slot)
        assertEquals(shortPersonName("Тихонова Марина Юрьевна"), card.teacher)
        assertEquals(SportPlace("спортивный зал", "Кронверкский пр., 49").short(), card.place, "the hall, then the building's short name")
        assertEquals(UiText.Res(SportStrings.sport_section_place, listOf(3, 7)), card.position)
        assertEquals(
            listOf(SportStrings.sport_queue_pill, SportStrings.sport_section_date_limit, SportStrings.sport_section_date_waiting)
                .map { UiText.Res(it) },
            card.dates.map { it.status },
        )
        assertEquals(listOf(SportSectionDateState.QUEUED, SportSectionDateState.LIMIT, null), card.dates.map { it.state })
        assertEquals(listOf(true, false, true), card.dates.map { it.on })
        assertEquals(listOf(true, false, true), card.dates.map { it.enabled }, "the limit skipped the second date")

        viewModel.setSectionDate(31, 0, on = false)
        advanceUntilIdle()
        assertEquals(SportStrings.sport_section_date_skipped, (viewModel.uiState.value.sections.single().dates[0].status as UiText.Res).key)

        viewModel.stopSection(31)
        advanceUntilIdle()
        assertEquals(listOf("date 31 ${dates[0].start} off", "stop 31"), sections.requests)
        assertEquals(emptyList(), viewModel.uiState.value.sections)
        assertEquals(UiText.Res(SportStrings.sport_section_stopped), viewModel.events.first())
    }

    @Test
    fun aQueuedDateShowsItsPlaceInTheLessonQueueAndARefreshMovesIt() = runTest(main.dispatcher) {
        val lesson = sections.queueLesson(7)
        fun section(position: Int, total: Int) = MySportSection(
            31, lesson,
            listOf(SportSectionDate(lesson.start + 14.days, lesson.end + 14.days, SportSectionDateState.QUEUED, 21, position, total)),
            place = 1, subscribers = 2,
        )
        sections.sections.value = LoadState.Content(listOf(section(1, 3)))
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(
            UiText.Res(SportStrings.sport_queue_position, listOf(1, 3)),
            viewModel.uiState.value.sections.single().dates.single().status,
        )

        val refreshes = sections.refreshCount
        viewModel.refresh(RefreshMode.Pull)
        advanceUntilIdle()
        assertTrue(sections.refreshCount > refreshes, "a pull reloads the sections")
        // A manual user joined: the date takes the next turn after them.
        sections.sections.value = LoadState.Content(listOf(section(2, 4)))
        advanceUntilIdle()
        assertEquals(
            UiText.Res(SportStrings.sport_queue_position, listOf(2, 4)),
            viewModel.uiState.value.sections.single().dates.single().status,
        )
    }

    @Test
    fun aRefusedSectionChangeSaysWhy() = runTest(main.dispatcher) {
        sections.slots[7] = SportSectionSlot(7, subscribers = 0, cap = 12, full = false, subscriptionId = null, place = null)
        sections.failure = AppError.CustomServicesDisabled
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.toggleSection(7)
        advanceUntilIdle()

        assertEquals(AppError.CustomServicesDisabled.toUiText(), viewModel.events.first())
        assertFalse(viewModel.sectionToggle(7)!!.on)
    }

    @Test
    fun aRefreshKeepsTheContentOnScreenAndOnlyMarksItRefreshing() = runTest(main.dispatcher) {
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()
        val before = viewModel.content()
        assertFalse(before.refreshing)

        bookings.gate = CompletableDeferred()
        viewModel.refresh(RefreshMode.Pull)
        runCurrent()
        assertTrue(viewModel.content().refreshing)
        assertEquals(before.days, viewModel.content().days)

        bookings.gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.content().refreshing)
    }

    @Test
    fun withoutASnapshotTheScreenStaysLoadingThroughTheRefresh() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        bookings.gate = CompletableDeferred()
        viewModel.ensureDataLoaded()
        runCurrent()
        assertEquals(SportMyBody.Loading, viewModel.uiState.value.body)
        bookings.gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(SportMyBody.Loading, viewModel.uiState.value.body)
    }

    @Test
    fun aFailedSourceAfterContentKeepsTheContentWithAPartialError() = runTest(main.dispatcher) {
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()
        val content = viewModel.content()

        bookings.merged.emit(LoadState.Error(AppError.Network))
        advanceUntilIdle()

        assertEquals(content.days, viewModel.content().days)
        assertTrue(viewModel.content().partialError)
    }

    @Test
    fun aClearedSessionDropsTheContentOfThePreviousOne() = runTest(main.dispatcher) {
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()

        data.score.emit(LoadState.Loading)
        bookings.merged.emit(LoadState.Content(emptyList()))
        advanceUntilIdle()

        assertEquals(SportMyBody.Loading, viewModel.uiState.value.body)
        assertNull(viewModel.uiState.value.score)
    }

    @Test
    fun aFailedSourceBeforeAnyContentIsAnError() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        data.score.emit(LoadState.Error(AppError.Network))
        bookings.merged.emit(LoadState.Error(AppError.Network))
        advanceUntilIdle()
        assertEquals(SportMyBody.Failed(AppError.Network), viewModel.uiState.value.body)
    }

    @Test
    fun aSecondEntryWithContentLoadedStartsNoRequest() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        bookings.gate = CompletableDeferred()
        viewModel.ensureDataLoaded()
        viewModel.ensureDataLoaded()
        runCurrent()
        assertEquals(1, bookings.refreshCount, "an entry while the first load runs joins it")

        emitSnapshot()
        bookings.gate.complete(Unit)
        advanceUntilIdle()
        viewModel.ensureDataLoaded()
        runCurrent()
        assertEquals(1, bookings.refreshCount)
    }

    @Test
    fun aCancellationTheSheetAskedForIsConfirmedFirstAndOnlyWhileOffered() = runTest(main.dispatcher) {
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSheetAction(7, SportBookingAction.SIGN)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.cancel, "a booking offers no sign-in")

        viewModel.onSheetAction(7, SportBookingAction.CANCEL)
        advanceUntilIdle()
        assertEquals(UiText.Res(CoreStrings.sport_cancel_booking_question), viewModel.uiState.value.cancel?.message)

        val refreshes = bookings.refreshCount
        viewModel.confirmCancel()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.cancel)
        assertTrue(bookings.refreshCount > refreshes, "the cancellation refreshed the bookings")
    }

    @Test
    fun aFailedCancellationWaitsForTheView() = runTest(main.dispatcher) {
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()
        actions.result = AppResult.Failure(AppError.Network)

        viewModel.onSheetAction(7, SportBookingAction.CANCEL)
        viewModel.confirmCancel()
        advanceUntilIdle()

        assertEquals(AppError.Network.toUiText(), viewModel.events.first())
    }

    @Test
    fun theSheetOfABookingIsItsSnapshot() = runTest(main.dispatcher) {
        emitSnapshot()
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(7L, viewModel.details(7)?.lessonId)
        assertNull(viewModel.details(99))
    }
}
