package dev.alllexey.itmowidgets.feature.settings.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.IcsFile
import dev.alllexey.itmowidgets.core.schedule.ScheduleExportRange
import dev.alllexey.itmowidgets.core.schedule.ScheduleIcsExport
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.shared.feature.settings.Res
import dev.alllexey.itmowidgets.shared.feature.settings.ics_range_custom
import dev.alllexey.itmowidgets.shared.feature.settings.ics_range_custom_caption
import dev.alllexey.itmowidgets.shared.feature.settings.ics_range_semester
import dev.alllexey.itmowidgets.shared.feature.settings.ics_range_two_weeks
import dev.alllexey.itmowidgets.shared.feature.settings.ics_range_until
import dev.alllexey.itmowidgets.shared.feature.settings.ics_range_week
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlinx.datetime.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

@OptIn(ExperimentalCoroutinesApi::class)
class IcsExportViewModelTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    private val export = FakeIcsExport()
    private val saved = SavedStateHandle()

    @Test
    fun theChoiceNamesTheDaysOfEveryRangeFromToday() = runTest(main.dispatcher) {
        val viewModel = IcsExportViewModel(export, Today, saved)

        assertEquals(
            IcsExportUiState.Choose(
                listOf(
                    IcsRangeOption(IcsRangeKind.WEEK, UiText.Res(Res.string.ics_range_week), UiText.Dynamic("2–8 октября")),
                    IcsRangeOption(IcsRangeKind.TWO_WEEKS, UiText.Res(Res.string.ics_range_two_weeks), UiText.Dynamic("2–15 октября")),
                    IcsRangeOption(
                        IcsRangeKind.SEMESTER, UiText.Res(Res.string.ics_range_semester),
                        UiText.Res(Res.string.ics_range_until, listOf("31 января"))
                    ),
                    IcsRangeOption(
                        IcsRangeKind.CUSTOM, UiText.Res(Res.string.ics_range_custom),
                        UiText.Res(Res.string.ics_range_custom_caption)
                    )
                )
            ),
            viewModel.uiState.value
        )
    }

    @Test
    fun aRangePreparesTheFileAndTheReadyStateNamesTheLessonsDays() = runTest(main.dispatcher) {
        export.gate = CompletableDeferred()
        export.result = AppResult.Success(FILE)
        val viewModel = IcsExportViewModel(export, Today, saved)

        viewModel.choose(IcsRangeKind.WEEK)
        runCurrent()
        assertEquals(IcsExportUiState.Preparing, viewModel.uiState.value)
        export.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf<ScheduleExportRange>(ScheduleExportRange.Week), export.ranges)
        assertEquals(IcsExportUiState.Ready(FILE, UiText.Dynamic("2–8 октября")), viewModel.uiState.value)
    }

    @Test
    fun ownDatesComeFromThePicker() = runTest(main.dispatcher) {
        export.result = AppResult.Success(FILE)
        val viewModel = IcsExportViewModel(export, Today, saved)

        viewModel.choose(IcsRangeKind.CUSTOM)
        assertEquals(IcsExportEvent.PickDates, viewModel.events.first())
        viewModel.onDates(LocalDate(2026, 9, 28), LocalDate(2026, 10, 4))
        advanceUntilIdle()

        assertEquals(listOf<ScheduleExportRange>(ScheduleExportRange.Custom(LocalDate(2026, 9, 28), LocalDate(2026, 10, 4))), export.ranges)
        assertEquals(IcsExportUiState.Ready(FILE, UiText.Dynamic("28 сентября – 4 октября")), viewModel.uiState.value)
    }

    @Test
    fun anEmptyRangeOffersAnotherOne() = runTest(main.dispatcher) {
        val viewModel = IcsExportViewModel(export, Today, saved)

        viewModel.choose(IcsRangeKind.TWO_WEEKS)
        advanceUntilIdle()
        assertEquals(IcsExportUiState.Empty, viewModel.uiState.value)

        viewModel.chooseAnother()
        assertEquals(IcsRangeKind.WEEK, (viewModel.uiState.value as IcsExportUiState.Choose).options.first().kind)
    }

    @Test
    fun aFailureIsShownAndRetriedWithTheSameRange() = runTest(main.dispatcher) {
        export.result = AppResult.Failure(AppError.Network)
        val viewModel = IcsExportViewModel(export, Today, saved)

        viewModel.choose(IcsRangeKind.SEMESTER)
        advanceUntilIdle()
        assertEquals(IcsExportUiState.Failed(AppError.Network), viewModel.uiState.value)

        export.result = AppResult.Success(FILE)
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(listOf<ScheduleExportRange>(ScheduleExportRange.Semester, ScheduleExportRange.Semester), export.ranges)
        assertEquals(IcsExportUiState.Ready(FILE, UiText.Dynamic("2 октября 2026 – 31 января 2027")), viewModel.uiState.value)
    }

    @Test
    fun aRecreatedSheetShowsTheWrittenFileWithoutWritingItAgain() = runTest(main.dispatcher) {
        export.result = AppResult.Success(FILE)
        IcsExportViewModel(export, Today, saved).choose(IcsRangeKind.WEEK)
        advanceUntilIdle()

        val recreated = IcsExportViewModel(export, Today, saved)

        assertEquals(IcsExportUiState.Ready(FILE, UiText.Dynamic("2–8 октября")), recreated.uiState.value)
        assertEquals(1, export.ranges.size)
    }

    @Test
    fun aSheetRecreatedWhilePreparingWritesTheFileAgain() = runTest(main.dispatcher) {
        export.gate = CompletableDeferred()
        IcsExportViewModel(export, Today, saved).choose(IcsRangeKind.WEEK)
        runCurrent()

        export.gate = null
        export.result = AppResult.Success(FILE)
        val recreated = IcsExportViewModel(export, Today, saved)
        advanceUntilIdle()

        assertEquals(IcsExportUiState.Ready(FILE, UiText.Dynamic("2–8 октября")), recreated.uiState.value)
    }

    @Test
    fun dateLabelsNameOneDayAMonthTwoMonthsAndTwoYears() {
        val day = LocalDate(2026, 10, 5)
        assertEquals(UiText.Dynamic("5 октября"), IcsDateLabels.range(day..day))
        assertEquals(UiText.Dynamic("5–11 октября"), IcsDateLabels.range(day..day.plus(6, DateTimeUnit.DAY)))
        assertEquals(UiText.Dynamic("28 сентября – 4 октября"), IcsDateLabels.range(LocalDate(2026, 9, 28)..LocalDate(2026, 10, 4)))
        assertEquals(
            UiText.Dynamic("28 декабря 2026 – 3 января 2027"),
            IcsDateLabels.range(LocalDate(2026, 12, 28)..LocalDate(2027, 1, 3))
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

    private object Today : AcademicTimeProvider by FixedAcademicTime(LocalDateTime(2026, 10, 2, 9, 0))

    private companion object {
        val FILE = IcsFile("content://test/a.ics", "a.ics", 23)
    }
}
