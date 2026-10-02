package dev.alllexey.itmowidgets.feature.sport.domain.model

import java.time.OffsetDateTime

/** A catalog lesson, the prototype, repeats this many days later; until the repeat appears it is predicted. */
const val PREDICTION_OFFSET_DAYS = 14L

fun SportLesson.predictedStart(): OffsetDateTime = start.plusDays(PREDICTION_OFFSET_DAYS)

fun SportLesson.predictedEnd(): OffsetDateTime = end.plusDays(PREDICTION_OFFSET_DAYS)

/** Whether this real lesson is the repeat of [prototype]: the same section, teacher, levels, type and slot. */
fun SportLesson.repeats(prototype: SportLesson): Boolean =
    start == prototype.predictedStart()
        && sectionId == prototype.sectionId
        && teacherIsu == prototype.teacherIsu
        && sectionLevel == prototype.sectionLevel
        && lessonLevel == prototype.lessonLevel
        && typeId == prototype.typeId
        && timeSlotId == prototype.timeSlotId

/**
 * The lesson a shared link names among the merged lessons, where a predicted lesson keeps its prototype's id.
 *
 * A real link names a real lesson. A predicted link names the prototype: its prediction while the repeat is missing,
 * the real repeat once the catalog has it, nothing when the prototype left the catalog.
 */
fun List<SportLesson>.findLinked(lessonId: Long, predicted: Boolean): SportLesson? {
    val real = firstOrNull { it.isLessonReal && it.lessonId == lessonId }
    if (!predicted) return real
    return firstOrNull { !it.isLessonReal && it.lessonId == lessonId }
        ?: real?.let { prototype -> firstOrNull { it.isLessonReal && it.repeats(prototype) } }
}
