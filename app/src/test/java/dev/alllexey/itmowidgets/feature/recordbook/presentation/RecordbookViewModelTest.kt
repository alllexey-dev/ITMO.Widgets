package dev.alllexey.itmowidgets.feature.recordbook.presentation

import dev.alllexey.itmowidgets.feature.recordbook.FakeBarsPreference
import dev.alllexey.itmowidgets.feature.recordbook.FakeBarsRepository
import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.recordbook.FakeRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.FakeSportScoreRepository
import dev.alllexey.itmowidgets.feature.recordbook.FixedAcademicTime
import dev.alllexey.itmowidgets.feature.recordbook.recordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.recordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportResolver
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import dev.alllexey.itmowidgets.core.sport.SportScorePeriod
import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import java.time.OffsetDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordbookViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private val repository = FakeRecordbookRepository()
    private val sport = FakeSportScoreRepository()

    private fun model(state: SavedStateHandle = SavedStateHandle(), date: String = "2026-09-07") =
        RecordbookViewModel(repository, FakeBarsRepository(), FakeBarsPreference(), state, RecordbookSportResolver(sport), FixedAcademicTime(date))

    @Test fun `loads current academic period without asking sport for regular subjects`() = runTest {
        val vm = model(); vm.ensureDataLoaded(); advanceUntilIdle()
        assertEquals(3, (vm.uiState.value as RecordbookUiState.Content).selection.period.semester)
        assertEquals(0, sport.periodRequests)
    }

    @Test fun `academic override selects spring even when server actual is autumn`() = runTest {
        val vm = model(date = "2026-06-01"); vm.ensureDataLoaded(); advanceUntilIdle()
        assertEquals(2, (vm.uiState.value as RecordbookUiState.Content).selection.period.semester)
    }

    @Test fun `restores explicitly selected historical period`() = runTest {
        val vm = model(SavedStateHandle(mapOf<String, Any>("recordbook_program_id" to 1L, "recordbook_semester" to 1)))
        vm.ensureDataLoaded(); advanceUntilIdle()
        assertEquals(1, (vm.uiState.value as RecordbookUiState.Content).selection.period.semester)
    }

    @Test fun `renders real empty catalog instead of not found error`() = runTest {
        repository.programs = AppResult.Success(emptyList())
        val vm = model(); vm.ensureDataLoaded(); advanceUntilIdle()
        assertEquals(RecordbookUiState.Empty, vm.uiState.value)
    }

    @Test fun `catalog failure is retryable`() = runTest {
        repository.programs = AppResult.Failure(AppError.Network)
        val vm = model(); vm.ensureDataLoaded(); advanceUntilIdle()
        assertEquals(RecordbookUiState.Error(AppError.Network), vm.uiState.value)
    }

    @Test fun `a fresh screen shows the cached period before the network answers`() = runTest {
        repository.cachedPrograms = listOf(recordbookProgram())
        repository.cachedSubjects = listOf(recordbookSubject(name = "Из кэша"))
        val gate = CompletableDeferred<AppResult<List<RecordbookProgram>>>()
        repository.programLoader = { gate.await() }
        val vm = model(); vm.ensureDataLoaded(); runCurrent()
        val seeded = vm.uiState.value as RecordbookUiState.Content
        assertFalse(seeded.refreshing)
        assertEquals("Из кэша", seeded.subjects.single().name)
        assertEquals(3, seeded.selection.period.semester)
        gate.complete(repository.programs); advanceUntilIdle()
        val fresh = vm.uiState.value as RecordbookUiState.Content
        assertFalse(fresh.refreshing)
        assertEquals(listOf(recordbookSubject()), fresh.subjects)
    }

    @Test fun `a cached catalog without the selected period still starts loading`() = runTest {
        repository.cachedPrograms = listOf(recordbookProgram())
        val vm = model(); vm.ensureDataLoaded()
        assertTrue(vm.uiState.value is RecordbookUiState.Loading)
    }

    @Test fun `refresh keeps content and ends spinner even when identical result arrives`() = runTest {
        val vm = model(); vm.ensureDataLoaded(); advanceUntilIdle()
        val before = vm.uiState.value
        vm.refresh()
        assertTrue((vm.uiState.value as RecordbookUiState.Content).refreshing)
        advanceUntilIdle()
        assertEquals(before, vm.uiState.value)
        assertEquals(2, repository.programRequests)
    }

    @Test fun `repeated failed refresh keeps previous values and clears spinner`() = runTest {
        val vm = model(); vm.ensureDataLoaded(); advanceUntilIdle()
        repository.subjects = AppResult.Failure(AppError.Network)
        vm.refresh(); vm.refresh(); advanceUntilIdle()
        val state = vm.uiState.value as RecordbookUiState.Content
        assertFalse(state.refreshing)
        assertEquals(AppError.Network, state.refreshError)
        assertEquals(listOf(recordbookSubject()), state.subjects)
    }

    @Test fun `rapid period selection cancels old result and cannot mix periods`() = runTest {
        val vm = model(); vm.ensureDataLoaded(); advanceUntilIdle()
        val oldRequest = CompletableDeferred<AppResult<List<dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject>>>()
        repository.subjectLoader = { semester -> if (semester == 1) oldRequest.await() else AppResult.Success(listOf(recordbookSubject(semester.toLong()))) }
        vm.selectPeriod(1, 1); runCurrent()
        vm.selectPeriod(1, 2); advanceUntilIdle()
        oldRequest.complete(AppResult.Success(listOf(recordbookSubject(99))))
        advanceUntilIdle()
        val state = vm.uiState.value as RecordbookUiState.Content
        assertEquals(2, state.selection.period.semester)
        assertEquals(2L, state.subjects.single().entryId)
    }

    @Test fun `sport failure does not hide official grades or mark PE passed`() = runTest {
        repository.subjects = AppResult.Success(listOf(recordbookSubject(name = "Физическая культура и спорт (элективная)").copy(rate = null)))
        sport.periods = AppResult.Failure(AppError.Network)
        val vm = model(); vm.ensureDataLoaded(); advanceUntilIdle()
        val state = vm.uiState.value as RecordbookUiState.Content
        assertEquals(RecordbookSportState.Error, state.sport)
        assertNull(state.subjects.single().rate)
    }

    @Test fun `same period does not trigger another request`() = runTest {
        val vm = model(); vm.ensureDataLoaded(); advanceUntilIdle()
        vm.selectPeriod(1, 3); advanceUntilIdle()
        assertEquals(listOf(3), repository.subjectRequests)
    }

    // --- attention and summary

    private val pe = recordbookSubject(id = 7, name = "Физическая культура и спорт (элективная)").copy(controlType = "Зачёт", rate = null, score = null)

    private fun sportPeriodEndingOn(date: String) {
        sport.periods = AppResult.Success(listOf(SportScorePeriod(11, "Осень 2026/2027", OffsetDateTime.parse("${date}T00:00:00+03:00"), current = true)))
        sport.score = AppResult.Success(SportScoreSummary(30, 10))
    }

    @Test fun `physical education short of points early in the semester stays in the regular list`() = runTest {
        repository.subjects = AppResult.Success(listOf(recordbookSubject(), pe))
        sportPeriodEndingOn("2026-12-28")
        val vm = model(); vm.ensureDataLoaded(); advanceUntilIdle()
        assertTrue((vm.uiState.value as RecordbookUiState.Content).attention.isEmpty())
    }

    @Test fun `physical education four weeks before the end needs attention with the missing points`() = runTest {
        repository.subjects = AppResult.Success(listOf(recordbookSubject(), pe))
        sportPeriodEndingOn("2026-12-28")
        val vm = model(date = "2026-12-01"); vm.ensureDataLoaded(); advanceUntilIdle()
        val state = vm.uiState.value as RecordbookUiState.Content
        assertEquals(mapOf(7L to RecordbookAttentionReason.SportShort(60)), state.attention)
    }

    @Test fun `a credited physical education never needs attention`() = runTest {
        repository.subjects = AppResult.Success(listOf(pe.copy(rate = "зачет")))
        sportPeriodEndingOn("2026-12-28")
        val vm = model(date = "2026-12-01"); vm.ensureDataLoaded(); advanceUntilIdle()
        assertTrue((vm.uiState.value as RecordbookUiState.Content).attention.isEmpty())
    }

    @Test fun `a known control under its minimum names the reason`() = runTest {
        repository.subjects = AppResult.Success(listOf(recordbookSubject().copy(rate = null, score = 30.0)))
        repository.cachedControls = listOf(
            RecordbookControl(1, "Лабораторная 1", 8.0, 5.0, 10.0, true, null, null),
            RecordbookControl(2, "КР 1", 3.0, 6.0, 15.0, true, null, null)
        )
        val vm = model(); vm.ensureDataLoaded(); advanceUntilIdle()
        assertEquals(mapOf(42L to RecordbookAttentionReason.BelowMinimum("КР 1")),
            (vm.uiState.value as RecordbookUiState.Content).attention)
    }

    @Test fun `failed and absent subjects need attention, passed ones do not`() = runTest {
        repository.subjects = AppResult.Success(listOf(
            recordbookSubject(id = 1).copy(rate = "2/FX"),
            recordbookSubject(id = 2).copy(rate = null, absent = true),
            recordbookSubject(id = 3)
        ))
        val vm = model(); vm.ensureDataLoaded(); advanceUntilIdle()
        assertEquals(mapOf(1L to RecordbookAttentionReason.Failed, 2L to RecordbookAttentionReason.Absent),
            (vm.uiState.value as RecordbookUiState.Content).attention)
    }

    @Test fun `the summary waits for the first final grade or credit`() = runTest {
        repository.subjects = AppResult.Success(listOf(recordbookSubject(id = 1).copy(rate = null), recordbookSubject(id = 2).copy(rate = null, score = null)))
        val midSemester = model(); midSemester.ensureDataLoaded(); advanceUntilIdle()
        assertFalse((midSemester.uiState.value as RecordbookUiState.Content).showSummary)

        repository.subjects = AppResult.Success(listOf(recordbookSubject(id = 1).copy(rate = "зачет"), recordbookSubject(id = 2).copy(rate = null)))
        val session = model(); session.ensureDataLoaded(); advanceUntilIdle()
        assertTrue((session.uiState.value as RecordbookUiState.Content).showSummary)
    }
}
