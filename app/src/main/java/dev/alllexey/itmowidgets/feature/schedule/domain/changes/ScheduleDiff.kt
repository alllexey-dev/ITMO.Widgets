package dev.alllexey.itmowidgets.feature.schedule.domain.changes

import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import java.time.LocalDateTime

/** A difference between two snapshots before it is stored; [before] or [after] is empty for a cancel or an add. */
data class DetectedChange(
    val kind: ScheduleChangeKind,
    val fields: Set<ScheduleChangeField>,
    val before: SnapshotLesson?,
    val after: SnapshotLesson?
) {
    fun soonestStart(): LocalDateTime = listOfNotNull(before, after).minOf { it.startsAt }

    fun isOver(now: LocalDateTime): Boolean = listOfNotNull(before, after).all { it.endsAt <= now }

    fun subjectName(): String = (after ?: before)?.subjectName.orEmpty()

    fun pairId(): Long = (after ?: before)?.pairId ?: 0
}

/**
 * Compares two snapshots of the own academic schedule. Pure: [now] is Moscow time from the caller.
 *
 * A lesson is the same when its positive `pairId` is in both snapshots, wherever its dates are. The rest is linked
 * by subject, flow and type only inside the overlap of the two windows, so days that left the window and the new
 * last day are never read as cancels or adds.
 */
object ScheduleDiff {

    fun compare(previous: ScheduleSnapshot, current: ScheduleSnapshot, now: LocalDateTime): List<DetectedChange> {
        val overlapStart = maxOf(previous.start, current.start)
        val overlapEnd = minOf(previous.end, current.end)
        if (overlapStart > overlapEnd) return emptyList()
        val inOverlap = { lesson: SnapshotLesson -> lesson.date in overlapStart..overlapEnd }

        val previousById = previous.lessons.filter { it.pairId > 0 }.associateBy { it.pairId }
        val links = mutableListOf<Pair<SnapshotLesson, SnapshotLesson>>()
        val linkedIds = mutableSetOf<Long>()
        current.lessons.forEach { lesson ->
            val before = previousById[lesson.pairId].takeIf { lesson.pairId > 0 } ?: return@forEach
            links += before to lesson
            linkedIds += lesson.pairId
        }

        val oldRest = previous.lessons.filter { (it.pairId <= 0 || it.pairId !in linkedIds) && inOverlap(it) }
        val newRest = current.lessons.filter { (it.pairId <= 0 || it.pairId !in linkedIds) && inOverlap(it) }
        val (heuristic, cancelled, added) = linkByKey(oldRest, newRest)
        links += heuristic

        val changes = buildList {
            links.forEach { (before, after) ->
                val fields = fieldsChanged(before, after)
                if (fields.isNotEmpty()) add(DetectedChange(ScheduleChangeKind.UPDATED, fields, before, after))
            }
            cancelled.forEach { add(DetectedChange(ScheduleChangeKind.CANCELLED, emptySet(), it, null)) }
            added.forEach { add(DetectedChange(ScheduleChangeKind.ADDED, emptySet(), null, it)) }
        }
        return changes
            .filterNot { it.isOver(now) }
            .sortedWith(compareBy<DetectedChange>({ it.soonestStart() }, { it.subjectName() }, { it.pairId() }))
    }

    /**
     * Links lessons of one subject, flow and type: the same date and start first, then the rest in time order by
     * index. Returns the links, the unlinked old and the unlinked new lessons.
     */
    private fun linkByKey(
        old: List<SnapshotLesson>,
        new: List<SnapshotLesson>
    ): Triple<List<Pair<SnapshotLesson, SnapshotLesson>>, List<SnapshotLesson>, List<SnapshotLesson>> {
        val links = mutableListOf<Pair<SnapshotLesson, SnapshotLesson>>()
        val cancelled = mutableListOf<SnapshotLesson>()
        val added = mutableListOf<SnapshotLesson>()
        val oldByKey = old.sortedWith(TIME_ORDER).groupBy(::key)
        val newByKey = new.sortedWith(TIME_ORDER).groupBy(::key)
        (oldByKey.keys + newByKey.keys).forEach { key ->
            val oldLeft = oldByKey[key].orEmpty().toMutableList()
            val newLeft = newByKey[key].orEmpty().toMutableList()
            oldLeft.toList().forEach { before ->
                val same = newLeft.firstOrNull { it.date == before.date && it.start == before.start } ?: return@forEach
                links += before to same
                oldLeft -= before
                newLeft -= same
            }
            val paired = minOf(oldLeft.size, newLeft.size)
            (0 until paired).forEach { links += oldLeft[it] to newLeft[it] }
            cancelled += oldLeft.drop(paired)
            added += newLeft.drop(paired)
        }
        return Triple(links, cancelled, added)
    }

    private fun fieldsChanged(before: SnapshotLesson, after: SnapshotLesson): Set<ScheduleChangeField> = buildSet {
        if (before.date != after.date || before.start != after.start || before.end != after.end) {
            add(ScheduleChangeField.TIME)
        }
        if (before.formatId != after.formatId) add(ScheduleChangeField.FORMAT)
        if (normalized(before.room) != normalized(after.room) || normalized(before.building) != normalized(after.building)) {
            add(ScheduleChangeField.PLACE)
        }
        val teacherChanged = if (before.teacherIsu != null && after.teacherIsu != null) {
            before.teacherIsu != after.teacherIsu
        } else {
            normalized(before.teacherName) != normalized(after.teacherName)
        }
        if (teacherChanged) add(ScheduleChangeField.TEACHER)
    }

    private fun normalized(value: String?): String? =
        value?.trim()?.replace(WHITESPACE, " ")?.lowercase()?.takeIf(String::isNotEmpty)

    private fun key(lesson: SnapshotLesson) = LessonKey(lesson.subjectId, lesson.flowId, lesson.typeId)

    private data class LessonKey(val subjectId: Long, val flowId: Long, val typeId: Int)

    private val WHITESPACE = Regex("\\s+")
    private val TIME_ORDER = compareBy<SnapshotLesson>({ it.date }, { it.start }, { it.end }, { it.pairId })
}
