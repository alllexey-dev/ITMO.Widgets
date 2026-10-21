package dev.alllexey.itmowidgets.feature.schedule.presentation.lesson

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.lesson.LessonPillKind
import dev.alllexey.itmowidgets.core.lesson.LessonSubject
import dev.alllexey.itmowidgets.core.lesson.LessonSubjectSource
import dev.alllexey.itmowidgets.core.location.BuildingDirectory
import dev.alllexey.itmowidgets.core.location.KnownBuilding
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.CampusMapArgs
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.reviews.TeacherLevelsRepository
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField
import dev.alllexey.itmowidgets.core.testing.FakePersonPhotosRepository
import dev.alllexey.itmowidgets.core.testing.FakeCustomServicesRepository
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.scheduleChange
import dev.alllexey.itmowidgets.core.testing.slot
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.schedule.FakeScheduleChangesRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.LessonFriendsRepository
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.schedule.ScheduleStrings
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

class LessonSheetViewModelTest {

    private val main = TestMainDispatcher()
    private val date = LocalDate(2026, 9, 9)

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    private fun viewModel(
        route: dev.alllexey.itmowidgets.core.navigation.AppRoute,
        at: LocalTime = LocalTime(12, 0),
        services: Boolean = true,
        changes: FakeScheduleChangesRepository = FakeScheduleChangesRepository(),
        subjects: List<LessonSubjectSource> = emptyList(),
        photos: FakePersonPhotosRepository = FakePersonPhotosRepository(),
        friendsOnLessons: LessonFriendsRepository = OneFriendOnLessons,
    ) = LessonSheetViewModel(
        SavedStateHandle(LessonSheetArgs.of(route)),
        friendsOnLessons,
        FakeCustomServicesRepository(services),
        PositiveLevels,
        changes,
        subjects,
        BuildingDirectory(listOf(KRONVA)),
        FixedAcademicTime(LocalDateTime(date, at)),
        photos,
    )

    @Test
    fun aLessonGoingOnSaysHowLongIsLeftWithItsRoomAndTheBuildingsAddress() = runTest(main.dispatcher) {
        val state = viewModel(AppRoutes.LessonDetails(LESSON), at = LocalTime(10, 42)).uiState.first()

        assertEquals(LessonPillKind.NOW, state.status?.kind)
        assertEquals(UiText.Res(ScheduleStrings.schedule_sheet_now, listOf(UiText.Res(CoreStrings.duration_minutes, listOf(48)))), state.status?.text)
        assertEquals(UiText.Dynamic("1506"), state.room)
        assertEquals("Кронверкский пр., 49", state.address)
        assertEquals("ФИЗ 2.3", state.flow)
    }

    @Test
    fun theRelevantActionsOnlyAndTheTeachersToneInWords() = runTest(main.dispatcher) {
        val subjects = listOf(
            LessonSubjectSource { _, _, _ -> LessonSubject(AppRoutes.TabRoot(AppTab.RECORDBOOK), 3, AppRoutes.TabRoot(AppTab.RECORDBOOK)) },
        )
        val vm = viewModel(AppRoutes.LessonDetails(LESSON.copy(zoomUrl = "https://example.org/meet")), subjects = subjects)

        val state = vm.uiState.first { it.actions.size == 5 && it.teacher?.tone != UiText.Res(ScheduleStrings.schedule_sheet_teacher) }

        assertEquals(
            listOf(LessonActionKind.ROUTE, LessonActionKind.VIDEO, LessonActionKind.FRIENDS, LessonActionKind.LINKS, LessonActionKind.SUBJECT),
            state.actions.map { it.kind },
        )
        assertEquals(UiText.Res(ScheduleStrings.schedule_sheet_links, listOf(3)), state.actions[3].label)
        assertEquals(listOf(FRIEND), state.friends)
        assertEquals(TEACHER_ISU, state.teacher?.isu)
    }

    @Test
    fun withoutTheConnectionNoFriendsNoToneAndNoSubjectWithoutASource() = runTest(main.dispatcher) {
        val state = viewModel(AppRoutes.LessonDetails(LESSON), services = false).uiState.first { !it.actionsPending }

        // Only the route: it needs neither the connection nor a subject source.
        assertEquals(listOf(AppRoutes.CampusMap(CampusMapArgs(LESSON, route = true))), state.actions.map { it.route })
        assertEquals(UiText.Res(ScheduleStrings.schedule_sheet_teacher), state.teacher?.tone)
    }

