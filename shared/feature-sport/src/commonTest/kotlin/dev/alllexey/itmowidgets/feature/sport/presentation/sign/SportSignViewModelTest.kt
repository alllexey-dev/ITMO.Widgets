package dev.alllexey.itmowidgets.feature.sport.presentation.sign

import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.feature.sport.domain.model.FriendSportBooking
import dev.alllexey.itmowidgets.core.lesson.DayLesson
import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.lesson.LessonTarget
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.feature.sport.cards.SportCardFixtures
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAttendance
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignLimits
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFilterOption
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.domain.model.UnavailableReason
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeDayLessonsGateway
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportActionRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportDataRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportScheduleRepository
import dev.alllexey.itmowidgets.feature.sport.domain.model.MySportSection
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportSectionRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportSignPreferences
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportSectionsHolder
import dev.alllexey.itmowidgets.feature.sport.presentation.bookingDelegate
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportPrompt
import dev.alllexey.itmowidgets.feature.sport.presentation.emptyCatalog
import dev.alllexey.itmowidgets.shared.feature.sport.SportStrings
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class SportSignViewModelTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUpMain() = main.install()

    @AfterTest
    fun tearDownMain() = main.reset()

    private val schedule = FakeSportScheduleRepository()
    private val data = FakeSportDataRepository()
    private val actions = FakeSportActionRepository()
    private val preferences = FakeSportSignPreferences()
    private val sections = FakeSportSectionRepository()
    private val timetable = FakeDayLessonsGateway()

    /** Tuesday, 8 September, at noon: the fixture lesson is tonight at 18:30. */
    private val time = FixedAcademicTime(LocalDateTime(2026, 9, 8, 12, 0))

    private fun TestScope.viewModel(): SportSignViewModel {
        val delegate = bookingDelegate(FakeSportBookingRepository(), schedule, data, this, actions)
        return SportSignViewModel(
            schedule, data, preferences, SportSectionsHolder(sections, backgroundScope), timetable,
            SportSharedLessonResolver(schedule, time), time,
            delegate, SportAutoSignFlow(delegate, time),
        )
    }

    private fun SportSignViewModel.day() = uiState.value.body as SportSignBody.Day

    private fun SportSignViewModel.selectedDate(): LocalDate = uiState.value.weeks.flatten().single { it.selected }.date

    private suspend fun emitCatalog(vararg lessons: SportLesson) {
        schedule.filters.emit(AppResult.Success(emptyCatalog()))
        schedule.timeSlots.emit(AppResult.Success(emptyList()))
        schedule.schedule.emit(LoadState.Content(lessons.toList()))
    }

    private fun lessonOn(id: Long, day: Int, hour: Int = 18) = SportCardFixtures.lesson(id).let { lesson ->
        val start = Instant.parse("2026-09-${day.twoDigits()}T${hour.twoDigits()}:30:00+03:00")
        lesson.copy(start = start, end = start + 90.minutes)
    }

    private fun SportLesson.predicted() = copy(isLessonReal = false, start = start + 14.days, end = end + 14.days)

    private fun SportLesson.full() = copy(available = 0, canSignIn = false, unavailableReasons = listOf(UnavailableReason.Full))

    @Test
    fun theChosenDayListsItsLessonsAsRowsWithOneAction() = runTest(main.dispatcher) {
        emitCatalog(SportCardFixtures.lesson(1), lessonOn(2, day = 8, hour = 20).full(), lessonOn(3, day = 9))
        val viewModel = viewModel()
        advanceUntilIdle()

        val day = viewModel.day()
        assertEquals(UiText.Dynamic("Вторник, 8 сентября"), day.title)
        assertEquals(listOf(1L, 2L), day.rows.map { it.lessonId })
        assertEquals(SportRowAction.Sign(busy = false), day.rows[0].action)
        assertEquals(SportRowAction.Queue(busy = false), day.rows[1].action)
        assertEquals(UiText.Res(SportStrings.sport_occupancy, listOf(13, 20)), day.rows[0].occupancy?.text)
        assertEquals(2, viewModel.uiState.value.weeks.size, "the current week and the next one at least")
        assertEquals(1, viewModel.uiState.value.weeks[0].single { it.date == LocalDate(2026, 9, 9) }.lessons)
    }

    @Test
    fun catalogRowsKeepTheFriendsIdentityAndPhoto() = runTest(main.dispatcher) {
        val friend = UserSummary(900001, "Тестовый Друг", "https://example.invalid/photo.jpg", emptyList(), UserSharing(true, true))
        emitCatalog(SportCardFixtures.lesson(1).copy(friendsBookings = listOf(FriendSportBooking(friend, 1, null))))
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(listOf(friend), viewModel.day().rows.single().friends)
    }

    @Test
    fun theSearchAndTheChipsNarrowTheDayAndSayWhatTheyHid() = runTest(main.dispatcher) {
        val volleyball = lessonOn(2, day = 8, hour = 20).copy(sectionName = SectionName("Волейбол"))
        emitCatalog(SportCardFixtures.lesson(1), volleyball, lessonOn(3, day = 8, hour = 9).copy(start = Instant.parse("2026-09-08T13:00:00+03:00"), end = Instant.parse("2026-09-08T14:30:00+03:00")))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.setQuery("волей")
        advanceUntilIdle()
        assertEquals(listOf(2L), viewModel.day().rows.map { it.lessonId })
        assertEquals(UiText.Plural(dev.alllexey.itmowidgets.shared.feature.sport.SportPlurals.sport_filters_hidden, 2, listOf(2)), viewModel.day().hidden)

        viewModel.setQuery("")
        viewModel.toggle(SportQuickFilter.EVENING)
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L), viewModel.day().rows.map { it.lessonId })
        assertEquals(1, viewModel.uiState.value.filterCount)
        assertTrue(viewModel.uiState.value.chips.single { it.filter == SportQuickFilter.EVENING }.selected)

        viewModel.resetFilters()
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.filterCount)
        assertEquals(3, viewModel.day().rows.size)
    }

    @Test
    fun theStripCoversThePublishedWeeksWithTheirDotsAndThePredictionsAfterThem() = runTest(main.dispatcher) {
        emitCatalog(SportCardFixtures.lesson(1), lessonOn(2, day = 17), lessonOn(3, day = 18))
        val viewModel = viewModel()
        advanceUntilIdle()

        val weeks = viewModel.uiState.value.weeks
        assertEquals(2, weeks.size)
        assertEquals(LocalDate(2026, 9, 7), weeks[0].first().date)
        assertEquals(LocalDate(2026, 9, 20), weeks[1].last().date)
        assertEquals(listOf(0, 0, 0, 1, 1, 0, 0), weeks[1].map { it.lessons })

        emitCatalog(SportCardFixtures.lesson(1), lessonOn(2, day = 17), lessonOn(3, day = 10).predicted())
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.weeks.size, "a queue's prediction two weeks on is on the strip too")
    }

    @Test
    fun theQuickChipsAreFreeMorningEveningFriends() = runTest(main.dispatcher) {
        emitCatalog(SportCardFixtures.lesson(1))
        val lesson = sections.queueLesson(1).copy(sectionName = SportCardFixtures.lesson(1).sectionName.raw)
        sections.sections.value = LoadState.Content(listOf(MySportSection(1, lesson, emptyList(), place = 1, subscribers = 1)))
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(
            listOf(SportQuickFilter.FREE, SportQuickFilter.MORNING, SportQuickFilter.EVENING, SportQuickFilter.FRIENDS),
            viewModel.uiState.value.chips.map { it.filter },
        )
    }

    @Test
    fun everyBuildingWithLessonsIsAChoiceWithTheOthersAndOnlineAtTheEnd() = runTest(main.dispatcher) {
        val lomonosova = lessonOn(1, day = 8, hour = 15).copy(buildingId = 273)
        val kronverksky = lessonOn(2, day = 8, hour = 16).copy(buildingId = 13)
        val yunost = lessonOn(3, day = 8, hour = 17).copy(buildingId = 0)
        val online = lessonOn(4, day = 8, hour = 19).copy(buildingId = -1, roomId = -1)
        schedule.filters.emit(
            AppResult.Success(emptyCatalog().copy(buildings = listOf(SportFilterOption(-1, "Online"), SportFilterOption(0, "Другие объекты"), SportFilterOption(273, "ул.Ломоносова, д.9")))),
        )
        schedule.timeSlots.emit(AppResult.Success(emptyList()))
        schedule.schedule.emit(LoadState.Content(listOf(lomonosova, kronverksky, yunost, online)))
        val viewModel = viewModel()
        viewModel.openFilters()
        advanceUntilIdle()

        val buildings = viewModel.uiState.value.filters!!.buildings
        assertEquals(listOf(null, "ул.Ломоносова, д.9", "#other", "#online"), buildings.map { it.value })
        assertEquals(UiText.Res(SportStrings.sport_building_other), buildings[2].label)
        assertEquals(UiText.Res(SportStrings.sport_building_online), buildings[3].label)

        viewModel.selectBuilding(buildings[2].value)
        advanceUntilIdle()
        assertEquals(listOf(2L, 3L), viewModel.day().rows.map { it.lessonId })
        viewModel.selectBuilding(buildings[3].value)
        advanceUntilIdle()
        assertEquals(listOf(4L), viewModel.day().rows.map { it.lessonId })
    }

    @Test
    fun theTypesShowTheOwnThenThePopularAndTheRestBehindMoreWithASearch() = runTest(main.dispatcher) {
        // Eight types, the more places taken the later in the alphabet; one booked, one attended.
        val names = listOf("Бадминтон", "Волейбол", "Йога", "Плавание", "Теннис", "Фитнес", "Футбол", "Шахматы")
        val lessons = names.mapIndexed { index, name ->
            lessonOn(index + 10L, day = 9).copy(sectionName = SectionName(name), limit = 30, available = 30 - index)
        }
        schedule.filters.emit(AppResult.Success(emptyCatalog()))
        schedule.timeSlots.emit(AppResult.Success(emptyList()))
        schedule.schedule.emit(LoadState.Content(lessons.mapIndexed { index, lesson -> if (index == 1) lesson.copy(signed = true) else lesson }))
        data.score.emit(LoadState.Content(SportScore(10, 0, listOf(attendance("Йога"), attendance("Йога"), attendance("Упражнение")))))
        val viewModel = viewModel()
        viewModel.openFilters()
        advanceUntilIdle()

        val sheet = viewModel.uiState.value.filters!!
        assertEquals(listOf("Йога", "Волейбол", "Шахматы", "Футбол", "Фитнес", "Теннис"), sheet.sections.map { it.value })
        assertEquals(UiText.Res(SportStrings.sport_filter_more, listOf(2)), sheet.moreSections)
        assertNull(sheet.sectionQuery)

        viewModel.toggleSection("Бадминтон")
        advanceUntilIdle()
        assertEquals("Бадминтон", viewModel.uiState.value.filters!!.sections.last().value, "a chosen type stays visible")
        assertEquals(UiText.Res(SportStrings.sport_filter_more, listOf(1)), viewModel.uiState.value.filters!!.moreSections)

        viewModel.showAllSections()
        advanceUntilIdle()
        assertEquals(names, viewModel.uiState.value.filters!!.sections.map { it.value })
        assertNull(viewModel.uiState.value.filters!!.moreSections)
        viewModel.setSectionQuery("фу")
        advanceUntilIdle()
        assertEquals(listOf("Футбол"), viewModel.uiState.value.filters!!.sections.map { it.value })

        viewModel.closeFilters()
        viewModel.openFilters()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.filters!!.sectionQuery, "the sheet opens with the top types again")
    }

    private fun attendance(section: String) = SportAttendance(
        type = "lesson", name = SectionName(section), evaluationId = 1, evaluationName = null, sectionLevel = 1, score = 10,
        dateTime = Instant.parse("2026-09-01T18:30:00+03:00"), isCompetition = false,
    )

    @Test
    fun anEmptyDayLeadsToTheNextOneAndAFilteredOneToTheReset() = runTest(main.dispatcher) {
        emitCatalog(lessonOn(4, day = 10))
        val viewModel = viewModel()
        advanceUntilIdle()

        val empty = viewModel.day().empty!!
        assertFalse(empty.filtered)
        assertEquals(SportEmptyAction.ShowDay(LocalDate(2026, 9, 10), UiText.Res(SportStrings.sport_day_show)), empty.action)
        viewModel.onEmptyAction(empty.action!!)
        advanceUntilIdle()
        assertEquals(LocalDate(2026, 9, 10), viewModel.selectedDate())

        viewModel.toggle(SportQuickFilter.MORNING)
        advanceUntilIdle()
        val filtered = viewModel.day().empty!!
        assertTrue(filtered.filtered)
        assertIs<SportEmptyAction.ResetFilters>(filtered.action)
    }

    @Test
    fun aSwipeToAnotherWeekSelectsItsMondayAndBackToTheCurrentOneToday() = runTest(main.dispatcher) {
        emitCatalog(SportCardFixtures.lesson(1).predicted())
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.showWeek(5)
        advanceUntilIdle()
        assertEquals(LocalDate(2026, 9, 21), viewModel.selectedDate(), "the strip ends at the last lesson's week")
        assertEquals(2, viewModel.uiState.value.selectedWeek)

        viewModel.showWeek(0)
        advanceUntilIdle()
        assertEquals(LocalDate(2026, 9, 8), viewModel.selectedDate())
    }

    @Test
    fun aClashNamesTheLessonFromTheTimetable() = runTest(main.dispatcher) {
        timetable.days.value = listOf(
            LessonDay(
                LocalDate(2026, 9, 8),
                listOf(
                    DayLesson(
                        LocalTime(18, 0), LocalTime(19, 30), "Алгоритмы", 3, null, "1506", null, online = false,
                        target = LessonTarget.PendingSport(PendingSportDetailsArgs(0, "", false, false, "", "", "", "")),
                    ),
                ),
                cachedAt = null,
            ),
        )
        emitCatalog(SportCardFixtures.lesson(1).copy(intersection = true), lessonOn(2, day = 8, hour = 20).copy(intersection = true))
        val viewModel = viewModel()
        advanceUntilIdle()

        val rows = viewModel.day().rows
        assertEquals(UiText.Res(SportStrings.sport_row_clash, listOf("Алгоритмы")), rows[0].clash)
        assertEquals(UiText.Res(SportStrings.sport_row_clash_unknown), rows[1].clash)
        assertEquals("Алгоритмы", viewModel.details(1)?.clashSubject)
        assertEquals("18:00", viewModel.details(1)?.clashStart)
        assertEquals(SportRowAction.Sign(busy = false), rows[0].action, "a clash does not block")
    }

    @Test
    fun aRefreshKeepsTheDayOnScreenAndOnlyMarksItRefreshing() = runTest(main.dispatcher) {
        emitCatalog(SportCardFixtures.lesson(1))
        val viewModel = viewModel()
        advanceUntilIdle()
        val before = viewModel.day()
        assertFalse(before.refreshing)

        schedule.gate = CompletableDeferred()
        viewModel.refresh(RefreshMode.Pull)
        runCurrent()
        assertTrue(viewModel.day().refreshing)
        assertEquals(before.rows, viewModel.day().rows)

        schedule.gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.day().refreshing)
    }

    @Test
    fun beforeTheCatalogAnswersTheStripShowsOverALoadingDay() = runTest(main.dispatcher) {
        schedule.gate = CompletableDeferred()
        val viewModel = viewModel()
        runCurrent()
        assertEquals(SportSignBody.Loading, viewModel.uiState.value.body)
        assertEquals(2, viewModel.uiState.value.weeks.size, "this week and the next")
        schedule.gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun aFailedCatalogBeforeAnyContentIsAnErrorAndAfterItAPartialOne() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        schedule.schedule.emit(LoadState.Error(AppError.Network))
        advanceUntilIdle()
        assertEquals(SportSignBody.Failed(AppError.Network), viewModel.uiState.value.body)

        emitCatalog(SportCardFixtures.lesson(1))
        advanceUntilIdle()
        schedule.schedule.emit(LoadState.Error(AppError.Network))
        advanceUntilIdle()
        assertTrue(viewModel.day().partialError)
        assertEquals(listOf(1L), viewModel.day().rows.map { it.lessonId })
    }

    @Test
    fun aSecondTapOnALessonInFlightSendsNothingAndTheRowIsBusyUntilTheAnswer() = runTest(main.dispatcher) {
        emitCatalog(SportCardFixtures.lesson(1), lessonOn(2, day = 8, hour = 20))
        val viewModel = viewModel()
        advanceUntilIdle()
        actions.gate = CompletableDeferred()

        viewModel.act(1)
        viewModel.act(1)
        viewModel.act(2)
        runCurrent()
        assertEquals(listOf(1L, 2L), actions.signedInLessons)
        assertEquals(SportRowAction.Sign(busy = true), viewModel.day().rows.first { it.lessonId == 1L }.action)
        assertTrue(viewModel.isBusy(1))

        actions.gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.isBusy(1))
        assertEquals(SportSignEvent.Notice(UiText.Res(SportStrings.sport_sign_success)), viewModel.events.first())
    }

    @Test
    fun aFailedSignInSaysWhyAndFreesTheLesson() = runTest(main.dispatcher) {
        emitCatalog(SportCardFixtures.lesson(1))
        val viewModel = viewModel()
        advanceUntilIdle()
        actions.result = AppResult.Failure(AppError.Network)

        viewModel.act(1)
        advanceUntilIdle()

        assertFalse(viewModel.isBusy(1))
        assertEquals(SportSignEvent.Notice(AppError.Network.toUiText()), viewModel.events.first())
    }

    @Test
    fun aFullLessonAsksToJoinTheQueueAndTheLateSwitchReachesBackendOnce() = runTest(main.dispatcher) {
        emitCatalog(SportCardFixtures.lesson(1).full())
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.act(1)
        advanceUntilIdle()
        val join = assertIs<SportPrompt.Join>(viewModel.uiState.value.prompt)
        assertTrue(join.lateSign)
        assertEquals(UiText.Res(SportStrings.sport_join_full_step_3, listOf("17:30")), join.steps[2])
        assertNull(join.limit, "a free queue spends no limit")

        viewModel.confirmPrompt(lateSign = true)
        viewModel.confirmPrompt(lateSign = true)
        advanceUntilIdle()
        assertEquals(listOf(1L to true), actions.freeSignRequests)
        assertNull(viewModel.uiState.value.prompt)
    }

    @Test
    fun aPredictedLessonSaysHowManyQueuesAreLeftOrWhenTheLimitFreesUp() = runTest(main.dispatcher) {
        val predicted = lessonOn(7, day = 9).predicted()
        emitCatalog(predicted)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSheetAction(7, SportBookingAction.AUTO)
        advanceUntilIdle()
        val join = assertIs<SportPrompt.Join>(viewModel.uiState.value.prompt)
        assertFalse(join.lateSign)
        assertEquals(UiText.Res(SportStrings.sport_queue_limit_left, listOf(2, 3)), join.limit)
        viewModel.dismissPrompt()

        // What java.time writes on a "ru" device (`SportAutoSignLegacyDateTest` pins it on the JVM).
        data.limits = SportAutoSignLimits(limit = 2, available = 0, nextAvailableAt = Instant.parse("2026-09-12T09:00:00+03:00"))
        viewModel.onSheetAction(7, SportBookingAction.AUTO)
        advanceUntilIdle()
        assertEquals(
            SportPrompt.Info(UiText.Res(SportStrings.sport_auto_sign_limit_reached, listOf("12 сент. 2026 г., 09:00:00"))),
            viewModel.uiState.value.prompt,
        )
    }

    @Test
    fun aSheetActionTheLessonNoLongerOffersSaysItIsUnavailable() = runTest(main.dispatcher) {
        emitCatalog(SportCardFixtures.lesson(1))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSheetAction(1, SportBookingAction.AUTO)
        advanceUntilIdle()

        assertEquals(SportSignEvent.Notice(UiText.Res(SportStrings.sport_lesson_gone)), viewModel.events.first())
        assertEquals(emptyList(), actions.signedInLessons)
    }

    @Test
    fun aSharedLessonHiddenByTheFiltersSelectsItsDayAndOpensItsSheet() = runTest(main.dispatcher) {
        val hidden = lessonOn(5, day = 10).copy(available = 0, canSignIn = false, unavailableReasons = listOf(UnavailableReason.DailyLimitReached))
        emitCatalog(SportCardFixtures.lesson(1), hidden)
        val viewModel = viewModel()
        viewModel.toggle(SportQuickFilter.FREE)
        advanceUntilIdle()

        viewModel.openSharedLesson(5, predicted = false)
        advanceUntilIdle()

        assertEquals(SportSignEvent.OpenLesson(5), viewModel.events.first())
        assertEquals(LocalDate(2026, 9, 10), viewModel.selectedDate())
        assertTrue(viewModel.day().rows.isEmpty())
        assertEquals(5L, viewModel.details(5)?.lessonId)
    }

    @Test
    fun anEndedOrMissingSharedLessonIsUnavailable() = runTest(main.dispatcher) {
        emitCatalog(lessonOn(3, day = 8, hour = 9))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.openSharedLesson(3, predicted = false)
        advanceUntilIdle()
        val unavailable = SportSignEvent.Notice(UiText.Res(SportStrings.sport_link_unavailable_text))
        assertEquals(unavailable, viewModel.events.first())

        viewModel.openSharedLesson(404, predicted = false)
        advanceUntilIdle()
        assertEquals(unavailable, viewModel.events.first())
    }

    @Test
    fun aPredictedLinkOpensThePrediction() = runTest(main.dispatcher) {
        val prototype = lessonOn(7, day = 9)
        emitCatalog(prototype, prototype.predicted())
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.openSharedLesson(7, predicted = true)
        advanceUntilIdle()
        assertEquals(SportSignEvent.OpenLesson(7), viewModel.events.first())
        assertEquals(LocalDate(2026, 9, 23), viewModel.selectedDate())
        assertFalse(viewModel.details(7)!!.isReal)
    }

    private fun Int.twoDigits(): String = toString().padStart(2, '0')
}
