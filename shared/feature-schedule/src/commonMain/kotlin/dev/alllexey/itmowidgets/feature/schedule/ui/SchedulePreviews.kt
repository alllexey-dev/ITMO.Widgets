package dev.alllexey.itmowidgets.feature.schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.schedule.ui.feed.ScheduleFeedActions
import dev.alllexey.itmowidgets.feature.schedule.ui.feed.ScheduleFeedScreen
import dev.alllexey.itmowidgets.feature.schedule.ui.friends.FriendPickerActions
import dev.alllexey.itmowidgets.feature.schedule.ui.friends.FriendPickerSheetContent
import dev.alllexey.itmowidgets.feature.schedule.ui.lesson.LessonSheetActions
import dev.alllexey.itmowidgets.feature.schedule.ui.lesson.LessonSheetContent
import dev.alllexey.itmowidgets.feature.schedule.ui.month.ScheduleMonthActions
import dev.alllexey.itmowidgets.feature.schedule.ui.month.ScheduleMonthSheetContent
import dev.alllexey.itmowidgets.feature.schedule.ui.preview.ScheduleSamples

// The schedule at the design lab's axes; the names are `<Screen>_<state>`. The samples are Monday 7 September 2026,
// 12:22 (ScheduleSamples).

private const val SCREEN_HEIGHT = 900

private fun actions(pushed: Boolean = false) = ScheduleFeedActions(onBack = if (pushed) ({}) else null)

/** The sheet's colour under the body, as the shell's sheet draws it. */
@Composable
private fun SheetPreview(content: @Composable () -> Unit) = ItmoPreview {
    Box(Modifier.background(ItmoTheme.colorScheme.surfaceContainerLow)) { content() }
}

internal class ScheduleFeedTodayPreview {
    @Preview(name = "today", heightDp = SCREEN_HEIGHT)
    @Composable
    fun ScheduleFeed() = ItmoPreview { ScheduleFeedScreen(ScheduleSamples.Feed, actions()) }
}

internal class ScheduleFeedPastDaysPreview {
    @Preview(name = "past-days", heightDp = SCREEN_HEIGHT)
    @Composable
    fun ScheduleFeed() = ItmoPreview { ScheduleFeedScreen(ScheduleSamples.PastDays, actions()) }
}

internal class ScheduleFeedNextWeekPreview {
    @Preview(name = "next-week", heightDp = SCREEN_HEIGHT)
    @Composable
    fun ScheduleFeed() = ItmoPreview { ScheduleFeedScreen(ScheduleSamples.NextWeek, actions()) }
}

internal class ScheduleFeedFriendPreview {
    @Preview(name = "friend", heightDp = SCREEN_HEIGHT)
    @Composable
    fun ScheduleFeed() = ItmoPreview { ScheduleFeedScreen(ScheduleSamples.FriendFeed, actions()) }
}

internal class ScheduleFeedUserPreview {
    @Preview(name = "user", heightDp = SCREEN_HEIGHT)
    @Composable
    fun ScheduleFeed() = ItmoPreview { ScheduleFeedScreen(ScheduleSamples.FriendFeed.copy(friend = null, canPickFriend = false), actions(pushed = true)) }
}

internal class ScheduleFeedOverlapPreview {
    @Preview(name = "overlap", heightDp = SCREEN_HEIGHT)
    @Composable
    fun ScheduleFeed() = ItmoPreview { ScheduleFeedScreen(ScheduleSamples.Overlap, actions()) }
}

internal class ScheduleFeedEmptyPreview {
    @Preview(name = "empty", heightDp = SCREEN_HEIGHT)
    @Composable
    fun ScheduleFeed() = ItmoPreview { ScheduleFeedScreen(ScheduleSamples.Empty, actions()) }
}

internal class ScheduleFeedErrorPreview {
    @Preview(name = "error", heightDp = SCREEN_HEIGHT)
    @Composable
    fun ScheduleFeed() = ItmoPreview { ScheduleFeedScreen(ScheduleSamples.Failed, actions()) }
}

internal class ScheduleMonthLessonsPreview {
    @Preview(name = "lessons")
    @Composable
    fun ScheduleMonth() = SheetPreview { ScheduleMonthSheetContent(ScheduleSamples.Month, ScheduleMonthActions()) }
}

internal class ScheduleMonthFreeDayPreview {
    @Preview(name = "free-day")
    @Composable
    fun ScheduleMonth() = SheetPreview { ScheduleMonthSheetContent(ScheduleSamples.MonthFreeDay, ScheduleMonthActions()) }
}

internal class FriendPickerFriendsPreview {
    @Preview(name = "friends")
    @Composable
    fun FriendPicker() = SheetPreview { FriendPickerSheetContent(ScheduleSamples.Picker, FriendPickerActions()) }
}

internal class FriendPickerGatePreview {
    @Preview(name = "gate")
    @Composable
    fun FriendPicker() = SheetPreview { FriendPickerSheetContent(ScheduleSamples.PickerDisabled, FriendPickerActions()) }
}

internal class FriendPickerEmptyPreview {
    @Preview(name = "empty")
    @Composable
    fun FriendPicker() = SheetPreview { FriendPickerSheetContent(ScheduleSamples.PickerEmpty, FriendPickerActions()) }
}

internal class LessonSheetNowPreview {
    @Preview(name = "now", heightDp = SCREEN_HEIGHT)
    @Composable
    fun LessonSheet() = SheetPreview { LessonSheetContent(ScheduleSamples.SheetNow, LessonSheetActions()) }
}

internal class LessonSheetOnlinePreview {
    @Preview(name = "online")
    @Composable
    fun LessonSheet() = SheetPreview { LessonSheetContent(ScheduleSamples.SheetOnline, LessonSheetActions()) }
}

internal class LessonSheetChangedPreview {
    @Preview(name = "changed", heightDp = SCREEN_HEIGHT)
    @Composable
    fun LessonSheet() = SheetPreview { LessonSheetContent(ScheduleSamples.SheetChanged, LessonSheetActions()) }
}

internal class LessonSheetSportPreview {
    @Preview(name = "sport")
    @Composable
    fun LessonSheet() = SheetPreview { LessonSheetContent(ScheduleSamples.SheetSport, LessonSheetActions()) }
}