    @Test
    fun aChangedLessonShowsWasAndIsPerField() = runTest(main.dispatcher) {
        val moved = scheduleChange(
            fields = setOf(ScheduleChangeField.PLACE),
            before = slot(LESSON.pairId, date, start = LocalTime(10, 0), room = "1506"),
            after = slot(LESSON.pairId, date, start = LocalTime(10, 0), room = "2202"),
        )
        val vm = viewModel(AppRoutes.LessonDetails(LESSON), changes = FakeScheduleChangesRepository(moved))

        val state = vm.uiState.first { it.changes != null }

        assertEquals(LessonPillKind.CHANGED, state.status?.kind)
        assertNull(state.changes?.summary)
        assertEquals(1, state.changes?.lines?.size)
        assertEquals(UiText.Res(CoreStrings.schedule_change_field_place), state.changes?.lines?.single()?.field)
    }

    @Test
    fun anOwnQueueIsARowToSportAndABookedSportLessonToo() = runTest(main.dispatcher) {
        val queue = viewModel(AppRoutes.PendingSportDetails(QUEUE)).uiState.first()
        assertEquals(LessonPillKind.QUEUE, queue.status?.kind)
        assertEquals(UiText.Res(ScheduleStrings.schedule_sheet_queue_waiting), queue.sport?.text)
        assertEquals("Плавание", queue.subject)

        val booked = viewModel(AppRoutes.LessonDetails(LESSON.copy(typeId = 11))).uiState.first()
        assertEquals(UiText.Res(ScheduleStrings.schedule_sheet_booked), booked.sport?.title)
    }

    @Test
    fun anOnlineLessonHasNoRoomAndOnlyItsMeeting() = runTest(main.dispatcher) {
        val online = LESSON.copy(format = "Дистанционный", room = "дистант", zoomUrl = "https://example.org/meet", zoomPassword = "1234")

        val state = viewModel(AppRoutes.LessonDetails(online), services = false).uiState.first { !it.actionsPending }

        assertTrue(state.online)
        assertNull(state.room)
        assertNull(state.address)
        assertEquals("1234", state.password)
        assertEquals(listOf(LessonActionKind.VIDEO), state.actions.map { it.kind })
    }

    @Test
    fun theTilesWaitForTheOptInAndTheSubjectAndFriendsKeepsItsPlaceWhileLoading() = runTest(main.dispatcher) {
        val subject = CompletableDeferred<LessonSubject?>()
        val friends = CompletableDeferred<List<UserSummary>>()
        val vm = viewModel(
            AppRoutes.LessonDetails(LESSON),
            subjects = listOf(LessonSubjectSource { _, _, _ -> subject.await() }),
            friendsOnLessons = object : LessonFriendsRepository {
                override suspend fun friendsOnLesson(pairId: Long, date: LocalDate) = AppResult.Success(friends.await())
            },
        )
        val states = mutableListOf<LessonSheetUiState>()
        backgroundScope.launch { vm.uiState.collect(states::add) }
        runCurrent()

        assertTrue(states.last().actionsPending)

        subject.complete(LessonSubject(AppRoutes.TabRoot(AppTab.RECORDBOOK), 0, AppRoutes.TabRoot(AppTab.RECORDBOOK)))
        runCurrent()
        val loading = states.last()
        assertFalse(loading.actionsPending)
        assertEquals(listOf(LessonActionKind.ROUTE, LessonActionKind.FRIENDS, LessonActionKind.SUBJECT), loading.actions.map { it.kind })
        assertEquals(LessonAction(LessonActionKind.FRIENDS, UiText.Res(ScheduleStrings.schedule_sheet_friends_none), enabled = false), loading.actions[1])

        friends.complete(emptyList())
        runCurrent()
        // Nobody there: the tile stays, disabled, so the row never reflows.
        assertEquals(loading.actions, states.last().actions)
    }

