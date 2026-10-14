package dev.alllexey.itmowidgets.feature.schedule.presentation.friends

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.schedule.presentation.feed.SelectedUser

/** The picker: the [own] schedule first, then the [friends]. */
data class FriendPickerUiState(val own: PickerRow, val friends: PickerFriends)

/** The friends part of the picker. */
sealed interface PickerFriends {
    data object Loading : PickerFriends

    /** Friends need the connection to ITMO.Widgets: a gate with `Подключить`. */
    data object Disabled : PickerFriends

    data class Failed(val error: AppError) : PickerFriends

    /** Empty: `Друзей пока нет`. */
    data class Content(val rows: List<PickerRow>) : PickerFriends
}

/**
 * One choice: [user] (null for the own schedule), [title] and the avatar ([person]'s photo [avatar] or [initials];
 * [person] is null while the own name is unknown), [status] where they
 * are now (`на паре до 13:10`, `окно до 15:20`), [selectable] false for a friend who hid the schedule (dimmed, no
 * reason given), [selected] the one shown now.
 */
data class PickerRow(
    val user: SelectedUser?,
    val title: UiText,
    val person: String?,
    val initials: String,
    val avatar: String?,
    val status: UiText?,
    val selectable: Boolean,
    val selected: Boolean,
)
