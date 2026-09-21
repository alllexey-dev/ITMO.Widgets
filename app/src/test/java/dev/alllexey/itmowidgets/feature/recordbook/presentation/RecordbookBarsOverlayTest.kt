package dev.alllexey.itmowidgets.feature.recordbook.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.recordbook.FakeBarsPreference
import dev.alllexey.itmowidgets.feature.recordbook.FakeBarsRepository
import dev.alllexey.itmowidgets.feature.recordbook.FakeRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.FakeSportScoreRepository
import dev.alllexey.itmowidgets.feature.recordbook.FixedAcademicTime
import dev.alllexey.itmowidgets.feature.recordbook.barsSubject
import dev.alllexey.itmowidgets.feature.recordbook.recordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportResolver
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordbookBarsOverlayTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    private val myItmo = FakeRecordbookRepository().apply {
        subjects = AppResult.Success(listOf(recordbookSubject(), recordbookSubject(id = 43L, name = "Физическая культура и спорт (элективная)")))
    }
    private val bars = FakeBarsRepository().apply { subjects = AppResult.Success(listOf(barsSubject())) }
    private val preference = FakeBarsPreference()
    private fun model(state: SavedStateHandle = SavedStateHandle()) = RecordbookViewModel(myItmo, bars, preference, state,
        RecordbookSportResolver(FakeSportScoreRepository()), FixedAcademicTime())
    private val content get() = model().let { it.ensureDataLoaded(); it }

    @Test fun `disabled overlay never asks BARS`() = runTest {
        val vm = content; advanceUntilIdle()
        assertFalse(vm.barsEnabled.value)
        assertTrue(bars.periodRequests.isEmpty())
        assertEquals(75.0, (vm.uiState.value as RecordbookUiState.Content).subjects.first().score!!, 0.0)
    }
    @Test fun `enabling overlays matched subjects keeps the rest and persists the choice`() = runTest {
        val vm = content; advanceUntilIdle()
        vm.setBarsEnabled(true); advanceUntilIdle()
        val state = vm.uiState.value as RecordbookUiState.Content
        assertEquals(listOf(myItmoPeriod()), bars.periodRequests.map { it.studyYear to it.semester })
        assertEquals(91.5, state.subjects[0].score!!, 0.0)
        assertEquals("5/A", state.subjects[0].rate)
        assertEquals(42L, state.subjects[0].entryId)
        assertEquals("Тестовый преподаватель", state.subjects[0].teacherName)
        assertEquals(barsSubject().barsJournal, state.subjects[0].barsJournal)
        assertTrue(state.barsApplied)
        assertFalse(state.refreshing)
        assertNull(state.subjects[1].barsJournal)
        assertEquals(recordbookSubject(id = 43L, name = "Физическая культура и спорт (элективная)"), state.subjects[1])
        assertTrue(preference.enabled)
        val restored = model(); restored.ensureDataLoaded(); advanceUntilIdle()
        assertTrue(restored.barsEnabled.value)
        assertEquals(91.5, (restored.uiState.value as RecordbookUiState.Content).subjects[0].score!!, 0.0)
    }
    @Test fun `BARS failure keeps MyITMO values and reports it separately`() = runTest {
        preference.enabled = true
        bars.subjects = AppResult.Failure(AppError.Unauthorized)
        val vm = content; advanceUntilIdle()
        val state = vm.uiState.value as RecordbookUiState.Content
        assertEquals(AppError.Unauthorized, state.barsError)
        assertFalse(state.barsApplied)
        assertNull(state.refreshError)
        assertEquals(75.0, state.subjects[0].score!!, 0.0)
        bars.subjects = AppResult.Success(listOf(barsSubject()))
        vm.refresh(); advanceUntilIdle()
        assertNull((vm.uiState.value as RecordbookUiState.Content).barsError)
    }
    @Test fun `disabling drops the overlay without waiting for a slow BARS reply`() = runTest {
        preference.enabled = true
        val slow = CompletableDeferred<AppResult<List<RecordbookSubject>>>()
        bars.subjectLoader = { slow.await() }
        val vm = content; runCurrent()
        vm.setBarsEnabled(false); advanceUntilIdle()
        slow.complete(AppResult.Success(listOf(barsSubject())))
        advanceUntilIdle()
        assertEquals(75.0, (vm.uiState.value as RecordbookUiState.Content).subjects[0].score!!, 0.0)
        assertFalse(preference.enabled)
    }
    @Test fun `MyITMO list is shown while BARS is still loading and is not marked as missing`() = runTest {
        preference.enabled = true
        val slow = CompletableDeferred<AppResult<List<RecordbookSubject>>>()
        bars.subjectLoader = { slow.await() }
        val vm = content; advanceUntilIdle()
        val pending = vm.uiState.value as RecordbookUiState.Content
        // The load on entry waits for BARS silently; only a pull shows the indicator.
        assertFalse(pending.refreshing)
        assertFalse(pending.barsApplied)
        assertEquals(75.0, pending.subjects[0].score!!, 0.0)
        slow.complete(AppResult.Success(listOf(barsSubject()))); advanceUntilIdle()
        val done = vm.uiState.value as RecordbookUiState.Content
        assertFalse(done.refreshing)
        assertTrue(done.barsApplied)
        assertEquals(91.5, done.subjects[0].score!!, 0.0)
    }
    @Test fun `toggle before the first load wins over the stored value`() = runTest {
        preference.enabled = true
        val vm = model(); vm.setBarsEnabled(false); advanceUntilIdle()
        assertFalse(vm.barsEnabled.value)
        assertTrue(bars.periodRequests.isEmpty())
    }

    private fun myItmoPeriod() = "2026/2027" to 3
}
