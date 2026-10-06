package dev.alllexey.itmowidgets.feature.schedule.domain.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class IcsWriterTest {

    @Test
    fun aCalendarHasOneEventPerLessonWithStableUidsAndUtcTimes() {
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
    fun everyLineEndsWithCrlfAndNoBareLineFeedIsLeft() {
        val text = IcsWriter.write(listOf(event("lesson-1", description = "Лекция\nТестовый преподаватель")), STAMP)

        assertTrue(text.endsWith("\r\n"))
        assertFalse(text.replace("\r\n", "").contains('\n'))
        assertTrue("DESCRIPTION:Лекция\\nТестовый преподаватель\r\n" in text)
    }

    @Test
    fun missingLocationAndDescriptionAreLeftOut() {
        val text = IcsWriter.write(listOf(event("lesson-1", location = null, description = null)), STAMP)

        assertFalse("LOCATION" in text)
        assertFalse("DESCRIPTION" in text)
    }

    @Test
    fun textValuesEscapeBackslashesSemicolonsCommasAndLineBreaks() {
        assertEquals("a\\\\b\\;c\\,d\\ne\\nf", IcsWriter.escape("a\\b;c,d\r\ne\nf"))
    }

    @Test
    fun longLinesFoldAt75OctetsWithoutSplittingACharacter() {
        val line = "SUMMARY:" + "Физика".repeat(20)

        val folded = IcsWriter.fold(line)

        val parts = folded.split("\r\n")
        assertTrue(parts.size > 1)
        parts.forEachIndexed { index, part ->
            assertTrue(part.encodeToByteArray().size <= 75)
            if (index > 0) assertTrue(part.startsWith(" "))
        }
        assertEquals(line, parts.first() + parts.drop(1).joinToString("") { it.removePrefix(" ") })
    }

    @Test
    fun aLineOfExactly75OctetsIsNotFolded() {
        val line = "X".repeat(75)

        assertEquals(line, IcsWriter.fold(line))
        assertEquals("X".repeat(75) + "\r\n X", IcsWriter.fold("X".repeat(76)))
    }

    @Test
    fun anEmptyCalendarIsStillValid() {
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
