package dev.alllexey.itmowidgets.feature.recordbook.presentation

import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeSportScoreRepository
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.feature.recordbook.FakeBarsPreference
import dev.alllexey.itmowidgets.feature.recordbook.FakeBarsRepository
import dev.alllexey.itmowidgets.feature.recordbook.FakeMarkTrackingRepository
import dev.alllexey.itmowidgets.feature.recordbook.FakeRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.FakeSheetScoresRepository
import dev.alllexey.itmowidgets.feature.recordbook.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.recordbook.TEST_HALF
import dev.alllexey.itmowidgets.feature.recordbook.barsJournal
import dev.alllexey.itmowidgets.feature.recordbook.barsSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportResolver
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.BarsCheckpointMark
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.BarsPlanMarks
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.of
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.recordbookSubject
import dev.alllexey.itmowidgets.shared.feature.recordbook.RecordbookStrings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** BARS in the recordbook: optional, offered quietly, laid over My ITMO once connected, dimmed when its session ends. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordbookBarsTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    private val myItmo = FakeRecordbookRepository().apply {
        subjects = AppResult.Success(listOf(
            recordbookSubject().copy(rate = null),
            recordbookSubject(id = 43L, name = "Другой тестовый предмет").copy(rate = null),
        ))
    }
    private val bars = FakeBarsRepository().apply { subjects = AppResult.Success(listOf(barsSubject(rate = null))) }
    private val preference = FakeBarsPreference()
    private val marks = FakeMarkTrackingRepository()
    private fun loaded() = RecordbookViewModel(myItmo, bars, preference, RecordbookSportResolver(FakeSportScoreRepository()),
        FixedAcademicTime(LocalDate(2026, 9, 7)), marks, FakeSheetScoresRepository()).apply { refresh(RefreshMode.Silent) }

    @Test fun `without BARS the list is complete from My ITMO and offers BARS quietly`() = runTest {
        val vm = loaded(); advanceUntilIdle()
        assertTrue(bars.periodRequests.isEmpty())
        assertEquals("75", vm.uiState.value.row(42).points)
        assertEquals(RecordbookStrings.recordbook_source_myitmo, vm.uiState.value.row(42).sourceKey)
        assertTrue(vm.uiState.value.content.barsOffer)
        assertNull(vm.uiState.value.content.barsNotice)
    }

    @Test fun `the offer hidden once stays hidden`() = runTest {
        val vm = loaded(); advanceUntilIdle()
        vm.hideBarsOffer(); advanceUntilIdle()
        assertFalse(vm.uiState.value.content.barsOffer)
        assertTrue(preference.offerHidden)
        assertFalse(loaded().also { advanceUntilIdle() }.uiState.value.content.barsOffer)
    }

    @Test fun `a BARS sign-in connects BARS and lays it over the matched subjects`() = runTest {
        val vm = loaded(); advanceUntilIdle()
        vm.barsSignedIn(); advanceUntilIdle()
        assertTrue(preference.enabled)
        val state = vm.uiState.value
        assertEquals("91,5", state.row(42).points)
        assertEquals(RecordbookStrings.recordbook_source_bars, state.row(42).sourceKey)
        assertEquals(RecordbookStrings.recordbook_source_myitmo, state.row(43).sourceKey)
        assertFalse(state.content.barsOffer)
        assertEquals(barsJournal().planId, (state.row(42).target as RecordbookTarget.Subject).args.barsPlan)
    }

    @Test fun `a BARS failure keeps My ITMO values and says so over the list`() = runTest {
        preference.enabled = true
        bars.subjects = AppResult.Failure(AppError.Network)
        val vm = loaded(); advanceUntilIdle()
        assertEquals("75", vm.uiState.value.row(42).points)
        assertEquals(RecordbookBarsNotice.Failed(AppError.Network), vm.uiState.value.content.barsNotice)
        assertFalse(vm.uiState.value.content.barsOffer)
    }

    @Test fun `an ended session keeps the earlier BARS numbers dimmed with their date and asks to sign in`() = runTest {
        preference.enabled = true
        val vm = loaded(); advanceUntilIdle()
        assertEquals("91,5", vm.uiState.value.row(42).points)

        bars.subjects = AppResult.Failure(AppError.Unauthorized)
        vm.refresh(RefreshMode.Pull); advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(RecordbookBarsNotice.Expired, state.content.barsNotice)
        assertEquals("91,5", state.row(42).points)
        assertTrue(state.row(42).stale)
        assertEquals(RecordbookStrings.recordbook_source_since, state.row(42).sourceKey)
        assertFalse(state.row(43).stale)
    }

    @Test fun `an ended session without an earlier answer shows My ITMO`() = runTest {
        preference.enabled = true
        bars.subjects = AppResult.Failure(AppError.Unauthorized)
        val vm = loaded(); advanceUntilIdle()
        assertEquals(RecordbookBarsNotice.Expired, vm.uiState.value.content.barsNotice)
        assertEquals("75", vm.uiState.value.row(42).points)
        assertFalse(vm.uiState.value.row(42).stale)
    }

    @Test fun `My ITMO is shown while BARS still loads`() = runTest {
        preference.enabled = true
        val slow = CompletableDeferred<AppResult<List<RecordbookSubject>>>()
        bars.subjectLoader = { slow.await() }
        val vm = loaded(); advanceUntilIdle()
        assertFalse(vm.uiState.value.content.refreshing)
        assertEquals("75", vm.uiState.value.row(42).points)
        assertNull(vm.uiState.value.content.barsNotice)
        slow.complete(AppResult.Success(listOf(barsSubject(rate = null)))); advanceUntilIdle()
        assertEquals("91,5", vm.uiState.value.row(42).points)
    }

    @Test fun `a BARS answer advances the snapshot with the journals whose checkpoints are known`() = runTest {
        preference.enabled = true
        val other = barsSubject(name = "Тестовый предмет без точек").copy(barsJournal = barsJournal(9L))
        bars.subjects = AppResult.Success(listOf(barsSubject(), other))
        val controls = listOf(
            RecordbookControl(1, "Контрольная 1", 10.0, 5.0, 20.0, true, null, null),
            RecordbookControl(2, "Контрольная 2", null, 5.0, 20.0, true, null, null),
        )
        bars.cachedControls = mapOf(barsJournal() to controls)
        loaded(); advanceUntilIdle()
        assertEquals(listOf(TEST_HALF to listOf(BarsPlanMarks.of(barsSubject(), controls)!!)), marks.seenBars)
        assertEquals(listOf(BarsCheckpointMark(1, 10.0, false)), marks.seenBars.single().second.single().marks)
    }

    @Test fun `a failed BARS answer advances nothing of BARS`() = runTest {
        preference.enabled = true
        bars.subjects = AppResult.Failure(AppError.Network)
        loaded(); advanceUntilIdle()
        assertTrue(marks.seenBars.isEmpty())
        assertEquals(1, marks.seenMyItmo.size)
    }
}
