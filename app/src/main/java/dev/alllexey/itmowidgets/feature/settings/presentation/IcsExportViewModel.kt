package dev.alllexey.itmowidgets.feature.settings.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.IcsFile
import dev.alllexey.itmowidgets.core.schedule.ScheduleExportRange
import dev.alllexey.itmowidgets.core.schedule.ScheduleIcsExport
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate

/** A row of the range choice: its title and the days it covers from today. */
data class IcsRangeOption(val kind: IcsRangeKind, val title: UiText, val dates: UiText)

enum class IcsRangeKind { WEEK, TWO_WEEKS, SEMESTER, CUSTOM }

sealed interface IcsExportUiState {
    data class Choose(val options: List<IcsRangeOption>) : IcsExportUiState

    data object Preparing : IcsExportUiState

    /** [dates] names the exported days, as «5–11 октября». */
    data class Ready(val file: IcsFile, val dates: UiText) : IcsExportUiState

    data object Empty : IcsExportUiState

    data class Failed(val error: AppError) : IcsExportUiState
}

sealed interface IcsExportEvent {
    /** Opens the date range picker; its answer comes back through `onDates`. */
    data object PickDates : IcsExportEvent
}

/**
 * The «Выгрузить в .ics» sheet: a range is chosen, the file is written, then shared. The chosen range and a written
 * file are kept in [SavedStateHandle], so recreation shows the same state and a process death writes the file again.
 */
