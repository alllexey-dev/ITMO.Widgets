package dev.alllexey.itmowidgets.feature.recordbook.presentation

import dev.alllexey.itmowidgets.feature.recordbook.FakeBarsPreference
import dev.alllexey.itmowidgets.feature.recordbook.FakeBarsRepository
import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.SubjectLinkChip
import dev.alllexey.itmowidgets.core.resources.SubjectLinksState
import dev.alllexey.itmowidgets.feature.recordbook.domain.ControlGroup
import dev.alllexey.itmowidgets.feature.recordbook.domain.ControlGroupKind
import dev.alllexey.itmowidgets.feature.recordbook.domain.ControlEntry
import dev.alllexey.itmowidgets.feature.recordbook.domain.GradeStep
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookGradeScale
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.feature.resources.presentation.FakeSubjectLinksRepository
import dev.alllexey.itmowidgets.feature.resources.presentation.linksSnapshot
import dev.alllexey.itmowidgets.feature.resources.presentation.subjectLink
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.recordbook.FakeRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.FakeScheduleRefreshGateway
import dev.alllexey.itmowidgets.feature.recordbook.FakeSportScoreRepository
import dev.alllexey.itmowidgets.feature.recordbook.FakeSubjectBindingStore
import dev.alllexey.itmowidgets.feature.recordbook.FakeSubjectLessonsGateway
import dev.alllexey.itmowidgets.feature.recordbook.FixedAcademicTime
import dev.alllexey.itmowidgets.feature.recordbook.barsJournal
import dev.alllexey.itmowidgets.feature.recordbook.barsSubject
import dev.alllexey.itmowidgets.feature.recordbook.recordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.subjectLesson
import dev.alllexey.itmowidgets.core.schedule.ScheduleSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectContext
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import java.time.LocalDate
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsSubjectDetails
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportResolver
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectContextResolver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
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
    private fun model(withBars: Boolean = false) = RecordbookSubjectViewModel(repository, bars, SavedStateHandle(buildMap {
        put("entry_id", 42L); put("program_id", 1L); put("semester", 2); put("study_year", "2025/2026")
        if (withBars) { put("bars_plan", 8L); put("bars_type", "flow"); put("bars_identifier", "7") }
    }), RecordbookSportResolver(FakeSportScoreRepository()), lessons, scheduleRefresh, bindingStore, SubjectContextResolver(), FixedAcademicTime(), resources)
    private val lessons = FakeSubjectLessonsGateway()
    private val scheduleRefresh = FakeScheduleRefreshGateway()
    private val bindingStore = FakeSubjectBindingStore()

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
    private fun currentPeriodModel(subject: RecordbookSubject = recordbookSubject()): RecordbookSubjectViewModel {
        // FixedAcademicTime is 2026-09-07: autumn of 2026/2027, semester 3 for a second-year student.
        repository.subjects = AppResult.Success(listOf(subject))
        return RecordbookSubjectViewModel(repository, bars, SavedStateHandle(buildMap {
            put("entry_id", 42L); put("program_id", 1L); put("semester", 3); put("study_year", "2026/2027")
        }), RecordbookSportResolver(FakeSportScoreRepository()), lessons, scheduleRefresh, bindingStore, SubjectContextResolver(), FixedAcademicTime(), resources)
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

    @Test fun `chips and chats come from the links snapshot`() = runTest {
        resources.state.value = SubjectLinksState.Content(linksSnapshot(
            mine = listOf(subjectLink("table", LinkCategory.SCORES), subjectLink("chat", LinkCategory.CHAT)),
            shared = listOf(
                subjectLink("group-chat", LinkCategory.CHAT, LinkVisibility.GROUP, isMine = false),
                subjectLink("tasks", LinkCategory.TASKS, LinkVisibility.FLOW, isMine = false),
                subjectLink("notes", LinkCategory.NOTES, LinkVisibility.ALL, isMine = false, score = 3),
                subjectLink("video", LinkCategory.RECORDINGS, LinkVisibility.ALL, isMine = false),
                subjectLink("exam", LinkCategory.EXAM, LinkVisibility.ALL, isMine = false)
            )
        ))
        val vm = model(); advanceUntilIdle()
        val hub = vm.hub()
        assertEquals(listOf("table", "tasks", "video", "notes"),
            hub.chips.visible.map { (it as SubjectLinkChip.Link).link.id })
        assertEquals(1, hub.chips.moreCount)
        assertEquals(listOf("chat", "group-chat"), hub.chats.map { it.id })
        assertEquals(1, resources.refreshes)
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
}
