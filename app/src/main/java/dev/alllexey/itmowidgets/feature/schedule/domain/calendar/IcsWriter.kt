package dev.alllexey.itmowidgets.feature.schedule.domain.calendar

import dev.alllexey.itmowidgets.core.text.DateTexts
import kotlin.time.Instant

/**
 * An RFC 5545 calendar of [CalendarEvent]s: CRLF line ends, lines folded at 75 octets without splitting a UTF-8
 * character, escaped text and times in UTC, so no `VTIMEZONE` is needed. The UID of an occurrence is the same in every
 * file, so importing a newer file updates the events instead of duplicating them.
 */
object IcsWriter {

    fun write(events: List<CalendarEvent>, stamp: Instant): String = buildString {
        line("BEGIN:VCALENDAR")
        line("VERSION:2.0")
        line("PRODID:-//ITMO.Widgets//Schedule//RU")
        line("CALSCALE:GREGORIAN")
        line("METHOD:PUBLISH")
        events.forEach { event ->
            line("BEGIN:VEVENT")
            line("UID:${escape(event.uid)}")
            line("DTSTAMP:${utc(stamp)}")
            line("DTSTART:${utc(event.start)}")
            line("DTEND:${utc(event.end)}")
            line("SUMMARY:${escape(event.title)}")
            event.location?.let { line("LOCATION:${escape(it)}") }
            event.description?.let { line("DESCRIPTION:${escape(it)}") }
            line("TRANSP:OPAQUE")
            line("END:VEVENT")
        }
        line("END:VCALENDAR")
    }

    /** TEXT escaping of RFC 5545 3.3.11. */
    fun escape(text: String): String = text
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .replace("\n", "\\n")

    /** Splits [line] into lines of at most 75 octets; every continuation starts with one space (RFC 5545 3.1). */
    fun fold(line: String): String {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var octets = 0
        var index = 0
        while (index < line.length) {
            val codePoint = line.codePointAt(index)
            val character = String(Character.toChars(codePoint))
            val size = character.toByteArray(Charsets.UTF_8).size
            // The leading space of a continuation line counts toward its 75 octets.
            val limit = if (parts.isEmpty()) MAX_OCTETS else MAX_OCTETS - 1
            if (octets + size > limit) {
                parts += current.toString()
                current.clear()
                octets = 0
            }
            current.append(character)
            octets += size
            index += Character.charCount(codePoint)
        }
        parts += current.toString()
        return parts.joinToString("$CRLF ")
    }

    private fun StringBuilder.line(content: String) {
        append(fold(content)).append(CRLF)
    }

    private fun utc(instant: Instant): String = DateTexts.icsUtc(instant)

    private const val CRLF = "\r\n"
    private const val MAX_OCTETS = 75
}
