package dev.alllexey.itmowidgets.feature.messages.presentation

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.messages.domain.IsuFolder
import dev.alllexey.itmowidgets.feature.messages.domain.MessageBlock
import dev.alllexey.itmowidgets.feature.messages.domain.MessageBody
import dev.alllexey.itmowidgets.feature.messages.domain.MessageSpan
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.messages.MessagesPlurals
import dev.alllexey.itmowidgets.shared.feature.messages.MessagesStrings

/** Fictional states of the messages screen for previews, JVM screenshots and the iOS snapshot tests. */
object IsuMessagesSamples {

    private val rows = listOf(
        IsuMessageRow(
            id = 6, sender = "Белова Ольга Сергеевна", subject = "Перенос лекции по матанализу",
            preview = "Здравствуйте! Лекция в среду переносится на пятницу, 13:30, аудитория 1404 (Кронверкский, 49).",
            date = "09:12", unread = true, attachments = 0,
        ),
        IsuMessageRow(
            id = 5, sender = "Деканат ФИКТ", subject = "Расписание консультаций перед рубежным контролем",
            preview = "Уважаемые студенты! Во вложении расписание консультаций на неделю. Обратите внимание:",
            date = "24 окт.", unread = true, attachments = 1,
        ),
        IsuMessageRow(
            id = 4, sender = "Громов Пётр Андреевич", subject = "Журнал лабораторных работ",
            preview = "Журнал лабораторных открыт: ссылка на курс. Сдача первой лабораторной до 3 ноября.",
            date = "20 окт.", unread = false, attachments = 0,
        ),
        IsuMessageRow(
            id = 3, sender = "Центр дополнительного образования", subject = "Осенний набор на курсы",
            preview = "Открыт набор на курсы английского и немецкого языков. Подробности и запись: https://example.org/courses",
            date = "14 окт.", unread = false, attachments = 2,
        ),
    )

    val Inbox = IsuMessagesUiState(
        list = IsuMessagesList.Messages(rows, MoreState.AVAILABLE),
        unread = 2,
        unreadText = UiText.Plural(MessagesPlurals.isu_messages_unread_count, 2, listOf(2)),
    )

    val Loading = IsuMessagesUiState()

    val EmptyArchive = IsuMessagesUiState(
        folder = IsuFolder.ARCHIVE,
        list = IsuMessagesList.Empty(
            UiText.Res(MessagesStrings.isu_messages_empty_archive),
            UiText.Res(MessagesStrings.isu_messages_empty_archive_text),
        ),
    )

    val SignIn = IsuMessagesUiState(
        list = IsuMessagesList.Failed(
            UiText.Res(MessagesStrings.isu_messages_sign_in),
            UiText.Res(MessagesStrings.isu_messages_sign_in_text),
            signIn = true,
        ),
    )

    val Offline = IsuMessagesUiState(
        list = IsuMessagesList.Failed(
            UiText.Res(MessagesStrings.isu_messages_error),
            UiText.Res(CoreStrings.common_error_network),
            signIn = false,
        ),
    )

    val Opened = Inbox.copy(
        opened = IsuMessageDetail(
            id = 5,
            subject = "Расписание консультаций перед рубежным контролем",
            sender = "Деканат ФИКТ",
            date = "сб, 24 октября, 16:40",
            body = MessageBody(
                listOf(
                    MessageBlock(listOf(MessageSpan("Уважаемые студенты!"))),
                    MessageBlock(
                        listOf(
                            MessageSpan("Во вложении расписание консультаций на неделю. Записи "),
                            MessageSpan("нет", bold = true),
                            MessageSpan(", вопросы можно прислать заранее в "),
                            MessageSpan("чат курса", link = "https://t.me/example"),
                            MessageSpan("."),
                        ),
                    ),
                    MessageBlock(listOf(MessageSpan("консультации проходят очно;")), marker = "1."),
                    MessageBlock(listOf(MessageSpan("с собой зачётка", italic = true)), marker = "2."),
                ),
            ),
            attachments = listOf(
                IsuAttachmentRow("0123456789abcdef0123456789abcdef", "Консультации_K32.pdf", UiText.Res(MessagesStrings.isu_messages_kind_pdf)),
                IsuAttachmentRow("00112233445566778899aabbccddeeff", "Заявление.doc", UiText.Res(MessagesStrings.isu_messages_kind_word)),
            ),
            unread = true,
        ),
        downloading = "00112233445566778899aabbccddeeff",
    )
}
