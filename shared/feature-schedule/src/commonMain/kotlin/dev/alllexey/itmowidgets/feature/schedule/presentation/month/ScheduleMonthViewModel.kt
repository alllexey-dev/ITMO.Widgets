package dev.alllexey.itmowidgets.feature.schedule.presentation.month

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.lesson.SportMark
import dev.alllexey.itmowidgets.core.navigation.FriendSelectionContract
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleDays
import dev.alllexey.itmowidgets.shared.feature.schedule.ScheduleStrings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.format
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minusMonth
import kotlinx.datetime.plus
import kotlinx.datetime.plusMonth
import kotlinx.datetime.yearMonth

/**
 * The month sheet (docs/features/schedule.md): the month of the `ARG_DATE` day, its lessons from [ScheduleDays] of
 * `ARG_USER_ISU` (the own schedule without one) as dots; each month shown is refreshed once in the background. No day
 * summary and no quick chips (the spec): the feed shows the day after `Открыть`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleMonthViewModel(
    savedStateHandle: SavedStateHandle,
    private val days: ScheduleDays,
    private val time: AcademicTimeProvider,
) : ViewModel() {

    private val userIsu: Int? = savedStateHandle.get<Int>(ARG_USER_ISU)?.takeIf { it != FriendSelectionContract.NO_USER_ISU }
    private val selected = MutableStateFlow(
        savedStateHandle.get<String>(ARG_DATE)?.let(LocalDate::parse) ?: time.today(),
    )
    private val month = MutableStateFlow(selected.value.yearMonth)
    private val refreshed = mutableSetOf<YearMonth>()

    val uiState: StateFlow<ScheduleMonthUiState> = combine(
        month.flatMapLatest { shown ->
            refreshOnce(shown)
            days.observe(userIsu, shown.firstDay, shown.lastDay).onStart { emit(emptyList()) }
        },
        month,
        selected,
    ) { cached, shown, chosen -> scheduleMonthState(shown, chosen, cached, time.today()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), scheduleMonthState(month.value, selected.value, emptyList(), time.today()))

    fun previousMonth() {
        month.value = month.value.minusMonth()
    }

    fun nextMonth() {
        month.value = month.value.plusMonth()
    }

    fun select(date: LocalDate) {
        selected.value = date
    }

    private fun refreshOnce(shown: YearMonth) {
        if (!refreshed.add(shown)) return
        viewModelScope.launch { days.refresh(userIsu, shown.firstDay, shown.lastDay) }
    }

    companion object {
        const val ARG_DATE = "month_date"
        const val ARG_USER_ISU = "month_user_isu"
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** The month sheet of [shown] with [chosen] selected and the dots of the [cached] days, at [today]. Pure. */
internal fun scheduleMonthState(
    shown: YearMonth,
    chosen: LocalDate,
    cached: List<LessonDay>,
    today: LocalDate,
): ScheduleMonthUiState {
    val counts = cached.associate { day ->
        day.date to day.lessons.count { it.change?.cancelled != true && it.sport != SportMark.QUEUED }
    }
    val first = shown.firstDay
    return ScheduleMonthUiState(
        title = DateTexts.MONTH_YEAR.format(shown).replaceFirstChar { it.uppercaseChar() },
        weekdays = DayOfWeek.entries.map { DateTexts.shortWeekday(it).replaceFirstChar { c -> c.uppercaseChar() } },
        leading = first.dayOfWeek.isoDayNumber - 1,
        days = generateSequence(first) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it <= shown.lastDay }.map { date ->
            MonthDay(
                date = date,
                number = date.format(DateTexts.DAY),
                lessons = (counts[date] ?: 0).coerceAtMost(MAX_MONTH_DOTS),
                today = date == today,
                past = date < today,
                selected = date == chosen,
            )
        }.toList(),
        selected = chosen,
        open = UiText.Res(ScheduleStrings.schedule_month_open, listOf(chosen.format(DateTexts.WEEKDAY_DAY_MONTH))),
    )
}

private const val MAX_MONTH_DOTS = 4
