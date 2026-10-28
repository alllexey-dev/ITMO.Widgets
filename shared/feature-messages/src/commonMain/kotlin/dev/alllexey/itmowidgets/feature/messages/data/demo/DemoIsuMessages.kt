package dev.alllexey.itmowidgets.feature.messages.data.demo

import dev.alllexey.itmowidgets.feature.messages.data.MessageHtml
import dev.alllexey.itmowidgets.feature.messages.domain.IsuAttachment
import dev.alllexey.itmowidgets.feature.messages.domain.IsuFolder
import dev.alllexey.itmowidgets.feature.messages.domain.IsuMessage
import dev.alllexey.itmowidgets.feature.messages.domain.IsuMessagesPage
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * The fictional ISU messages of the demo session: invented senders, subjects and links, bodies in the HTML shape ISU
 * sends, so the demo goes through the same parser. One page per folder; attachments cannot be opened in the demo.
 */
internal object DemoIsuMessages {

    fun page(folder: IsuFolder, number: Int): IsuMessagesPage {
        val messages = if (number == 1) (if (folder == IsuFolder.INBOX) inbox else archive) else emptyList()
        return IsuMessagesPage(messages, unread = messages.count { !it.read }, last = true)
    }

    val inbox: List<IsuMessage> by lazy {
        listOf(
            message(
                id = 9_000_006, sender = "Белова Ольга Сергеевна", subject = "Перенос лекции по матанализу",
                date = LocalDate(2026, 10, 26), time = LocalTime(9, 12), read = false,
                html = "<p>Здравствуйте!</p><p>Лекция в среду <strong>переносится на пятницу, 13:30</strong>, " +
                    "аудитория 1404 (Кронверкский, 49).</p><p>Домашнее задание остаётся прежним.</p>",
            ),
            message(
                id = 9_000_005, sender = "Деканат ФИКТ", subject = "Расписание консультаций перед рубежным контролем",
                date = LocalDate(2026, 10, 24), time = LocalTime(16, 40), read = false,
                html = "<div>Уважаемые студенты!</div><div>Во вложении расписание консультаций на неделю. Обратите " +
                    "внимание:</div><ol><li>консультации проходят очно;</li><li>запись не нужна;</li>" +
                    "<li>вопросы можно прислать заранее.</li></ol>",
                attachments = listOf(IsuAttachment("0123456789abcdef0123456789abcdef", "Консультации_K32.pdf", "application/pdf")),
            ),
            message(
                id = 9_000_004, sender = "Громов Пётр Андреевич", subject = "Журнал лабораторных работ",
                date = LocalDate(2026, 10, 20), time = LocalTime(11, 5), read = true,
                html = "<p>Журнал лабораторных открыт: <a href=\"https://example.org/labs\" target=\"_blank\">" +
                    "ссылка на курс</a>.</p><p><em>Сдача первой лабораторной до 3 ноября.</em></p>",
            ),
            message(
                id = 9_000_003, sender = "Центр дополнительного образования", subject = "Осенний набор на курсы",
                date = LocalDate(2026, 10, 14), time = LocalTime(14, 0), read = true,
                html = "<p>Открыт набор на курсы английского и немецкого языков. Подробности и запись: " +
                    "https://example.org/courses</p>",
                attachments = listOf(
                    IsuAttachment("fedcba9876543210fedcba9876543210", "Программа курсов.pdf", "application/pdf"),
                    IsuAttachment("00112233445566778899aabbccddeeff", "Заявление.doc", "application/msword"),
                ),
            ),
            message(
                id = 9_000_002, sender = "Белова Ольга Сергеевна", subject = "Чат курса",
                date = LocalDate(2026, 9, 3), time = LocalTime(18, 30), read = true,
                html = "Добрый вечер! Вопросы по курсу задавайте в чате: <a href=\"https://t.me/example\">t.me/example</a>",
            ),
        )
    }

    val archive: List<IsuMessage> by lazy {
        listOf(
            message(
                id = 9_000_001, sender = "Деканат ФИКТ", subject = "Итоги летней сессии",
                date = LocalDate(2025, 7, 2), time = LocalTime(10, 0), read = true,
                html = "<p>Ведомости летней сессии закрыты. Пересдачи пройдут с 1 по 14 сентября.</p>",
            ),
        )
    }

    private fun message(
        id: Long,
        sender: String,
        subject: String,
        date: LocalDate,
        time: LocalTime,
        read: Boolean,
        html: String,
        attachments: List<IsuAttachment> = emptyList(),
    ) = IsuMessage(id, sender, subject, date, time, read, MessageHtml.parse(html), attachments)
}
