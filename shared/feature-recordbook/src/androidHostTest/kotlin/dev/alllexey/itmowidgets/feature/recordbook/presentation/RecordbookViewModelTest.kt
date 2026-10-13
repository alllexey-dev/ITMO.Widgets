package dev.alllexey.itmowidgets.feature.recordbook.presentation

import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.sport.SportScorePeriod
import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import dev.alllexey.itmowidgets.core.testing.FakeSportScoreRepository
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.recordbook.FakeBarsPreference
import dev.alllexey.itmowidgets.feature.recordbook.FakeBarsRepository
import dev.alllexey.itmowidgets.feature.recordbook.FakeMarkTrackingRepository
import dev.alllexey.itmowidgets.feature.recordbook.FakeRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.FakeSheetScoresRepository
import dev.alllexey.itmowidgets.feature.recordbook.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.recordbook.TEST_HALF
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportResolver
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.StudyHalf
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.markNews
import dev.alllexey.itmowidgets.feature.recordbook.recordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.recordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.sheetScore
import dev.alllexey.itmowidgets.shared.feature.recordbook.RecordbookPlurals
import dev.alllexey.itmowidgets.shared.feature.recordbook.RecordbookStrings
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordbookViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private val repository = FakeRecordbookRepository()
    private val sport = FakeSportScoreRepository()
    private val marks = FakeMarkTrackingRepository()

    private fun model(
        date: String = "2026-09-07",
        sheets: FakeSheetScoresRepository = FakeSheetScoresRepository(),
    ) = RecordbookViewModel(repository, FakeBarsRepository(), FakeBarsPreference(), RecordbookSportResolver(sport),
        FixedAcademicTime(LocalDate.parse(date)), marks, sheets)

    private fun loaded(vm: RecordbookViewModel = model()) = vm.apply { refresh(RefreshMode.Silent) }

    @Test fun `loads current academic period without asking sport for regular subjects`() = runTest {
        val vm = loaded(); advanceUntilIdle()
        assertEquals(3, vm.uiState.value.selectedSemester)
        assertEquals(0, sport.periodRequests)
    }

    @Test fun `the header says the course and year and offers every period newest first`() = runTest {
        val vm = loaded(); advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(UiText.Res(RecordbookStrings.recordbook_subtitle, listOf(2, "2026/2027")), state.subtitle)
        assertEquals(UiText.Res(RecordbookStrings.subject_semester, listOf(3)), state.periods!!.label)
        val group = state.periods!!.groups.single()
        assertEquals(listOf(3, 2, 1), group.options.map { it.semester })
        assertNull(group.program)
    }

    @Test fun `a connected sheet's total stands in for My ITMO while the subject is in progress`() = runTest {
        val sheets = FakeSheetScoresRepository(
            sheetScore(scope = ResourceScope(1, "Тестовый предмет", "2026-1"), value = "41,5"),
            sheetScore(scope = ResourceScope(2, "Тестовый предмет 2", "2026-1"), value = null),
        )
        repository.subjects = AppResult.Success(listOf(
            recordbookSubject().copy(rate = null, score = 35.0),
            recordbookSubject(id = 43, name = "Тестовый предмет 2").copy(disciplineId = 2, rate = null, score = 20.0),
        ))
        val vm = loaded(model(sheets = sheets)); advanceUntilIdle()

        assertEquals("41,5", vm.uiState.value.row(42).points)
        assertEquals(RecordbookStrings.recordbook_source_sheet, vm.uiState.value.row(42).sourceKey)
        assertEquals("20", vm.uiState.value.row(43).points)
        assertEquals(RecordbookStrings.recordbook_source_myitmo, vm.uiState.value.row(43).sourceKey)
    }

    @Test fun `a new sheet total reaches the open list without a request or a download`() = runTest {
        repository.subjects = AppResult.Success(listOf(recordbookSubject().copy(rate = null)))
        val sheets = FakeSheetScoresRepository()
        val vm = loaded(model(sheets = sheets)); advanceUntilIdle()
        val requests = repository.subjectRequests.size

        sheets.scores.value = listOf(sheetScore(scope = ResourceScope(1, "Тестовый предмет", "2026-1"), value = "70"))
        advanceUntilIdle()

        assertEquals("70", vm.uiState.value.row(42).points)
        assertEquals(requests, repository.subjectRequests.size)
        assertTrue(sheets.refreshes.isEmpty())
        assertTrue(sheets.inspected.isEmpty())
    }

    @Test fun `academic override selects spring even when server actual is autumn`() = runTest {
        val vm = loaded(model(date = "2026-06-01")); advanceUntilIdle()
        assertEquals(2, vm.uiState.value.selectedSemester)
    }

    @Test fun `renders real empty catalog instead of not found error`() = runTest {
        repository.programs = AppResult.Success(emptyList())
        val vm = loaded(); advanceUntilIdle()
        assertEquals(RecordbookBody.Empty, vm.uiState.value.body)
    }

    @Test fun `catalog failure is retryable and a retry shows its progress`() = runTest {
        repository.programs = AppResult.Failure(AppError.Network)
        val vm = loaded(); advanceUntilIdle()
        assertEquals(RecordbookBody.Failed(AppError.Network, retrying = false), vm.uiState.value.body)

        val gate = CompletableDeferred<AppResult<List<RecordbookProgram>>>()
        repository.programLoader = { gate.await() }
        vm.refresh(RefreshMode.Force); runCurrent()
        assertEquals(RecordbookBody.Failed(AppError.Network, retrying = true), vm.uiState.value.body)
        gate.complete(AppResult.Success(listOf(recordbookProgram()))); advanceUntilIdle()
        assertEquals(listOf(42L), vm.uiState.value.rows.map { it.id })
    }

    @Test fun `a fresh screen shows the cached period before the network answers`() = runTest {
        repository.cachedPrograms = listOf(recordbookProgram())
        repository.cachedSubjects = listOf(recordbookSubject(name = "Из кэша"))
        val gate = CompletableDeferred<AppResult<List<RecordbookProgram>>>()
        repository.programLoader = { gate.await() }
        val vm = loaded(); runCurrent()
        assertFalse(vm.uiState.value.content.refreshing)
        assertEquals("Из кэша", vm.uiState.value.rows.single().name)
        gate.complete(repository.programs); advanceUntilIdle()
        assertEquals("Тестовый предмет", vm.uiState.value.rows.single().name)
    }

    @Test fun `a cached catalog without the selected period still starts loading`() = runTest {
        repository.cachedPrograms = listOf(recordbookProgram())
        val vm = loaded()
        assertEquals(RecordbookBody.Loading, vm.uiState.value.body)
    }

    @Test fun `a pull shows its indicator and ends it even when the identical result arrives`() = runTest {
        val vm = loaded(); advanceUntilIdle()
        val before = vm.uiState.value
        vm.refresh(RefreshMode.Pull)
        assertTrue(vm.uiState.value.content.refreshing)
        advanceUntilIdle()
        assertEquals(before, vm.uiState.value)
        assertEquals(2, repository.programRequests)
    }

    @Test fun `a failed pull keeps the rows and says so`() = runTest {
        val vm = loaded(); advanceUntilIdle()
        repository.subjects = AppResult.Failure(AppError.Network)
        vm.refresh(RefreshMode.Pull); vm.refresh(RefreshMode.Pull); advanceUntilIdle()
        val content = vm.uiState.value.content
        assertFalse(content.refreshing)
        assertEquals(AppError.Network, content.refreshError)
        assertEquals(listOf(42L), vm.uiState.value.rows.map { it.id })
    }

    @Test fun `rapid period selection cancels old result and cannot mix periods`() = runTest {
        val vm = loaded(); advanceUntilIdle()
        val oldRequest = CompletableDeferred<AppResult<List<RecordbookSubject>>>()
        repository.subjectLoader = { semester -> if (semester == 1) oldRequest.await() else AppResult.Success(listOf(recordbookSubject(semester.toLong()))) }
        vm.selectPeriod(1, 1); runCurrent()
        vm.selectPeriod(1, 2); advanceUntilIdle()
        oldRequest.complete(AppResult.Success(listOf(recordbookSubject(99))))
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.selectedSemester)
        assertEquals(listOf(2L), vm.uiState.value.rows.map { it.id })
    }

    @Test fun `same period does not trigger another request`() = runTest {
        val vm = loaded(); advanceUntilIdle()
        vm.selectPeriod(1, 3); advanceUntilIdle()
        assertEquals(listOf(3), repository.subjectRequests)
    }

    // --- physical education

    private val pe = recordbookSubject(id = 7, name = "Физическая культура и спорт (элективная)").copy(controlType = "Зачёт", rate = null, score = null)

    private fun sportPeriodEndingOn(date: String) {
        sport.periods = AppResult.Success(listOf(SportScorePeriod(11, "Осень 2026/2027", Instant.parse("${date}T00:00:00+03:00"), current = true)))
        sport.score = AppResult.Success(SportScoreSummary(30, 10))
    }

    @Test fun `physical education shows the sport total in a ring and leads to the sport tab`() = runTest {
        repository.subjects = AppResult.Success(listOf(recordbookSubject(), pe))
        sportPeriodEndingOn("2026-12-28")
        val vm = loaded(); advanceUntilIdle()
        val row = vm.uiState.value.row(7)
        assertEquals(RecordbookValue.Sport("40", 0.3f, 0.1f), row.value)
        assertEquals(RecordbookTarget.Sport, row.target)
        assertEquals(RecordbookStrings.recordbook_source_sport, row.sourceKey)
        assertFalse(row.hasSources)
    }

    @Test fun `sport failure does not hide official grades or mark PE passed`() = runTest {
        repository.subjects = AppResult.Success(listOf(pe))
        sport.periods = AppResult.Failure(AppError.Network)
        val vm = loaded(); advanceUntilIdle()
        assertEquals(RecordbookValue.None, vm.uiState.value.row(7).value)
    }

    @Test fun `physical education short of points early in the semester stays in the regular list`() = runTest {
        repository.subjects = AppResult.Success(listOf(recordbookSubject(), pe))
        sportPeriodEndingOn("2026-12-28")
        val vm = loaded(); advanceUntilIdle()
        assertTrue(vm.uiState.value.attentionIds.isEmpty())
    }

    @Test fun `physical education four weeks before the end needs attention with the missing points`() = runTest {
        repository.subjects = AppResult.Success(listOf(recordbookSubject().copy(rate = null), pe))
        sportPeriodEndingOn("2026-12-28")
        val vm = loaded(model(date = "2026-12-01")); advanceUntilIdle()
        assertEquals(listOf(7L), vm.uiState.value.attentionIds)
        val row = vm.uiState.value.row(7)
        assertEquals(UiText.Plural(RecordbookPlurals.recordbook_reason_sport, 60, listOf(60)), row.meta)
        assertEquals(RecordbookTone.WARN, row.metaTone)
    }

    @Test fun `a known control under its minimum names the reason and turns the bar red`() = runTest {
        repository.subjects = AppResult.Success(listOf(recordbookSubject().copy(rate = null, score = 30.0)))
        repository.cachedControls = listOf(
            RecordbookControl(1, "Лабораторная 1", 8.0, 5.0, 10.0, true, null, null),
            RecordbookControl(2, "КР 1", 3.0, 6.0, 15.0, true, null, null),
        )
        val vm = loaded(); advanceUntilIdle()
        val row = vm.uiState.value.row(42)
        assertEquals(listOf(42L), vm.uiState.value.attentionIds)
        assertEquals(UiText.Res(RecordbookStrings.recordbook_reason_below_minimum, listOf("КР 1")), row.meta)
        assertEquals(RecordbookValue.Points("30", 0.3f, RecordbookTone.BAD), row.value)
    }

    @Test fun `failed and absent subjects need attention, passed ones show their result`() = runTest {
        repository.subjects = AppResult.Success(listOf(
            recordbookSubject(id = 1).copy(rate = "2/FX"),
            recordbookSubject(id = 2).copy(rate = null, absent = true),
            recordbookSubject(id = 3),
        ))
        val vm = loaded(); advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(listOf(1L, 2L), state.attentionIds)
        assertEquals(RecordbookValue.Result(UiText.Dynamic("2FX"), RecordbookTone.BAD), state.row(1).value)
        assertEquals(RecordbookValue.Result(UiText.Dynamic("4C"), RecordbookTone.OK), state.row(3).value)
    }

    // --- one list and the session

    @Test fun `near the end of the semester the one list adds the session with the passed count and the average`() = runTest {
        repository.subjects = AppResult.Success(listOf(
            recordbookSubject(id = 1).copy(rate = "4/C", score = 79.0),
            recordbookSubject(id = 2).copy(rate = "5/A", score = 92.0),
            recordbookSubject(id = 3).copy(rate = null, score = 55.0),
        ))
        val vm = loaded(model(date = "2026-12-07")); advanceUntilIdle()
        val content = vm.uiState.value.content
        val summary = content.summary!!
        assertEquals(UiText.Res(RecordbookStrings.recordbook_session_passed, listOf(2)), summary.passed)
        assertEquals(UiText.Res(RecordbookStrings.recordbook_session_average, listOf("85,5")), summary.average)
        assertEquals(listOf(RecordbookStrings.recordbook_disciplines_section), content.sections.map { (it.title as UiText.Res).key })
    }

    @Test fun `mid-semester the same list has no session even with a final result`() = runTest {
        repository.subjects = AppResult.Success(listOf(recordbookSubject().copy(rate = "4/C")))
        val vm = loaded(); advanceUntilIdle()
        val content = vm.uiState.value.content
        assertNull(content.summary)
        assertEquals(listOf(RecordbookStrings.recordbook_disciplines_section), content.sections.map { (it.title as UiText.Res).key })
    }

    // --- marks

    @Test fun `unread marks of the listed half-year mark their subjects whatever the case and ё`() = runTest {
        repository.subjects = AppResult.Success(listOf(recordbookSubject(name = "Тестовый предмет по химии")))
        marks.news.value = listOf(markNews("ТЕСТОВЫЙ ПРЕДМЁТ ПО ХИМИИ"), markNews("Другой предмет", half = StudyHalf(2025, 2)))
        val vm = loaded(); advanceUntilIdle()
        assertTrue(vm.uiState.value.row(42).unread)

        vm.selectPeriod(1L, 1); advanceUntilIdle()
        assertEquals(1, vm.uiState.value.selectedSemester)
        assertFalse(vm.uiState.value.row(42).unread)
    }

    @Test fun `a new unread mark reaches the open list without a request`() = runTest {
        val vm = loaded(); advanceUntilIdle()
        val requests = repository.programRequests to repository.subjectRequests.size
        assertFalse(vm.uiState.value.row(42).unread)

        marks.news.value = listOf(markNews("Тестовый предмет")); advanceUntilIdle()

        assertTrue(vm.uiState.value.row(42).unread)
        assertEquals(requests, repository.programRequests to repository.subjectRequests.size)
    }

    @Test fun `only a loaded list of the current half-year advances the My ITMO snapshot`() = runTest {
        val vm = loaded(); advanceUntilIdle()
        assertEquals(listOf(FakeMarkTrackingRepository.SeenMyItmo(TEST_HALF, 1L, 3, listOf(recordbookSubject()))), marks.seenMyItmo)

        vm.selectPeriod(1L, 2); advanceUntilIdle()
        assertEquals(1, marks.seenMyItmo.size)

        val failing = FakeMarkTrackingRepository()
        repository.subjects = AppResult.Failure(AppError.Network)
        val failed = RecordbookViewModel(repository, FakeBarsRepository(), FakeBarsPreference(),
            RecordbookSportResolver(sport), FixedAcademicTime(LocalDate(2026, 9, 7)), failing, FakeSheetScoresRepository())
        failed.refresh(RefreshMode.Silent); advanceUntilIdle()
        assertTrue(failed.uiState.value.body is RecordbookBody.Failed)
        assertTrue(failing.seenMyItmo.isEmpty())
    }

    // --- sources

    @Test fun `the sources sheet shows every source with the shown one first and opens the subject`() = runTest {
        repository.subjects = AppResult.Success(listOf(recordbookSubject().copy(rate = null, score = 35.0)))
        val sheets = FakeSheetScoresRepository(sheetScore(scope = ResourceScope(1, "Тестовый предмет", "2026-1"), value = "41,5"))
        val vm = loaded(model(sheets = sheets)); advanceUntilIdle()

        vm.openSources(42)
        val sources = vm.uiState.value.content.sources!!
        assertEquals(listOf(RecordbookSource.SHEET, RecordbookSource.BARS, RecordbookSource.MY_ITMO), sources.rows.map { it.source })
        assertEquals(listOf("41,5", null, "35"), sources.rows.map { it.value })
        assertEquals(listOf(true, false, false), sources.rows.map { it.shown })
        assertEquals(42L, sources.target.args.entryId)

        vm.closeSources()
        assertNull(vm.uiState.value.content.sources)
    }
}
