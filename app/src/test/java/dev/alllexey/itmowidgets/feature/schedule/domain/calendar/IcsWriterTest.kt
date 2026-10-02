package dev.alllexey.itmowidgets.feature.schedule.domain.calendar

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsWriterTest {

    @Test
    fun `a calendar has one event per lesson with stable uids and utc times`() {
        val text = IcsWriter.write(listOf(event("lesson-1"), event("lesson-2")), STAMP)

        assertEquals(
            listOf(
                "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//ITMO.Widgets//Schedule//RU", "CALSCALE:GREGORIAN",
                "METHOD:PUBLISH",
                "BEGIN:VEVENT", "UID:lesson-1@widgets.alllexey.dev", "DTSTAMP:20261002T090000Z",
                "DTSTART:20261005T052000Z", "DTEND:20261005T065000Z", "SUMMARY:Физика", "LOCATION:1506",
                "DESCRIPTION:Лекция", "TRANSP:OPAQUE", "END:VEVENT",
                "BEGIN:VEVENT", "UID:lesson-2@widgets.alllexey.dev", "DTSTAMP:20261002T090000Z",
                "DTSTART:20261005T052000Z", "DTEND:20261005T065000Z", "SUMMARY:Физика", "LOCATION:1506",
                "DESCRIPTION:Лекция", "TRANSP:OPAQUE", "END:VEVENT",
                "END:VCALENDAR"
            ),
            text.removeSuffix("\r\n").split("\r\n")
        )
    }

    @Test
    fun `every line ends with crlf and no bare line feed is left`() {
        val text = IcsWriter.write(listOf(event("lesson-1", description = "Лекция\nТестовый преподаватель")), STAMP)

        assertTrue(text.endsWith("\r\n"))
        assertFalse(text.replace("\r\n", "").contains('\n'))
        assertTrue("DESCRIPTION:Лекция\\nТестовый преподаватель\r\n" in text)
    }

    @Test
    fun `missing location and description are left out`() {
        val text = IcsWriter.write(listOf(event("lesson-1", location = null, description = null)), STAMP)

        assertFalse("LOCATION" in text)
        assertFalse("DESCRIPTION" in text)
    }

    @Test
    fun `text values escape backslashes semicolons commas and line breaks`() {
        assertEquals("a\\\\b\\;c\\,d\\ne\\nf", IcsWriter.escape("a\\b;c,d\r\ne\nf"))
    }

    @Test
    fun `long lines fold at 75 octets without splitting a character`() {
        val line = "SUMMARY:" + "Физика".repeat(20)

        val folded = IcsWriter.fold(line)

        val parts = folded.split("\r\n")
        assertTrue(parts.size > 1)
        parts.forEachIndexed { index, part ->
            assertTrue(part.toByteArray(Charsets.UTF_8).size <= 75)
            if (index > 0) assertTrue(part.startsWith(" "))
        }
        assertEquals(line, parts.first() + parts.drop(1).joinToString("") { it.removePrefix(" ") })
    }

    @Test
    fun `a line of exactly 75 octets is not folded`() {
        val line = "X".repeat(75)

        assertEquals(line, IcsWriter.fold(line))
        assertEquals("X".repeat(75) + "\r\n X", IcsWriter.fold("X".repeat(76)))
    }

    @Test
    fun `an empty calendar is still valid`() {
        assertEquals(
            "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//ITMO.Widgets//Schedule//RU\r\nCALSCALE:GREGORIAN\r\n" +
                "METHOD:PUBLISH\r\nEND:VCALENDAR\r\n",
            IcsWriter.write(emptyList(), STAMP)
        )
    }

    private fun event(key: String, location: String? = "1506", description: String? = "Лекция") = CalendarEvent(
        key = key,
        title = "Физика",
        start = Instant.parse("2026-10-05T05:20:00Z"),
        end = Instant.parse("2026-10-05T06:50:00Z"),
        location = location,
        description = description
    )

    private companion object {
        val STAMP: Instant = Instant.parse("2026-10-02T09:00:00Z")
    }
}
