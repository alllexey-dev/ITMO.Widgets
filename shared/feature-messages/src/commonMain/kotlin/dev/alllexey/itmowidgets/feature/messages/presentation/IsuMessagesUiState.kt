package dev.alllexey.itmowidgets.feature.messages.presentation

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.messages.domain.IsuFolder
import dev.alllexey.itmowidgets.feature.messages.domain.MessageBody

/**
 * The ISU messages screen: the [folder] tabs, the list of the chosen folder and, when [opened] is set, one message
 * over it. [unread] is ISU's counter of the inbox, [unreadText] the header's `3 непрочитанных`; [downloading] the
 * attachment on its way.
 */
data class IsuMessagesUiState(
    val folder: IsuFolder = IsuFolder.INBOX,
    val list: IsuMessagesList = IsuMessagesList.Loading,
    val unread: Int = 0,
    val unreadText: UiText? = null,
    val refreshing: Boolean = false,
    val opened: IsuMessageDetail? = null,
    val downloading: String? = null,
)

/** The list of one folder. */
sealed interface IsuMessagesList {
    data object Loading : IsuMessagesList

    /** No message in the folder: [title] and [text] say which folder. */
    data class Empty(val title: UiText, val text: UiText) : IsuMessagesList

    /** The first page failed; [signIn] when ITMO.ID no longer signs in to ISU. */
    data class Failed(val title: UiText, val text: UiText, val signIn: Boolean) : IsuMessagesList

    data class Messages(val rows: List<IsuMessageRow>, val more: MoreState) : IsuMessagesList
}

/** The end of the list: nothing more, `Показать ещё`, the next page on its way, or it failed. */
enum class MoreState { END, AVAILABLE, LOADING, FAILED }

/** One message in the list: [date] is the time today, the day this year, the full date before. */
data class IsuMessageRow(
    val id: Long,
    val sender: String,
    val subject: String,
    val preview: String,
    val date: String,
    val unread: Boolean,
    val attachments: Int,
)

/** One opened message; [unread] adds the note that ISU still counts it unread. */
data class IsuMessageDetail(
    val id: Long,
    val subject: String,
    val sender: String,
    val date: String,
    val body: MessageBody,
    val attachments: List<IsuAttachmentRow>,
    val unread: Boolean,
)

/** An attachment row: the file name and its [kind] (`PDF`, `Документ Word`, ...). */
data class IsuAttachmentRow(val id: String, val name: String, val kind: UiText)

sealed interface IsuMessagesEvent {
    /** Opens a downloaded attachment in the system viewer. */
    data class OpenFile(val uri: String, val mimeType: String) : IsuMessagesEvent

    data class Snackbar(val text: UiText) : IsuMessagesEvent
}
