package dev.alllexey.itmowidgets.feature.messages.presentation

import dev.alllexey.itmowidgets.core.text.RichText
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.messages.domain.IsuFolder

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

    /** The first page failed; [signIn] when ITMO.ID does not sign in to ISU: the screen offers `Войти через ITMO.ID`. */
    data class Failed(val title: UiText, val text: UiText, val signIn: Boolean) : IsuMessagesList

    /** The loaded pages by month, newest first; the end of the list asks for the next page by itself. */
    data class Messages(val sections: List<IsuMessageSection>, val more: MoreState) : IsuMessagesList {
        val rows: List<IsuMessageRow> get() = sections.flatMap { it.rows }
    }
}

/**
 * The messages of one month: [title] is `Октябрь` this year, `Декабрь 2025` before. Undated messages join the month
 * above them; at the very top they have a section of their own with no title.
 */
data class IsuMessageSection(val title: String?, val rows: List<IsuMessageRow>)

/** The end of the list: nothing more, a next page to load, the next page on its way, or it failed (`Повторить`). */
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
    val body: RichText,
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
