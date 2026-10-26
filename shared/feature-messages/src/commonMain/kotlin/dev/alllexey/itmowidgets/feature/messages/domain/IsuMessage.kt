package dev.alllexey.itmowidgets.feature.messages.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** Where a message lies in ISU: the inbox holds the current calendar year, the archive the years before. */
enum class IsuFolder { INBOX, ARCHIVE }

/**
 * One received ISU message as the inbox list gives it. [read] is ISU's own flag: opening a message in the app does not
 * change it, the app never calls the ISU request that marks a message read.
 */
data class IsuMessage(
    val id: Long,
    val sender: String,
    val subject: String,
    val date: LocalDate?,
    val time: LocalTime?,
    val read: Boolean,
    val body: MessageBody,
    val attachments: List<IsuAttachment>,
) {
    override fun toString(): String = "IsuMessage($id)"
}

/** A file of a message; [id] is ISU's 32-character file hash that the download names. */
data class IsuAttachment(val id: String, val name: String, val mimeType: String?) {
    override fun toString(): String = "IsuAttachment($mimeType)"
}

/** One page of a folder: up to ten messages, [unread] is ISU's counter of the folder. */
data class IsuMessagesPage(val messages: List<IsuMessage>, val unread: Int, val last: Boolean)

/** A downloaded attachment saved on the device, ready for the system viewer: [uri] is `content://` or `file://`. */
data class AttachmentFile(val uri: String, val mimeType: String)
