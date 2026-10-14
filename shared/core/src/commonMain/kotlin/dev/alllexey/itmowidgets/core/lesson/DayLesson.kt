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
 * building in full. [online] is MyITMO's remote format; [isOnline] also counts a placeholder room such as `дистант`,
 * and such lessons have no place line. [videoUrl] is the meeting link. [withYou] marks a lesson of someone else's
 * schedule that the viewer attends too (`Вы`).
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
    val withYou: Boolean = false,
) {
    /** Remote by format, or with a placeholder instead of a room or a building (`дистант`, `онлайн`). */
    val isOnline: Boolean get() = online || isOnlinePlaceholder(room) || isOnlinePlaceholder(building)

    /** The note unless it only repeats that an online lesson is online. */
    val noteText: String? get() = note?.trim()?.takeIf { it.isNotEmpty() && !(isOnline && isOnlinePlaceholder(it)) }
}

/** MyITMO's words for "no room, it is remote" that come in the room, building or note fields. */
fun isOnlinePlaceholder(text: String?): Boolean {
    val word = text?.trim()?.trimEnd('.')?.lowercase() ?: return false
    return ONLINE_PLACEHOLDERS.any(word::startsWith)
}

private val ONLINE_PLACEHOLDERS = listOf("дистант", "дистанц", "онлайн", "online", "удален", "удалён", "zoom", "зум")

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

/**
 * The lessons of [date] that the cache has; [cachedAt] is when the cache last got them, [week] MyITMO's number of the
 * academic week (null when the cache does not say).
 */
data class LessonDay(val date: LocalDate, val lessons: List<DayLesson>, val cachedAt: Instant?, val week: Int? = null)
