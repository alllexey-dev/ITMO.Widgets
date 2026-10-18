package dev.alllexey.itmowidgets.core.schedule

import dev.alllexey.itmowidgets.core.result.AppResult
import kotlinx.datetime.LocalTime

/**
 * The lesson a teacher gives now ([isNow]) or else their next one today, from MyITMO's teacher timetable: the person
 * card's «Сейчас ведёт». Lessons at the same time, subject and place for several groups are one lesson with every
 * group in [groups]. [typeId] is the lesson type as in `DayLesson.typeId`; [room] and [building] are MyITMO's raw
 * texts, null for an online lesson.
 */
data class TeacherNowLesson(
    val start: LocalTime,
    val end: LocalTime,
    val subject: String,
    val typeId: Int,
    val room: String?,
    val building: String?,
    val online: Boolean,
    val groups: List<String>,
    val isNow: Boolean,
)

/** Reads a teacher's MyITMO timetable for today. Read only: these lessons are never uploaded to Backend. */
fun interface TeacherNowGateway {
    /**
     * Success with null when [teacherIsu] gives nothing for the rest of today, which is also the answer for someone
     * who does not teach. Asks MyITMO on every call; the caller decides how often.
     */
    suspend fun lessonOf(teacherIsu: Int): AppResult<TeacherNowLesson?>
}
