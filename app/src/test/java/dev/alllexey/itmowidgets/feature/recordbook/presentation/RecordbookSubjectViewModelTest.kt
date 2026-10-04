package dev.alllexey.itmowidgets.feature.recordbook.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.RestrictionCapability
import dev.alllexey.itmowidgets.core.resources.UserRestriction
import kotlinx.coroutines.launch
import org.junit.Assert.assertFalse
import dev.alllexey.itmowidgets.core.resources.SubjectLinkChip
import dev.alllexey.itmowidgets.core.resources.SubjectLinksState
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.schedule.ScheduleSubject
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.recordbook.FakeBarsRepository
import dev.alllexey.itmowidgets.feature.recordbook.FakeMarkTrackingRepository
import dev.alllexey.itmowidgets.feature.recordbook.FakeRecordbookRepository
import dev.alllexey.itmowidgets.core.testing.FakeScheduleRefreshGateway
import dev.alllexey.itmowidgets.feature.recordbook.FakeSheetScoresRepository
import dev.alllexey.itmowidgets.core.testing.FakeSportScoreRepository
import dev.alllexey.itmowidgets.feature.recordbook.FakeSubjectBindingStore
import dev.alllexey.itmowidgets.core.testing.FakeSubjectLessonsGateway
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.feature.recordbook.barsJournal
import dev.alllexey.itmowidgets.feature.recordbook.barsSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsSubjectDetails
import dev.alllexey.itmowidgets.feature.recordbook.domain.ControlEntry
import dev.alllexey.itmowidgets.feature.recordbook.domain.ControlGroup
import dev.alllexey.itmowidgets.feature.recordbook.domain.ControlGroupKind
import dev.alllexey.itmowidgets.feature.recordbook.domain.GradeStep
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookGradeScale
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportResolver
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectContext
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectContextResolver
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.StudyHalf
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.subjectNameKey
import dev.alllexey.itmowidgets.feature.recordbook.recordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.sheetScore
import dev.alllexey.itmowidgets.core.testing.subjectLesson
import dev.alllexey.itmowidgets.core.testing.FakeSubjectLinksRepository
import dev.alllexey.itmowidgets.core.testing.linksSnapshot
import dev.alllexey.itmowidgets.core.testing.subjectLink
import dev.alllexey.itmowidgets.core.testing.FakeTeacherLevelsRepository
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordbookSubjectViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private val repository = FakeRecordbookRepository()
    private val bars = FakeBarsRepository()
    private val resources = FakeSubjectLinksRepository()
    private val levels = FakeTeacherLevelsRepository()
    private val marks = FakeMarkTrackingRepository()
    private fun model(withBars: Boolean = false, sheets: FakeSheetScoresRepository = FakeSheetScoresRepository()) = RecordbookSubjectViewModel(repository, bars, SavedStateHandle(buildMap {
        put("entry_id", 42L); put("program_id", 1L); put("semester", 2); put("study_year", "2025/2026")
        if (withBars) { put("bars_plan", 8L); put("bars_type", "flow"); put("bars_identifier", "7") }
    }), RecordbookSportResolver(FakeSportScoreRepository()), lessons, scheduleRefresh, bindingStore, SubjectContextResolver(), FixedAcademicTime(LocalDate.of(2026, 9, 7)), resources, levels, marks, sheets)
    private val lessons = FakeSubjectLessonsGateway()
    private val scheduleRefresh = FakeScheduleRefreshGateway()
    private val bindingStore = FakeSubjectBindingStore()

    @Test fun `opening the page reads the subject's new marks once`() = runTest {
        val vm = model(); advanceUntilIdle()
        assertTrue(vm.uiState.value is RecordbookSubjectUiState.Content)
        assertEquals(listOf(StudyHalf(2025, 2) to subjectNameKey("Тестовый предмет")), marks.read)

        vm.refresh(); advanceUntilIdle()
        assertEquals(1, marks.read.size)
    }

    @Test fun `past periods expose private links independently from the schedule binding`() = runTest {
        resources.state.value = SubjectLinksState.Content(linksSnapshot(mine = listOf(subjectLink("own")), servicesEnabled = false))
        val vm = model(); advanceUntilIdle()
        val content = vm.uiState.value as RecordbookSubjectUiState.Content
        assertNotNull(content.hub.resourceScope)
        assertEquals("2025-2", content.hub.resourceScope!!.periodKey)
        assertFalse((content.hub.links as SubjectLinksState.Content).snapshot.servicesEnabled)
        assertFalse(content.refreshing)
    }

    @Test fun `BARS journal overlays the official subject and supplies its controls`() = runTest {
        val control = RecordbookControl(6, "Работа", 7.5, 0.0, 10.0, true, null, null)
        bars.details = AppResult.Success(BarsSubjectDetails(barsSubject(), listOf(control)))
        val vm = model(withBars = true); advanceUntilIdle()
        val state = vm.uiState.value as RecordbookSubjectUiState.Content
        assertEquals(listOf(barsJournal()), bars.journalRequests)
        assertEquals(91.5, state.subject.score!!, 0.0)
        assertEquals(42L, state.subject.entryId)
        assertEquals(listOf(control), state.controls)
        assertEquals(0, repository.controlRequests)
        assertNull(state.barsError)
    }
    @Test fun `failed BARS journal falls back to official values with a visible error`() = runTest {
        bars.details = AppResult.Failure(AppError.Network)
        val vm = model(withBars = true); advanceUntilIdle()
        val state = vm.uiState.value as RecordbookSubjectUiState.Content
        assertEquals(recordbookSubject(), state.subject)
        assertEquals(AppError.Network, state.barsError)
        assertEquals(1, repository.controlRequests)
    }
    @Test fun `a fresh hub shows the cached subject and controls before the network answers`() = runTest {
        val control = RecordbookControl(6, "Работа", 7.5, 0.0, 10.0, true, null, null)
        repository.cachedSubjects = listOf(recordbookSubject(name = "Из кэша"))
        repository.cachedControls = listOf(control)
        val gate = CompletableDeferred<AppResult<List<RecordbookSubject>>>()
        repository.subjectLoader = { gate.await() }
        val vm = model(); runCurrent()
        val seeded = vm.uiState.value as RecordbookSubjectUiState.Content
        assertFalse(seeded.refreshing)
        assertEquals("Из кэша", seeded.subject.name)
        assertEquals(listOf(control), seeded.controls)
        gate.complete(repository.subjects); advanceUntilIdle()
        val fresh = vm.uiState.value as RecordbookSubjectUiState.Content
        assertFalse(fresh.refreshing)
        assertEquals(recordbookSubject(), fresh.subject)
    }

    @Test fun `a cached subject without cached controls still starts from the placeholder`() = runTest {
        repository.cachedSubjects = listOf(recordbookSubject(name = "Из кэша"))
        val gate = CompletableDeferred<AppResult<List<RecordbookSubject>>>()
        repository.subjectLoader = { gate.await() }
        val vm = model(); runCurrent()
        assertEquals(RecordbookSubjectUiState.Loading, vm.uiState.value)
        gate.complete(repository.subjects); advanceUntilIdle()
        assertTrue(vm.uiState.value is RecordbookSubjectUiState.Content)
    }

    @Test fun `subject without a BARS journal never asks BARS`() = runTest {
        model(); advanceUntilIdle()
        assertTrue(bars.journalRequests.isEmpty())
    }

    @Test fun `no tree does not make a detail API request`() = runTest {
        repository.subjects = AppResult.Success(listOf(recordbookSubject(details = false)))
        val vm = model(); advanceUntilIdle()
        assertEquals(0, repository.controlRequests)
        assertTrue((vm.uiState.value as RecordbookSubjectUiState.Content).controls.isEmpty())
    }

    @Test fun `failed controls leave the overview available`() = runTest {
        repository.controls = AppResult.Failure(AppError.Forbidden)
        val vm = model(); advanceUntilIdle()
        val state = vm.uiState.value as RecordbookSubjectUiState.Content
        assertEquals(recordbookSubject(), state.subject)
        assertEquals(AppError.Forbidden, state.controlsError)
    }

    @Test fun `refresh reloads official overview instead of showing stale navigation arguments`() = runTest {
        val vm = model(); advanceUntilIdle()
        val updated = recordbookSubject().copy(rate = "5/A", score = 96.0)
        repository.subjects = AppResult.Success(listOf(updated))
        vm.refresh(); advanceUntilIdle()
        assertEquals(updated, (vm.uiState.value as RecordbookSubjectUiState.Content).subject)
    }

    @Test fun `unknown entry yields not found without requesting controls`() = runTest {
        repository.subjects = AppResult.Success(emptyList())
        val vm = model(); advanceUntilIdle()
        assertEquals(RecordbookSubjectUiState.Error(AppError.NotFound), vm.uiState.value)
        assertEquals(0, repository.controlRequests)
    }

    @Test fun `failed refresh preserves content and stops spinner`() = runTest {
        val vm = model(); advanceUntilIdle()
        repository.subjects = AppResult.Failure(AppError.Network)
        vm.refresh(); vm.refresh(); advanceUntilIdle()
        val state = vm.uiState.value as RecordbookSubjectUiState.Content
        assertEquals(recordbookSubject(), state.subject)
        assertEquals(AppError.Network, state.refreshError)
        assertFalse(state.refreshing)
    }

    // --- subject hub

    private fun content() = model().let { it to it }.first
    private fun RecordbookSubjectViewModel.hub() = (uiState.value as RecordbookSubjectUiState.Content).hub
    private fun currentPeriodModel(
        subject: RecordbookSubject = recordbookSubject(),
        sheets: FakeSheetScoresRepository = FakeSheetScoresRepository(),
    ): RecordbookSubjectViewModel {
        // FixedAcademicTime is 2026-09-07: autumn of 2026/2027, semester 3 for a second-year student.
        repository.subjects = AppResult.Success(listOf(subject))
        return RecordbookSubjectViewModel(repository, bars, SavedStateHandle(buildMap {
            put("entry_id", 42L); put("program_id", 1L); put("semester", 3); put("study_year", "2026/2027")
        }), RecordbookSportResolver(FakeSportScoreRepository()), lessons, scheduleRefresh, bindingStore, SubjectContextResolver(), FixedAcademicTime(LocalDate.of(2026, 9, 7)), resources, levels, marks, sheets)
    }

    @Test fun `an exact discipline id shows the upcoming lessons and their teachers without asking`() = runTest {
        lessons.lessons.value = listOf(
            subjectLesson(1, "2026-09-08", subjectId = 1L, typeId = 1, teacherIsu = 1, teacherFio = "Лектор Л. Л."),
            subjectLesson(2, "2026-09-09", subjectId = 1L, typeId = 3, teacherIsu = 2, teacherFio = "Практик П. П."),
            subjectLesson(3, "2026-09-10", subjectId = 1L, typeId = 3, teacherIsu = 2, teacherFio = "Практик П. П."),
            subjectLesson(4, "2026-09-11", subjectId = 2L, name = "Другой предмет"),
            subjectLesson(5, "2026-12-01", subjectId = 1L)
        )
        val model = currentPeriodModel()
        advanceUntilIdle()

        val hub = model.hub()
        assertEquals(SubjectLessonsState.Content(lessons.lessons.value.take(3), SubjectContext.Source.EXACT), hub.lessons)
        // Teachers keep the order the schedule shows them in; roles are their lesson types.
        assertEquals(listOf(SubjectTeacher("Лектор Л. Л.", 1, listOf(1)), SubjectTeacher("Практик П. П.", 2, listOf(3))), hub.teachers)
        assertEquals(listOf(LocalDate.parse("2026-09-07") to LocalDate.parse("2026-10-05")), scheduleRefresh.requests)
        assertTrue(hub.chips.visible.none { it is SubjectLinkChip.Lms })
    }

    @Test fun `teacher tones are asked once per set of ISUs and teachers without one are never asked`() = runTest {
        levels.levels[123456] = TeacherLevel.POSITIVE
        levels.levels[234567] = TeacherLevel.MIXED
        lessons.lessons.value = listOf(
            subjectLesson(1, "2026-09-08", subjectId = 1L, typeId = 1, teacherIsu = 123456, teacherFio = "Лектор Л. Л."),
            subjectLesson(2, "2026-09-09", subjectId = 1L, typeId = 3, teacherIsu = 234567, teacherFio = "Практик П. П."),
            subjectLesson(3, "2026-09-10", subjectId = 1L, typeId = 3, teacherIsu = null, teacherFio = "Без ИСУ Б. Б."),
        )
        val model = currentPeriodModel()
        advanceUntilIdle()

        assertEquals(mapOf(123456L to TeacherLevel.POSITIVE, 234567L to TeacherLevel.MIXED), model.hub().teacherLevels)
        assertEquals(listOf(setOf(123456, 234567)), levels.calls)

        lessons.lessons.value = lessons.lessons.value + subjectLesson(4, "2026-09-11", subjectId = 1L, typeId = 3,
            teacherIsu = 234567, teacherFio = "Практик П. П.")
        advanceUntilIdle()
        assertEquals(1, levels.calls.size)

        lessons.lessons.value = lessons.lessons.value.filter { it.teacherIsu != 234567L }
        advanceUntilIdle()
        assertEquals(listOf(setOf(123456, 234567), setOf(123456)), levels.calls)
        assertEquals(mapOf(123456L to TeacherLevel.POSITIVE), model.hub().teacherLevels)
    }

    @Test fun `a page whose teachers have no ISU asks for no tones`() = runTest {
        lessons.lessons.value = listOf(subjectLesson(1, "2026-09-08", subjectId = 1L, teacherIsu = null, teacherFio = "Без ИСУ Б. Б."))
        val model = currentPeriodModel()
        advanceUntilIdle()

        assertEquals(emptyMap<Long, TeacherLevel>(), model.hub().teacherLevels)
        assertEquals(emptyList<Set<Int>>(), levels.calls)
    }

    @Test fun `a name match is proposed, confirming stores it and rejecting leaves it unmatched`() = runTest {
        lessons.lessons.value = listOf(subjectLesson(1, "2026-09-08", subjectId = 555L, name = "Тестовый  ПРЕДМЕТ", teacherFio = "Иванов И. И."))
        val model = currentPeriodModel()
        advanceUntilIdle()
        assertEquals(SubjectLessonsState.Proposed(ScheduleSubject(555L, "Тестовый  ПРЕДМЕТ", setOf(10L))), model.hub().lessons)
        // Until the link is confirmed the recordbook teacher stands in.
        assertEquals(listOf(SubjectTeacher("Тестовый преподаватель", null, emptyList())), model.hub().teachers)

        model.rejectProposal()
        advanceUntilIdle()
        assertEquals(SubjectLessonsState.Unmatched, model.hub().lessons)
        assertTrue(bindingStore.bindings.isEmpty())

        model.confirmBinding(555L)
        advanceUntilIdle()
        assertEquals(mapOf(1L to 555L), bindingStore.bindings)
        assertEquals(SubjectLessonsState.Content(lessons.lessons.value, SubjectContext.Source.CONFIRMED), model.hub().lessons)
        assertEquals(listOf(SubjectTeacher("Иванов И. И.", 300001, listOf(1))), model.hub().teachers)
    }

    @Test fun `two look-alikes ask which one and a stored answer wins next time`() = runTest {
        lessons.lessons.value = listOf(
            subjectLesson(1, "2026-09-08", subjectId = 555L, name = "Тестовый предмет"),
            subjectLesson(2, "2026-09-08", subjectId = 556L, name = "тестовый предмет", start = "11:30")
        )
        val first = currentPeriodModel()
        advanceUntilIdle()
        assertTrue(first.hub().lessons is SubjectLessonsState.Ambiguous)
        assertEquals(2, (first.hub().lessons as SubjectLessonsState.Ambiguous).candidates.size)

        first.confirmBinding(556L)
        advanceUntilIdle()
        val second = currentPeriodModel()
        advanceUntilIdle()
        assertEquals(SubjectLessonsState.Content(lessons.lessons.value.filter { it.subjectId == 556L }, SubjectContext.Source.CONFIRMED), second.hub().lessons)
    }

    @Test fun `a subject absent from the window is unmatched even when it exists later`() = runTest {
        lessons.lessons.value = listOf(subjectLesson(1, "2026-09-08", subjectId = 2L, name = "Другой предмет"))
        val unmatched = currentPeriodModel()
        advanceUntilIdle()
        assertEquals(SubjectLessonsState.Unmatched, unmatched.hub().lessons)

        lessons.lessons.value = listOf(subjectLesson(1, "2026-12-08", subjectId = 1L))
        val later = currentPeriodModel()
        advanceUntilIdle()
        assertEquals(SubjectLessonsState.Unmatched, later.hub().lessons)
    }

    @Test fun `a failed schedule refresh is an error only when the cache is empty`() = runTest {
        scheduleRefresh.result = AppResult.Failure(AppError.Network)
        val empty = currentPeriodModel()
        advanceUntilIdle()
        assertEquals(SubjectLessonsState.Error(AppError.Network), empty.hub().lessons)

        lessons.lessons.value = listOf(subjectLesson(1, "2026-09-08", subjectId = 1L))
        empty.retryLessons()
        advanceUntilIdle()
        assertEquals(SubjectLessonsState.Content(lessons.lessons.value, SubjectContext.Source.EXACT), empty.hub().lessons)
    }

    @Test fun `past periods and physical education hide the lessons but keep the recordbook teacher`() = runTest {
        lessons.lessons.value = listOf(subjectLesson(1, "2026-09-08", subjectId = 1L))
        val past = model()
        advanceUntilIdle()
        assertEquals(SubjectLessonsState.Hidden, past.hub().lessons)
        assertEquals(listOf(SubjectTeacher("Тестовый преподаватель", null, emptyList())), past.hub().teachers)
        assertTrue(scheduleRefresh.requests.isEmpty())

        val pe = currentPeriodModel(recordbookSubject(name = "Физическая культура и спорт (элективная)"))
        advanceUntilIdle()
        assertEquals(SubjectLessonsState.Hidden, pe.hub().lessons)
    }

    @Test fun `the LMS link becomes the only resource and a subject without controls still builds its hub`() = runTest {
        repository.controls = AppResult.Success(emptyList())
        lessons.lessons.value = listOf(subjectLesson(1, "2026-09-08", subjectId = 1L))
        val model = currentPeriodModel(recordbookSubject(details = false).copy(lmsLink = "https://lms.itmo.ru/course/1"))
        advanceUntilIdle()

        assertEquals(SubjectLinkChip.Lms("https://lms.itmo.ru/course/1"), model.hub().chips.visible.first())
        assertTrue(model.hub().lessons is SubjectLessonsState.Content)
        assertEquals(0, repository.controlRequests)
    }

    // --- links, grade step, control groups, lessons

    @Test fun `chips rank the snapshot links by score and chats come from it`() = runTest {
        resources.state.value = SubjectLinksState.Content(linksSnapshot(
            mine = listOf(subjectLink("table", LinkCategory.SCORES), subjectLink("chat", LinkCategory.CHAT)),
            shared = listOf(
                subjectLink("group-chat", LinkCategory.CHAT, LinkVisibility.FLOW, isMine = false),
                subjectLink("tasks", LinkCategory.TASKS, LinkVisibility.FLOW, isMine = false),
                subjectLink("notes", LinkCategory.NOTES, LinkVisibility.ALL, isMine = false, score = 3),
                subjectLink("video", LinkCategory.RECORDINGS, LinkVisibility.ALL, isMine = false),
                subjectLink("exam", LinkCategory.EXAM, LinkVisibility.ALL, isMine = false)
            )
        ))
        val vm = model(); advanceUntilIdle()
        val hub = vm.hub()
        assertEquals(listOf("notes", "table", "tasks"),
            hub.chips.visible.map { (it as SubjectLinkChip.Link).link.id })
        assertEquals(2, hub.chips.moreCount)
        assertEquals(listOf("chat", "group-chat"), hub.chats.map { it.id })
        // «Все ссылки, N» counts what the links sheet lists, chats included.
        assertEquals(7, hub.linkCount)
        assertEquals(1, resources.refreshes)
    }

    @Test fun `votes on the page toggle like in the sheet and follow the connection and restrictions`() = runTest {
        val shared = subjectLink("notes", LinkCategory.NOTES, LinkVisibility.ALL, isMine = false, score = 3)
        resources.state.value = SubjectLinksState.Content(linksSnapshot(shared = listOf(shared)))
        val vm = model(); advanceUntilIdle()
        assertTrue(vm.hub().canVote)

        vm.voteLink("notes", up = true); advanceUntilIdle()
        resources.state.value = SubjectLinksState.Content(linksSnapshot(shared = listOf(shared.copy(myVote = 1))))
        advanceUntilIdle()
        vm.voteLink("notes", up = true); advanceUntilIdle()
        vm.voteLink("notes", up = false); advanceUntilIdle()
        assertEquals(listOf("vote:notes:1", "vote:notes:0", "vote:notes:-1"), resources.actions)

        resources.restrictions.value = listOf(UserRestriction("r", RestrictionCapability.VOTE, "spam", null))
        advanceUntilIdle()
        assertFalse(vm.hub().canVote)
        resources.restrictions.value = emptyList()
        resources.state.value = SubjectLinksState.Content(linksSnapshot(shared = listOf(shared)).copy(servicesEnabled = false))
        advanceUntilIdle()
        assertFalse(vm.hub().canVote)
    }

    @Test fun `a vote that fails on the page is reported once`() = runTest {
        resources.state.value = SubjectLinksState.Content(linksSnapshot(
            shared = listOf(subjectLink("notes", LinkCategory.NOTES, LinkVisibility.ALL, isMine = false))
        ))
        resources.result = AppResult.Failure(AppError.Network)
        val vm = model(); advanceUntilIdle()
        val errors = mutableListOf<AppError>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.linkErrors.collect(errors::add) }
        vm.voteLink("notes", up = false); advanceUntilIdle()
        assertEquals(listOf<AppError>(AppError.Network), errors)
        collector.cancel()
    }

    @Test fun `a vote does not change which links the page shows until a pull`() = runTest {
        val links = listOf(
            subjectLink("a", LinkCategory.TASKS, LinkVisibility.ALL, isMine = false, score = 3),
            subjectLink("b", LinkCategory.NOTES, LinkVisibility.ALL, isMine = false, score = 2),
            subjectLink("c", LinkCategory.EXAM, LinkVisibility.ALL, isMine = false, score = 1),
            subjectLink("d", LinkCategory.OTHER, LinkVisibility.ALL, isMine = false, score = 0),
        )
        resources.state.value = SubjectLinksState.Content(linksSnapshot(shared = links))
        val vm = model(); advanceUntilIdle()
        fun shown() = vm.hub().chips.visible.map { (it as SubjectLinkChip.Link).link.id }
        assertEquals(listOf("a", "b", "c"), shown())

        resources.state.value = SubjectLinksState.Content(linksSnapshot(shared = links.dropLast(1) + links.last().copy(score = 10, myVote = 1)))
        advanceUntilIdle()
        assertEquals(listOf("a", "b", "c"), shown())

        vm.refresh()
        advanceUntilIdle()
        resources.state.value = SubjectLinksState.Content(linksSnapshot(shared = links.dropLast(1) + links.last().copy(score = 10, myVote = 1)))
        advanceUntilIdle()
        assertEquals(listOf("d", "a", "b"), shown())
    }

    @Test fun `new links reach the open page without a reload`() = runTest {
        resources.state.value = SubjectLinksState.Content(linksSnapshot())
        val vm = model(); advanceUntilIdle()
        assertTrue(vm.hub().chips.visible.isEmpty())
        resources.state.value = SubjectLinksState.Content(linksSnapshot(mine = listOf(subjectLink("new"))))
        advanceUntilIdle()
        assertEquals(listOf("new"), vm.hub().chips.visible.map { (it as SubjectLinkChip.Link).link.id })
    }

    @Test fun `physical education has no links, chips or chats`() = runTest {
        repository.subjects = AppResult.Success(listOf(recordbookSubject(name = "Физическая культура и спорт (базовая)")
            .copy(lmsLink = "https://lms.itmo.ru/course/1")))
        val vm = model(); advanceUntilIdle()
        val hub = vm.hub()
        assertNull(hub.resourceScope)
        assertNull(hub.links)
        assertTrue(hub.chips.visible.isEmpty())
        assertTrue(hub.chats.isEmpty())
        assertEquals(0, resources.refreshes)
        assertNull((vm.uiState.value as RecordbookSubjectUiState.Content).gradeStep)
    }

    @Test fun `a connection of this subject period is shown and read on entry and on every pull`() = runTest {
        val sheets = FakeSheetScoresRepository()
        val vm = model(sheets = sheets); advanceUntilIdle()
        val scope = vm.hub().resourceScope!!
        sheets.scores.value = listOf(sheetScore(scope = scope))
        advanceUntilIdle()

        val connected = vm.hub().sheet as SubjectSheetState.Connected
        assertEquals("66,3", connected.score.value)
        assertEquals(LocalDateTime.of(2026, 9, 7, 12, 0), connected.updatedAt)
        assertEquals(listOf(scope), sheets.refreshes)

        vm.refresh(); advanceUntilIdle()
        assertEquals(listOf(scope, scope), sheets.refreshes)
    }

    @Test fun `without a connection the sheet links are offered own first, then pinned, then scores`() = runTest {
        val sheets = FakeSheetScoresRepository()
        fun sheetLink(id: String, category: LinkCategory, mine: Boolean, score: Int = 0, url: String = sheetUrl(id)) =
            subjectLink(id, category, isMine = mine, score = score).copy(url = url)
        resources.state.value = SubjectLinksState.Content(linksSnapshot(
            mine = listOf(sheetLink("own", LinkCategory.OTHER, mine = true)),
            shared = listOf(
                sheetLink("scores", LinkCategory.SCORES, mine = false, score = 1),
                sheetLink("pinned", LinkCategory.MATERIALS, mine = false),
                sheetLink("copy", LinkCategory.SCORES, mine = false, score = 9, url = sheetUrl("scores")),
                subjectLink("github", LinkCategory.TASKS, isMine = false).copy(url = "https://github.com/synthetic/tasks"),
            ),
            pinnedId = "pinned",
        ))
        val vm = model(sheets = sheets); advanceUntilIdle()
        sheets.scores.value = listOf(sheetScore(scope = vm.hub().resourceScope!!.copy(periodKey = "2024-2")))
        advanceUntilIdle()

        val hint = vm.hub().sheet as SubjectSheetState.Hint
        assertEquals(listOf(sheetUrl("own"), sheetUrl("pinned"), sheetUrl("scores")), hint.links.map { it.url })
        assertEquals(listOf(true, false, false), hint.links.map { it.mine })
        assertEquals("Ссылка copy", hint.links.last().title)
    }

    @Test fun `one sheet link is one option and links without a sheet offer nothing`() = runTest {
        resources.state.value = SubjectLinksState.Content(linksSnapshot(
            shared = listOf(subjectLink("sheet", LinkCategory.SCORES, isMine = false).copy(url = sheetUrl("sheet")))
        ))
        val vm = model(); advanceUntilIdle()
        assertEquals(listOf(SheetLinkOption(sheetUrl("sheet"), "Ссылка sheet", mine = false)), (vm.hub().sheet as SubjectSheetState.Hint).links)

        resources.state.value = SubjectLinksState.Content(linksSnapshot(
            shared = listOf(subjectLink("github", LinkCategory.TASKS, isMine = false).copy(url = "https://github.com/synthetic/tasks"))
        ))
        advanceUntilIdle()
        assertNull(vm.hub().sheet)
    }

    @Test fun `a new total reaches the open page without a reload`() = runTest {
        val sheets = FakeSheetScoresRepository()
        val vm = model(sheets = sheets); advanceUntilIdle()
        val scope = vm.hub().resourceScope!!
        sheets.scores.value = listOf(sheetScore(scope = scope))
        advanceUntilIdle()
        val requests = repository.subjectRequests.size

        sheets.scores.value = listOf(sheetScore(scope = scope, value = "70"))
        advanceUntilIdle()

        assertEquals("70", (vm.hub().sheet as SubjectSheetState.Connected).score.value)
        assertEquals(requests, repository.subjectRequests.size)
        assertEquals(listOf(scope), sheets.refreshes)
    }

    @Test fun `physical education has no sheet and reads none`() = runTest {
        val sheets = FakeSheetScoresRepository()
        repository.subjects = AppResult.Success(listOf(recordbookSubject(name = "Физическая культура и спорт (базовая)")))
        resources.state.value = SubjectLinksState.Content(linksSnapshot(mine = listOf(subjectLink("own").copy(url = sheetUrl("own")))))
        val vm = model(sheets = sheets); advanceUntilIdle()

        assertNull(vm.hub().sheet)
        assertTrue(sheets.refreshes.isEmpty())
    }

    @Test fun `disconnecting leaves the offer to connect again`() = runTest {
        val sheets = FakeSheetScoresRepository()
        resources.state.value = SubjectLinksState.Content(linksSnapshot(mine = listOf(subjectLink("own").copy(url = sheetUrl("own")))))
        val vm = model(sheets = sheets); advanceUntilIdle()
        val scope = vm.hub().resourceScope!!
        sheets.scores.value = listOf(sheetScore(scope = scope))
        advanceUntilIdle()

        vm.disconnectSheet(); advanceUntilIdle()

        assertEquals(listOf(scope), sheets.disconnected)
        assertTrue(vm.hub().sheet is SubjectSheetState.Hint)
    }

    @Test fun `the grade step follows the score and the kind of assessment`() = runTest {
        repository.subjects = AppResult.Success(listOf(recordbookSubject().copy(rate = null, score = 72.0)))
        val exam = model(); advanceUntilIdle()
        assertEquals(GradeStep("4C", 3.0), (exam.uiState.value as RecordbookSubjectUiState.Content).gradeStep)

        repository.subjects = AppResult.Success(listOf(recordbookSubject().copy(controlType = "Зачёт", rate = null, score = 52.0)))
        val credit = model(); advanceUntilIdle()
        assertEquals(GradeStep(RecordbookGradeScale.CREDIT_TARGET, 8.0), (credit.uiState.value as RecordbookSubjectUiState.Content).gradeStep)

        repository.subjects = AppResult.Success(listOf(recordbookSubject().copy(rate = "4/C", score = 76.0)))
        val graded = model(); advanceUntilIdle()
        assertNull((graded.uiState.value as RecordbookSubjectUiState.Content).gradeStep)

        repository.subjects = AppResult.Success(listOf(recordbookSubject().copy(rate = null, score = null)))
        val unknown = model(); advanceUntilIdle()
        assertNull((unknown.uiState.value as RecordbookSubjectUiState.Content).gradeStep)
    }

    @Test fun `numbered controls are grouped and lone ones stay rows`() = runTest {
        repository.controls = AppResult.Success(listOf(
            RecordbookControl(1, "Лабораторная работа 1", 8.0, 5.0, 10.0, true, null, null),
            RecordbookControl(2, "Лабораторная работа 2", 3.0, 5.0, 10.0, true, null, null),
            RecordbookControl(3, "Экзамен", null, 20.0, 40.0, true, null, null)
        ))
        val vm = model(); advanceUntilIdle()
        val entries = (vm.uiState.value as RecordbookSubjectUiState.Content).controlGroups
        val labs = entries.first() as ControlGroup
        assertEquals(ControlGroupKind.LABS, labs.kind)
        assertEquals(11.0, labs.score!!, 0.0)
        assertEquals(20.0, labs.maximum!!, 0.0)
        assertEquals(listOf(2L), labs.belowMinimum.map { it.id })
        assertTrue(entries.last() is ControlEntry.Single)
    }

    @Test fun `two nearest lessons first and the rest after show all`() = runTest {
        lessons.lessons.value = (1L..5L).map { subjectLesson(it, "2026-09-${(7 + it).toString().padStart(2, '0')}", subjectId = 1L) }
        val model = currentPeriodModel()
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L), model.hub().visibleLessons.map { it.pairId })
        assertEquals(5, model.hub().allLessonsCount)

        model.showAllLessons()
        assertEquals((1L..5L).toList(), model.hub().visibleLessons.map { it.pairId })
        assertEquals(0, model.hub().allLessonsCount)

        model.refresh(); advanceUntilIdle()
        assertTrue(model.hub().lessonsExpanded)
    }

    private fun sheetUrl(id: String) = "https://docs.google.com/spreadsheets/d/1SyntheticSheet${id.padEnd(16, '0')}/edit"
}