    @Test
    fun aSlowSubjectStopsHoldingTheTilesAfterAWhile() = runTest(main.dispatcher) {
        val vm = viewModel(
            AppRoutes.LessonDetails(LESSON),
            subjects = listOf(LessonSubjectSource { _, _, _ -> awaitCancellation() }),
        )
        val states = mutableListOf<LessonSheetUiState>()
        backgroundScope.launch { vm.uiState.collect(states::add) }
        runCurrent()
        assertTrue(states.last().actionsPending)

        advanceTimeBy(1_001)

        assertFalse(states.last().actionsPending)
        assertEquals(listOf(LessonActionKind.ROUTE, LessonActionKind.FRIENDS), states.last().actions.map { it.kind })
    }

    @Test
    fun theTeacherPhotoIsScreenStateAndDoesNotHoldTheLessonOrRequireBackendServices() = runTest(main.dispatcher) {
        val answer = CompletableDeferred<String?>()
        val photos = FakePersonPhotosRepository().apply {
            cached = mapOf(TEACHER_ISU to "https://example.test/cached.jpg")
            lookup = { answer.await() }
        }
        val vm = viewModel(AppRoutes.LessonDetails(LESSON), services = false, photos = photos)
        assertEquals("https://example.test/cached.jpg", vm.uiState.value.teacher?.pictureUrl)
        val states = mutableListOf<LessonSheetUiState>()
        backgroundScope.launch { vm.uiState.collect(states::add) }
        runCurrent()
        assertEquals(listOf(TEACHER_ISU), photos.calls)
        assertFalse(states.last().actionsPending)
        assertEquals(LESSON.subjectName, states.last().subject)

        answer.complete("https://example.test/teacher.jpg")
        runCurrent()
        assertEquals("https://example.test/teacher.jpg", states.last().teacher?.pictureUrl)
        assertEquals(LESSON.teacherFio, states.last().teacher?.name)
        assertEquals(UiText.Res(ScheduleStrings.schedule_sheet_teacher), states.last().teacher?.tone)
    }

    @Test
    fun aTeacherWithoutAKnownIsuKeepsInitialsWithoutADirectoryLookup() = runTest(main.dispatcher) {
        val photos = FakePersonPhotosRepository()
        val vm = viewModel(AppRoutes.LessonDetails(LESSON.copy(teacherIsu = 0)), photos = photos)
        runCurrent()
        assertNull(vm.uiState.value.teacher?.pictureUrl)
        assertTrue(photos.calls.isEmpty())
    }

    private object OneFriendOnLessons : LessonFriendsRepository {
        override suspend fun friendsOnLesson(pairId: Long, date: LocalDate): AppResult<List<UserSummary>> =
            AppResult.Success(listOf(FRIEND))
    }

    private object PositiveLevels : TeacherLevelsRepository {
        override suspend fun levels(isus: Set<Int>): Map<Int, TeacherLevel> = isus.associateWith { TeacherLevel.POSITIVE }
    }

    private companion object {
        const val TEACHER_ISU = 300003
        val FRIEND = UserSummary(300001, "Козлов Иван", null, emptyList(), UserSharing(sport = true, schedule = true))
        val KRONVA = KnownBuilding("kronva", listOf(13), listOf("кронв"), "Кронва", "Кронверкский пр., 49", 59.95, 30.31)
        val LESSON = LessonDetailsArgs(
            pairId = 7001, date = "2026-09-09", subjectName = "Физика", typeId = 3, format = "Очный",
            start = "10:00", end = "11:30", teacherFio = "Смирнова Ольга Петровна", teacherIsu = TEACHER_ISU.toLong(),
            room = "1506", building = "Кронверкский проспект, 49", buildingId = 13, mainBuildingId = 13, note = null,
            zoomUrl = null, zoomPassword = null, zoomInfo = null, flowName = "ФИЗ 2.3", subjectId = 70010,
        )
        val QUEUE = PendingSportDetailsArgs(
            lessonId = 101, sectionName = "Плавание", autoSign = true, isPrediction = false,
            start = "2026-09-09T15:20+03:00", end = "2026-09-09T16:50+03:00", teacherFio = "Захаров Николай Иванович",
            roomName = "Бассейн",
        )
    }
}
