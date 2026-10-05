package dev.alllexey.itmowidgets.feature.settings.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.IcsFile
import dev.alllexey.itmowidgets.core.schedule.ScheduleExportRange
import dev.alllexey.itmowidgets.core.schedule.ScheduleIcsExport
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

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
    val uiState: StateFlow<IcsExportUiState> = mutableState.asStateFlow()

    private val eventQueue = EventQueue<IcsExportEvent>()
    val events: Flow<IcsExportEvent> = eventQueue.events

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
            IcsRangeKind.CUSTOM -> viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                eventQueue.send(IcsExportEvent.PickDates)
            }
        }
    }

    fun onDates(start: LocalDate, end: LocalDate) {
        if (mutableState.value is IcsExportUiState.Choose) {
            start(ScheduleExportRange.Custom(start, end))
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
            IcsRangeOption(kind, UiText.Resource(title), IcsDateLabels.range(range.dates(today)))
        return IcsExportUiState.Choose(
            listOf(
                option(IcsRangeKind.WEEK, R.string.ics_range_week, ScheduleExportRange.Week),
                option(IcsRangeKind.TWO_WEEKS, R.string.ics_range_two_weeks, ScheduleExportRange.TwoWeeks),
                IcsRangeOption(
                    IcsRangeKind.SEMESTER,
                    UiText.Resource(R.string.ics_range_semester),
                    UiText.Resource(
                        R.string.ics_range_until,
                        listOf(IcsDateLabels.day(ScheduleExportRange.Semester.dates(today).endInclusive))
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

    private fun label(range: ScheduleExportRange): UiText = IcsDateLabels.range(range.dates(time.today()))

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
            ?.let { (start, end) -> ScheduleExportRange.Custom(LocalDate.parse(start), LocalDate.parse(end)) }
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
