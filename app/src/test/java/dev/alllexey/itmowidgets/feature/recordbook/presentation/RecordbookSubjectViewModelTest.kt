package dev.alllexey.itmowidgets.feature.recordbook.presentation

import dev.alllexey.itmowidgets.feature.recordbook.FakeBarsPreference
import dev.alllexey.itmowidgets.feature.recordbook.FakeBarsRepository
import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.result.AppError
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
    private fun model(withBars: Boolean = false) = RecordbookSubjectViewModel(repository, bars, SavedStateHandle(buildMap {
        put("entry_id", 42L); put("program_id", 1L); put("semester", 2); put("study_year", "2025/2026")
        if (withBars) { put("bars_plan", 8L); put("bars_type", "flow"); put("bars_identifier", "7") }
    }), RecordbookSportResolver(FakeSportScoreRepository()), lessons, scheduleRefresh, bindingStore, SubjectContextResolver(), FixedAcademicTime())
    private val lessons = FakeSubjectLessonsGateway()
    private val scheduleRefresh = FakeScheduleRefreshGateway()
    private val bindingStore = FakeSubjectBindingStore()

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
        assertTrue(seeded.refreshing)
        assertEquals("Из кэша", seeded.subject.name)
        assertEquals(listOf(control), seeded.controls)
        gate.complete(repository.subjects); advanceUntilIdle()
        val fresh = vm.uiState.value as RecordbookSubjectUiState.Content
        assertFalse(fresh.refreshing)
        assertEquals(recordbookSubject(), fresh.subject)
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
        }), RecordbookSportResolver(FakeSportScoreRepository()), lessons, scheduleRefresh, bindingStore, SubjectContextResolver(), FixedAcademicTime())
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
        assertTrue(hub.resources.isEmpty())
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

        assertEquals(listOf(SubjectResource("https://lms.itmo.ru/course/1")), model.hub().resources)
        assertTrue(model.hub().lessons is SubjectLessonsState.Content)
        assertEquals(0, repository.controlRequests)
    }
}
