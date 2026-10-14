package dev.alllexey.itmowidgets.feature.schedule.presentation.friends

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.lesson.DayLesson
import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.lesson.SportMark
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.model.primaryGroup
import dev.alllexey.itmowidgets.core.navigation.FriendSelectionContract
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.personInitials
import dev.alllexey.itmowidgets.core.text.userDisplayName
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleDays
import dev.alllexey.itmowidgets.feature.schedule.presentation.feed.SelectedUser
import dev.alllexey.itmowidgets.shared.feature.schedule.ScheduleStrings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.format
import kotlin.time.Duration.Companion.minutes

/**
 * The schedule's friend picker (docs/features/schedule.md): one tap on the own schedule or a friend whose schedule is
 * open to the viewer; a friend who hid it is listed dimmed and cannot be chosen. Each row says where the person is now,
 * from today's lessons of their schedule ([ScheduleDays], refreshed once per opening). Backend decides who is a friend
 * and whose schedule is open ([UserSummary.sharing]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FriendPickerViewModel(
    savedStateHandle: SavedStateHandle,
    private val friends: FriendRepository,
    private val days: ScheduleDays,
    private val time: AcademicTimeProvider,
) : ViewModel() {

    private val selectedIsu: Int? = savedStateHandle.get<Int>(FriendSelectionContract.ARG_SELECTED_ISU)
        ?.takeIf { it != FriendSelectionContract.NO_USER_ISU }

    /** Whose today has been refreshed (null: the own schedule), so an absent day means no lessons. */
    private val answered = MutableStateFlow<Set<Int?>>(emptySet())
    private val requested = mutableSetOf<Int?>()

    private val clock: Flow<Pair<LocalDate, LocalTime>> = flow {
        while (true) {
            val now = time.localNow()
            emit(now.date to now.time)
            delay(1.minutes)
        }
    }

    private val list: Flow<LoadState<List<UserSummary>>> = friends.observeFriendList()
        .onEach { state -> (state as? LoadState.Content)?.value?.filter { it.sharing.schedule }?.forEach { refreshToday(it.isu) } }

    private val todays: Flow<Map<Int?, List<DayLesson>>> = list.flatMapLatest { state ->
        val isus = listOf<Int?>(null) + (state as? LoadState.Content)?.value.orEmpty().filter { it.sharing.schedule }.map { it.isu }
        val today = time.today()
        combine(isus.map { isu -> days.observe(isu, today, today).map { isu to it } }) { pairs ->
            pairs.associate { (isu, day) -> isu to day.lessonsOf(today) }
        }
    }

    val uiState: StateFlow<FriendPickerUiState> = combine(
        list,
        friends.observeCurrentUser().onStart { emit(null) },
        todays.onStart { emit(emptyMap()) },
        answered,
        clock,
    ) { list, me, todays, answered, (_, now) ->
        fun status(isu: Int?) = todays[isu]?.takeIf { it.isNotEmpty() || isu in answered }?.let { whereNow(it, now) }
        FriendPickerUiState(
            own = PickerRow(
                user = null,
                title = UiText.Res(ScheduleStrings.schedule_picker_mine),
                person = me?.name,
                initials = me?.name?.let(::personInitials) ?: OWN_INITIALS,
                avatar = me?.pictureUrl,
                status = status(null),
                selectable = true,
                selected = selectedIsu == null,
            ),
            friends = when (list) {
                LoadState.Loading -> PickerFriends.Loading
                LoadState.Disabled -> PickerFriends.Disabled
                is LoadState.Error -> PickerFriends.Failed(list.error)
                is LoadState.Content -> PickerFriends.Content(
                    list.value.sortedWith(compareBy<UserSummary> { !it.sharing.schedule }.thenBy { it.name })
                        .map { friend -> friend.row(status(friend.isu).takeIf { friend.sharing.schedule }) },
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), initialState())

    init {
        refreshToday(null)
        viewModelScope.launch { friends.refreshFriendList() }
    }

    /** `Повторить`. */
    fun retry() {
        viewModelScope.launch { friends.refreshFriendList() }
    }

    private fun refreshToday(isu: Int?) {
        if (!requested.add(isu)) return
        val today = time.today()
        viewModelScope.launch {
            if (days.refresh(isu, today, today) is AppResult.Success) answered.update { it + isu }
        }
    }

    private fun UserSummary.row(status: UiText?): PickerRow {
        val group = primaryGroup()?.name
        return PickerRow(
            user = SelectedUser(isu, name, pictureUrl, group),
            title = userDisplayName(name, isu),
            person = name,
            initials = personInitials(name),
            avatar = pictureUrl,
            status = status,
            selectable = sharing.schedule,
            selected = isu == selectedIsu,
        )
    }

    private fun initialState() = FriendPickerUiState(
        own = PickerRow(null, UiText.Res(ScheduleStrings.schedule_picker_mine), null, OWN_INITIALS, null, null, true, selectedIsu == null),
        friends = PickerFriends.Loading,
    )

    private fun List<LessonDay>.lessonsOf(date: LocalDate) = firstOrNull { it.date == date }?.lessons.orEmpty()

    companion object {
        private const val OWN_INITIALS = "Я"
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        /** Where someone with today's [lessons] is at [now]: on a lesson, in a gap, before the first or done. */
        fun whereNow(lessons: List<DayLesson>, now: LocalTime): UiText {
            val live = lessons.filter { it.change?.cancelled != true && it.sport != SportMark.QUEUED }.sortedBy { it.start }
            val current = live.firstOrNull { it.start <= now && now < it.end }
            val next = live.firstOrNull { it.start > now }
            return when {
                live.isEmpty() -> UiText.Res(ScheduleStrings.schedule_picker_no_lessons)
                current != null -> UiText.Res(ScheduleStrings.schedule_picker_on_lesson, listOf(current.end.format(DateTexts.TIME)))
                next == null -> UiText.Res(ScheduleStrings.schedule_picker_done)
                live.first().start > now -> UiText.Res(ScheduleStrings.schedule_picker_first, listOf(next.start.format(DateTexts.TIME)))
                else -> UiText.Res(ScheduleStrings.schedule_picker_gap, listOf(next.start.format(DateTexts.TIME)))
            }
        }
    }
}
