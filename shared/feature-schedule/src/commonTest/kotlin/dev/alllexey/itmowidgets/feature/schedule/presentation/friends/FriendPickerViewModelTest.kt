package dev.alllexey.itmowidgets.feature.schedule.presentation.friends

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.lesson.LessonChange
import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.navigation.FriendSelectionContract
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.schedule.FakeScheduleDays
import dev.alllexey.itmowidgets.feature.schedule.feedLesson
import dev.alllexey.itmowidgets.shared.feature.schedule.ScheduleStrings
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

class FriendPickerViewModelTest {

    private val main = TestMainDispatcher()
    private val monday = LocalDate(2026, 9, 7)
    private val time = FixedAcademicTime(LocalDateTime(monday, LocalTime(12, 22)))

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun whereSomeoneIsNowFollowsTheirDay() {
        val day = listOf(feedLesson("10:00", "11:30"), feedLesson("13:30", "15:00"))
        fun at(time: String) = FriendPickerViewModel.whereNow(day, LocalTime.parse(time))

        assertEquals(UiText.Res(ScheduleStrings.schedule_picker_first, listOf("10:00")), at("09:00"))
        assertEquals(UiText.Res(ScheduleStrings.schedule_picker_on_lesson, listOf("11:30")), at("10:15"))
        assertEquals(UiText.Res(ScheduleStrings.schedule_picker_gap, listOf("13:30")), at("12:22"))
        assertEquals(UiText.Res(ScheduleStrings.schedule_picker_done), at("16:00"))
        val cancelled = feedLesson("10:00", "11:30").copy(change = LessonChange(LocalDateTime(2026, 9, 6, 20, 14), cancelled = true))
        assertEquals(UiText.Res(ScheduleStrings.schedule_picker_no_lessons), FriendPickerViewModel.whereNow(listOf(cancelled), LocalTime(9, 0)))
    }

    @Test
    fun friendsWithAnOpenScheduleComeFirstAndAHiddenOneCannotBePicked() = runTest(main.dispatcher) {
        val days = FakeScheduleDays(LessonDay(monday, listOf(feedLesson("11:40", "13:10")), null))
        days.days(IVAN.isu).value = listOf(LessonDay(monday, listOf(feedLesson("13:30", "15:00")), null))
        val vm = FriendPickerViewModel(SavedStateHandle(), Friends(listOf(TIMUR, IVAN)), days, time)

        val state = vm.uiState.first { picker ->
            val rows = (picker.friends as? PickerFriends.Content)?.rows.orEmpty()
            rows.isNotEmpty() && rows.first().status != null && picker.own.status != null
        }

        val rows = (state.friends as PickerFriends.Content).rows
        assertEquals(listOf(IVAN.isu, TIMUR.isu), rows.map { it.user?.isu })
        assertEquals(UiText.Res(ScheduleStrings.schedule_picker_first, listOf("13:30")), rows[0].status)
        assertTrue(rows[0].selectable)
        assertFalse(rows[1].selectable)
        assertEquals(null, rows[1].status)
        assertEquals("M3205", rows[0].user?.group)
        assertTrue(state.own.selected)
        assertEquals(UiText.Res(ScheduleStrings.schedule_picker_on_lesson, listOf("13:10")), state.own.status)
        assertTrue(days.refreshes.any { it.userIsu == IVAN.isu })
        assertFalse(days.refreshes.any { it.userIsu == TIMUR.isu })
    }

    @Test
    fun theShownFriendIsMarkedAndWithoutTheConnectionThePickerIsGated() = runTest(main.dispatcher) {
        val state = SavedStateHandle(mapOf(FriendSelectionContract.ARG_SELECTED_ISU to IVAN.isu))
        val picked = FriendPickerViewModel(state, Friends(listOf(IVAN)), FakeScheduleDays(), time)
        val rows = (picked.uiState.first { it.friends is PickerFriends.Content }.friends as PickerFriends.Content).rows
        assertTrue(rows.single().selected)
        assertFalse(picked.uiState.value.own.selected)

        val gated = FriendPickerViewModel(SavedStateHandle(), Friends(state = LoadState.Disabled), FakeScheduleDays(), time)
        assertEquals(PickerFriends.Disabled, gated.uiState.first { it.friends == PickerFriends.Disabled }.friends)
    }

    private class Friends(
        friends: List<UserSummary> = emptyList(),
        state: LoadState<List<UserSummary>> = LoadState.Content(friends),
    ) : FriendRepository {
        private val list = MutableStateFlow(state)
        var refreshes = 0
        override fun observeFriendList(): Flow<LoadState<List<UserSummary>>> = list
        override fun observeCurrentUser(): Flow<UserSummary?> = flowOf(null)
        override suspend fun refreshFriendList() {
            refreshes++
        }
        override val currentFriends: List<UserSummary>? get() = (list.value as? LoadState.Content)?.value
    }

    private companion object {
        val IVAN = UserSummary(300001, "Козлов Иван", null, listOf(UserGroup("M3205", 2, "ФИТИП")), UserSharing(sport = true, schedule = true))
        val TIMUR = UserSummary(300004, "Абаев Тимур", null, emptyList(), UserSharing(sport = false, schedule = false))
    }
}
