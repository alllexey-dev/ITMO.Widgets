package dev.alllexey.itmowidgets.feature.settings.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.IcsFile
import dev.alllexey.itmowidgets.core.schedule.ScheduleExportRange
import dev.alllexey.itmowidgets.core.schedule.ScheduleIcsExport
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IcsExportViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val export = FakeIcsExport()
    private val saved = SavedStateHandle()

    @Test
    fun `the choice names the days of every range from today`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = IcsExportViewModel(export, Today, saved)

        assertEquals(
            IcsExportUiState.Choose(
                listOf(
                    IcsRangeOption(IcsRangeKind.WEEK, UiText.Resource(R.string.ics_range_week), UiText.Dynamic("2–8 октября")),
                    IcsRangeOption(IcsRangeKind.TWO_WEEKS, UiText.Resource(R.string.ics_range_two_weeks), UiText.Dynamic("2–15 октября")),
                    IcsRangeOption(
                        IcsRangeKind.SEMESTER, UiText.Resource(R.string.ics_range_semester),
                        UiText.Resource(R.string.ics_range_until, listOf("31 января"))
                    ),
                    IcsRangeOption(
                        IcsRangeKind.CUSTOM, UiText.Resource(R.string.ics_range_custom),
                        UiText.Resource(R.string.ics_range_custom_caption)
                    )
                )
            ),
            viewModel.state.value
        )
    }

    @Test
    fun `a range prepares the file and the ready state names the lessons' days`() = runTest(mainDispatcherRule.dispatcher) {
        export.gate = CompletableDeferred()
        export.result = AppResult.Success(FILE)
        val viewModel = IcsExportViewModel(export, Today, saved)

        viewModel.choose(IcsRangeKind.WEEK)
        runCurrent()
        assertEquals(IcsExportUiState.Preparing, viewModel.state.value)
        export.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf<ScheduleExportRange>(ScheduleExportRange.Week), export.ranges)
        assertEquals(IcsExportUiState.Ready(FILE, UiText.Dynamic("2–8 октября")), viewModel.state.value)
    }

    @Test
    fun `own dates come from the picker`() = runTest(mainDispatcherRule.dispatcher) {
        export.result = AppResult.Success(FILE)
        val viewModel = IcsExportViewModel(export, Today, saved)

        viewModel.choose(IcsRangeKind.CUSTOM)
        assertEquals(IcsExportEvent.PickDates, viewModel.events.first())
        viewModel.onDates(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4))
        advanceUntilIdle()

        assertEquals(listOf<ScheduleExportRange>(ScheduleExportRange.Custom(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4))), export.ranges)
        assertEquals(IcsExportUiState.Ready(FILE, UiText.Dynamic("28 сентября – 4 октября")), viewModel.state.value)
    }

    @Test
    fun `an empty range offers another one`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = IcsExportViewModel(export, Today, saved)

        viewModel.choose(IcsRangeKind.TWO_WEEKS)
        advanceUntilIdle()
        assertEquals(IcsExportUiState.Empty, viewModel.state.value)

        viewModel.chooseAnother()
        assertEquals(IcsRangeKind.WEEK, (viewModel.state.value as IcsExportUiState.Choose).options.first().kind)
    }

    @Test
    fun `a failure is shown and retried with the same range`() = runTest(mainDispatcherRule.dispatcher) {
        export.result = AppResult.Failure(AppError.Network)
        val viewModel = IcsExportViewModel(export, Today, saved)

        viewModel.choose(IcsRangeKind.SEMESTER)
        advanceUntilIdle()
        assertEquals(IcsExportUiState.Failed(AppError.Network), viewModel.state.value)

        export.result = AppResult.Success(FILE)
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(listOf<ScheduleExportRange>(ScheduleExportRange.Semester, ScheduleExportRange.Semester), export.ranges)
        assertEquals(IcsExportUiState.Ready(FILE, UiText.Dynamic("2 октября 2026 – 31 января 2027")), viewModel.state.value)
    }

    @Test
    fun `a recreated sheet shows the written file without writing it again`() = runTest(mainDispatcherRule.dispatcher) {
        export.result = AppResult.Success(FILE)
        IcsExportViewModel(export, Today, saved).choose(IcsRangeKind.WEEK)
        advanceUntilIdle()

        val recreated = IcsExportViewModel(export, Today, saved)

        assertEquals(IcsExportUiState.Ready(FILE, UiText.Dynamic("2–8 октября")), recreated.state.value)
        assertEquals(1, export.ranges.size)
    }

    @Test
    fun `a sheet recreated while preparing writes the file again`() = runTest(mainDispatcherRule.dispatcher) {
        export.gate = CompletableDeferred()
        IcsExportViewModel(export, Today, saved).choose(IcsRangeKind.WEEK)
        runCurrent()

        export.gate = null
        export.result = AppResult.Success(FILE)
        val recreated = IcsExportViewModel(export, Today, saved)
        advanceUntilIdle()

        assertEquals(IcsExportUiState.Ready(FILE, UiText.Dynamic("2–8 октября")), recreated.state.value)
    }

    @Test
    fun `date labels name one day, a month, two months and two years`() {
        val day = LocalDate.of(2026, 10, 5)
        assertEquals(UiText.Dynamic("5 октября"), IcsDateLabels.range(day..day))
        assertEquals(UiText.Dynamic("5–11 октября"), IcsDateLabels.range(day..day.plusDays(6)))
        assertEquals(UiText.Dynamic("28 сентября – 4 октября"), IcsDateLabels.range(LocalDate.of(2026, 9, 28)..LocalDate.of(2026, 10, 4)))
        assertEquals(
            UiText.Dynamic("28 декабря 2026 – 3 января 2027"),
            IcsDateLabels.range(LocalDate.of(2026, 12, 28)..LocalDate.of(2027, 1, 3))
        )
    }

    private class FakeIcsExport : ScheduleIcsExport {
        var result: AppResult<IcsFile?> = AppResult.Success(null)
        var gate: CompletableDeferred<Unit>? = null
        val ranges = mutableListOf<ScheduleExportRange>()

        override suspend fun export(range: ScheduleExportRange): AppResult<IcsFile?> {
            ranges += range
            gate?.await()
            return result
        }
    }

    private object Today : AcademicTimeProvider by FixedAcademicTime(LocalDateTime.of(2026, 10, 2, 9, 0))

    private companion object {
        val FILE = IcsFile("content://test/a.ics", "a.ics", 23)
    }
}
