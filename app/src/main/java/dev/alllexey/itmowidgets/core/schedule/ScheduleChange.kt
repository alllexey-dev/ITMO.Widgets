package dev.alllexey.itmowidgets.core.schedule

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** One occurrence of a lesson: My ITMO's `pair_id` on one date. */
data class LessonOccurrence(val pairId: Long, val date: LocalDate)

enum class ScheduleChangeKind { ADDED, CANCELLED, UPDATED }

/** What differs in an updated lesson; the order is the priority of the headline. */
enum class ScheduleChangeField { TIME, FORMAT, PLACE, TEACHER }

/** One side of a change: when, where, how and with whom the lesson was or is. */
data class LessonSlot(
    val pairId: Long,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val room: String?,
    val building: String?,
    val formatId: Int,
    val format: String?,
    val teacherIsu: Long?,
    val teacherName: String?
) {
    val startsAt: LocalDateTime get() = LocalDateTime.of(date, start)
    val endsAt: LocalDateTime get() = LocalDateTime.of(date, end)

    fun occurrence() = LessonOccurrence(pairId, date)
}

/**
 * A change of the viewer's own schedule found by the background check. [before] is empty for an added lesson,
 * [after] for a cancelled one; an updated lesson has both.
 */
data class ScheduleChange(
    val id: String,
    val detectedAt: Instant,
    val kind: ScheduleChangeKind,
    val fields: Set<ScheduleChangeField>,
    val subjectName: String,
    val typeId: Int,
    val flowName: String?,
    val before: LessonSlot?,
    val after: LessonSlot?,
    val read: Boolean,
    val notified: Boolean
) {
    init {
        require(before != null || after != null) { "Schedule change $id has neither side" }
    }

    /** The field the headline speaks about; `null` for an added or cancelled lesson. */
    val headlineField: ScheduleChangeField?
        get() = ScheduleChangeField.entries.firstOrNull { it in fields }

    /**
     * The occurrences a schedule marks: the new one, plus the old one when the lesson left it, so a stale cache that
     * still has the old slot is marked too.
     */
    fun occurrences(): Set<LessonOccurrence> = buildSet {
        after?.let { add(it.occurrence()) }
        if (kind == ScheduleChangeKind.CANCELLED || ScheduleChangeField.TIME in fields) before?.let { add(it.occurrence()) }
    }

    /** Every side has ended by [now]; a move from a past slot to a future one is not over. */
    fun isOver(now: LocalDateTime): Boolean = sides().all { it.endsAt <= now }

    fun soonestStart(): LocalDateTime = sides().minOf { it.startsAt }

    fun touches(date: LocalDate): Boolean = before?.date == date || after?.date == date

    private fun sides(): List<LessonSlot> = listOfNotNull(before, after)
}
