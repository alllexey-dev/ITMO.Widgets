package dev.alllexey.itmowidgets.core.lesson

import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

/**
 * One own lesson of a day as every lesson view needs it, built by the schedule feature from its cache, the found
 * changes and the sport queues. [room] and [building] are MyITMO's raw texts: rows shorten them, a hero shows the
 * building in full. [online] lessons have no place line. [videoUrl] is the meeting link.
 */
data class DayLesson(
    val start: LocalTime,
    val end: LocalTime,
    val subject: String,
    val typeId: Int,
    val teacher: String?,
    val room: String?,
    val building: String?,
    val online: Boolean,
    val target: LessonTarget,
    val note: String? = null,
    val videoUrl: String? = null,
    val change: LessonChange? = null,
    val sport: SportMark? = null,
)

/**
 * A change the background check found on this lesson. [previousRoom] is the room before a move, struck through
 * before the new one; [cancelled] lessons stay on their day, struck through. [detectedAt] is academic wall time.
 */
data class LessonChange(val detectedAt: LocalDateTime, val previousRoom: String? = null, val cancelled: Boolean = false)

/** A sport lesson that is not an ordinary booked lesson of the timetable. */
enum class SportMark {
    /** Booked: the lesson is in the own timetable. */
    BOOKED,

    /** Waiting in a queue (an auto-sign, a free place or a predicted lesson). */
    QUEUED,
}

/** Where a tap on a lesson leads. */
sealed interface LessonTarget {
    data class Lesson(val args: LessonDetailsArgs) : LessonTarget

    data class PendingSport(val args: PendingSportDetailsArgs) : LessonTarget
}

/** The own lessons of [date] that the cache has; [cachedAt] is when the cache last got them. */
data class LessonDay(val date: LocalDate, val lessons: List<DayLesson>, val cachedAt: Instant?)