@HiltViewModel
class IcsExportViewModel @Inject constructor(
    private val export: ScheduleIcsExport,
    private val time: AcademicTimeProvider,
    private val saved: SavedStateHandle
) : ViewModel() {

    private val mutableState = MutableStateFlow<IcsExportUiState>(choice())
    val state: StateFlow<IcsExportUiState> = mutableState.asStateFlow()

    private val eventChannel = Channel<IcsExportEvent>(Channel.BUFFERED)
    val events: Flow<IcsExportEvent> = eventChannel.receiveAsFlow()

    private var job: Job? = null

    init {
        val range = savedRange()
        val file = savedFile()
        when {
            range != null && file != null -> mutableState.value = IcsExportUiState.Ready(file, label(range))
            range != null -> run(range)
        }
    }

    fun choose(kind: IcsRangeKind) {
        if (mutableState.value !is IcsExportUiState.Choose) return
        when (kind) {
            IcsRangeKind.WEEK -> start(ScheduleExportRange.Week)
            IcsRangeKind.TWO_WEEKS -> start(ScheduleExportRange.TwoWeeks)
            IcsRangeKind.SEMESTER -> start(ScheduleExportRange.Semester)
            IcsRangeKind.CUSTOM -> eventChannel.trySend(IcsExportEvent.PickDates)
        }
    }

    fun onDates(start: LocalDate, end: LocalDate) {
        if (mutableState.value is IcsExportUiState.Choose) {
            start(ScheduleExportRange.Custom(start.toKotlinLocalDate(), end.toKotlinLocalDate()))
        }
    }

    fun retry() {
        savedRange()?.let(::run)
    }

    /** Back to the range choice, forgetting the range and the file. */
    fun chooseAnother() {
        job?.cancel()
        saved.remove<String>(RANGE)
        clearFile()
        mutableState.value = choice()
    }

    private fun start(range: ScheduleExportRange) {
        saveRange(range)
        run(range)
    }

    private fun run(range: ScheduleExportRange) {
        job?.cancel()
        clearFile()
        mutableState.value = IcsExportUiState.Preparing
        job = viewModelScope.launch {
            mutableState.value = when (val result = export.export(range)) {
                is AppResult.Success -> result.value?.let { file ->
                    saveFile(file)
                    IcsExportUiState.Ready(file, label(range))
                } ?: IcsExportUiState.Empty
                is AppResult.Failure -> IcsExportUiState.Failed(result.error)
            }
        }
    }

    private fun choice(): IcsExportUiState.Choose {
        val today = time.today()
        fun option(kind: IcsRangeKind, title: Int, range: ScheduleExportRange) =
            IcsRangeOption(kind, UiText.Resource(title), IcsDateLabels.range(range.javaDates(today)))
        return IcsExportUiState.Choose(
            listOf(
                option(IcsRangeKind.WEEK, R.string.ics_range_week, ScheduleExportRange.Week),
                option(IcsRangeKind.TWO_WEEKS, R.string.ics_range_two_weeks, ScheduleExportRange.TwoWeeks),
                IcsRangeOption(
                    IcsRangeKind.SEMESTER,
                    UiText.Resource(R.string.ics_range_semester),
                    UiText.Resource(
                        R.string.ics_range_until,
                        listOf(IcsDateLabels.day(ScheduleExportRange.Semester.javaDates(today).endInclusive))
                    )
                ),
                IcsRangeOption(
                    IcsRangeKind.CUSTOM,
                    UiText.Resource(R.string.ics_range_custom),
                    UiText.Resource(R.string.ics_range_custom_caption)
                )
            )
        )
    }

    private fun label(range: ScheduleExportRange): UiText = IcsDateLabels.range(range.javaDates(time.today()))

    private fun ScheduleExportRange.javaDates(today: kotlinx.datetime.LocalDate): ClosedRange<LocalDate> =
        dates(today).let { it.start.toJavaLocalDate()..it.endInclusive.toJavaLocalDate() }

    private fun saveRange(range: ScheduleExportRange) {
        saved[RANGE] = when (range) {
            ScheduleExportRange.Week -> "week"
            ScheduleExportRange.TwoWeeks -> "two_weeks"
            ScheduleExportRange.Semester -> "semester"
            is ScheduleExportRange.Custom -> "${range.start}/${range.end}"
        }
    }

    private fun savedRange(): ScheduleExportRange? = when (val value = saved.get<String>(RANGE)) {
        null -> null
        "week" -> ScheduleExportRange.Week
        "two_weeks" -> ScheduleExportRange.TwoWeeks
        "semester" -> ScheduleExportRange.Semester
        else -> value.split('/').takeIf { it.size == 2 }
            ?.let { (start, end) -> ScheduleExportRange.Custom(kotlinx.datetime.LocalDate.parse(start), kotlinx.datetime.LocalDate.parse(end)) }
    }

    private fun saveFile(file: IcsFile) {
        saved[FILE_URI] = file.uri
        saved[FILE_NAME] = file.name
        saved[FILE_LESSONS] = file.lessons
    }

    private fun savedFile(): IcsFile? {
        val uri = saved.get<String>(FILE_URI) ?: return null
        return IcsFile(uri, saved.get<String>(FILE_NAME).orEmpty(), saved.get<Int>(FILE_LESSONS) ?: 0)
    }

    private fun clearFile() {
        saved.remove<String>(FILE_URI)
        saved.remove<String>(FILE_NAME)
        saved.remove<Int>(FILE_LESSONS)
    }

    private companion object {
        const val RANGE = "ics_range"
        const val FILE_URI = "ics_file_uri"
        const val FILE_NAME = "ics_file_name"
        const val FILE_LESSONS = "ics_file_lessons"
    }
}

/** Russian day ranges: «5 октября», «5–11 октября», «28 сентября – 4 октября», years only across a year's end. */
object IcsDateLabels {
    private val RUSSIAN: Locale = Locale.forLanguageTag("ru")
    private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", RUSSIAN)
    private val DAY_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", RUSSIAN)

    fun day(date: LocalDate): String = DAY.format(date)

    fun range(dates: ClosedRange<LocalDate>): UiText {
        val start = dates.start
        val end = dates.endInclusive
        val text = when {
            start == end -> DAY.format(start)
            start.year != end.year -> "${DAY_YEAR.format(start)} – ${DAY_YEAR.format(end)}"
            start.month == end.month -> "${start.dayOfMonth}–${DAY.format(end)}"
            else -> "${DAY.format(start)} – ${DAY.format(end)}"
        }
        return UiText.Dynamic(text)
    }
}
