package dev.alllexey.itmowidgets.feature.schedule.domain.calendar

import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant

/** One lesson as a calendar event; [key] names the same occurrence in every sync and every `.ics` file. */
data class CalendarEvent(
    val key: String,
    val title: String,
    val start: Instant,
    val end: Instant,
    val location: String?,
    val description: String?
) {
    /** The RFC 5545 UID of the occurrence; also stored in the provider's `UID_2445`. */
    val uid: String get() = "$key@$UID_DOMAIN"

    /**
     * The description as the phone's calendar keeps it: the last line names the occurrence. Google stores the
     * description on its server, so the line survives an event the sync adapter writes back, unlike the provider's
     * local `CUSTOM_APP_*` columns.
     */
    val taggedDescription: String get() = listOfNotNull(description, "$TAG_PREFIX$key").joinToString("\n")

    companion object {
        const val UID_DOMAIN = "widgets.alllexey.dev"
        const val TAG_PREFIX = "ITMO.Widgets · "

        /** The occurrence key of a description written by [taggedDescription], or null for any other text. */
        fun keyOfDescription(description: String?): String? =
            description?.substringAfterLast('\n')?.takeIf { it.startsWith(TAG_PREFIX) }?.removePrefix(TAG_PREFIX)
                ?.takeIf { it.startsWith("lesson-") }
    }
}

/** The own schedule exactly as My ITMO sends it, as calendar events: no lesson kind is left out. */
object CalendarEvents {

    /**
     * Events of [days] in time order. A lesson repeated under the same key is kept once, at its first slot.
     * [address] gives the building's street address when the building is known.
     */
    fun from(days: List<DaySchedule>, zone: TimeZone, address: (Lesson) -> String?): List<CalendarEvent> = days
        .flatMap { day -> day.lessons.map { lesson -> event(day.date, lesson, zone, address(lesson)) } }
        .sortedWith(compareBy(CalendarEvent::start, CalendarEvent::key))
        .distinctBy(CalendarEvent::key)

    /** `lesson-<pairId>`; a lesson without a positive id is named by its slot, flow and subject. */
    fun key(date: LocalDate, lesson: Lesson): String =
        if (lesson.pairId > 0) "lesson-${lesson.pairId}"
        else "lesson-$date-${lesson.start.toSecondOfDay() / 60}-${lesson.flowId}-${lesson.subjectId}"

    private fun event(date: LocalDate, lesson: Lesson, zone: TimeZone, address: String?): CalendarEvent {
        val start = date.atTime(lesson.start).toInstant(zone)
        val end = date.atTime(lesson.end).toInstant(zone)
        return CalendarEvent(
            key = key(date, lesson),
            title = lesson.subjectName.trim(),
            start = start,
            end = maxOf(start, end),
            location = listOfNotNull(lesson.room?.raw, address ?: lesson.building?.raw)
                .map(String::trim).filter(String::isNotEmpty).joinToString(", ").ifEmpty { null },
            description = listOfNotNull(lesson.type, lesson.teacherFio, lesson.groupName)
                .map(String::trim).filter(String::isNotEmpty).distinct().joinToString("\n").ifEmpty { null }
        )
    }
}
