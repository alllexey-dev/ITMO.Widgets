package dev.alllexey.itmowidgets.feature.messages.data

import dev.alllexey.itmowidgets.feature.messages.domain.IsuAttachment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class IsuMessagesJsonTest {

    @Test
    fun aPageReadsItsMessagesByFieldName() {
        val page = IsuMessagesJson.parse(IsuFixtures.PAGE)

        assertEquals(4, page.unread)
        assertTrue(page.last, "fewer than ten items is the last page")
        assertEquals(listOf(70001L, 70000L), page.messages.map { it.id })
        val first = page.messages[0]
        assertEquals("Белова Ольга Сергеевна", first.sender)
        assertEquals("Перенос лекции & практики", first.subject)
        assertEquals(LocalDate(2026, 10, 21), first.date)
        assertEquals(LocalTime(15, 51), first.time)
        assertFalse(first.read)
        assertEquals("Лекция переносится на пятницу.\nАудитория 1404", first.body.plainText)
        assertTrue(page.messages[1].read)
    }

    @Test
    fun onlyFileHashesBecomeAttachments() {
        val files = IsuMessagesJson.parse(IsuFixtures.PAGE).messages[0].attachments

        assertEquals(listOf(IsuAttachment("0123456789abcdef0123456789abcdef", "plan.pdf", "application/pdf")), files)
    }

    @Test
    fun theEmptyPageEndsTheList() {
        val page = IsuMessagesJson.parse(IsuFixtures.EMPTY)

        assertTrue(page.messages.isEmpty())
        assertTrue(page.last)
    }

    @Test
    fun aFullPageIsNotTheLast() {
        fun item(id: Int) = """{"msgid": $id, "sender_name": "A", "subject": "S", "date": "01.10.2026", "is_read": "Y", "text": "", "files": []}"""
        val page = IsuMessagesJson.parse("""{"unrd_cntr": 0, "incoming": [${(1..10).joinToString { item(it) }}]}""")

        assertFalse(page.last)
    }

    @Test
    fun anotherShapeIsAFormatError() {
        assertFailsWith<IsuFormatException> { IsuMessagesJson.parse(IsuFixtures.LOST) }
        assertFailsWith<IsuFormatException> { IsuMessagesJson.parse("""{"messages": []}""") }
    }
}
