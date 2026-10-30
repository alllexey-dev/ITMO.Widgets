package dev.alllexey.itmowidgets.feature.schedule.data.days

import dev.alllexey.itmoapi.core.requireResult
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.lesson.DayLesson
import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.lesson.LessonTarget
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.data.demo.DemoSchedule
import dev.alllexey.itmowidgets.feature.schedule.data.mapper.toModel
import dev.alllexey.itmowidgets.feature.schedule.data.scheduleResultOf
import dev.alllexey.itmowidgets.feature.schedule.domain.TeacherScheduleDays
import dev.alllexey.itmowidgets.feature.schedule.domain.groupNames
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.toDetailsArgs
import dev.alllexey.itmowidgets.feature.schedule.domain.taughtLessons
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

/**
 * A teacher's MyITMO timetable (`/api/schedule/teacher/{isu}`) as feed days: what this instance fetched, in memory
 * only; nothing is cached on disk or uploaded to Backend. Lessons taught together to several groups are one lesson
 * with every group in place of the teacher; room bookings are left out. Demo answers from the demo week.
 */
class TeacherScheduleDaysImpl(
    private val myItmo: MyItmoClient,
    private val time: AcademicTimeProvider,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers,
) : TeacherScheduleDays {

    private val fetched = MutableStateFlow<Map<Int, Map<LocalDate, LessonDay>>>(emptyMap())

    override fun observe(userIsu: Int?, from: LocalDate, to: LocalDate): Flow<List<LessonDay>> {
        val isu = teacher(userIsu)
        return fetched.map { all -> all[isu].orEmpty().values.filter { it.date in from..to }.sortedBy { it.date } }
            .distinctUntilChanged()
    }

    override suspend fun refresh(userIsu: Int?, from: LocalDate, to: LocalDate): AppResult<Unit> {
        val isu = teacher(userIsu)
        return when (val result = scheduleResultOf { days(isu, from, to).map { it.toLessonDay() } }) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                // Dates missing from the answer are dropped, as the schedule cache does.
                fetched.update { all ->
                    val kept = all[isu].orEmpty().filterKeys { it !in from..to }
                    all + (isu to kept + result.value.associateBy { it.date })
                }
                AppResult.Success(Unit)
            }
        }
    }

    private suspend fun days(isu: Int, from: LocalDate, to: LocalDate): List<DaySchedule> {
        if (demo.isActive()) {
            return DemoSchedule.ownDays(from, to, time.today()).map { day ->
                day.copy(lessons = day.lessons.filter { it.teacherIsu == isu.toLong() })
            }
        }
        return withContext(dispatchers.io) {
            myItmo.schedule.getTeacherSchedule(isu.toLong(), from, to).requireResult().map { it.toModel() }
        }
    }

    private fun DaySchedule.toLessonDay() = LessonDay(
        date = date,
        lessons = taughtLessons(lessons).map { it.toDayLesson(date) },
        cachedAt = null,
        week = weekNumber.takeIf { it > 0 },
    )

    private fun List<Lesson>.toDayLesson(date: LocalDate): DayLesson {
        val lesson = first()
        val groups = groupNames()
        return DayLesson(
            start = lesson.start,
            end = lesson.end,
            subject = lesson.subjectName,
            typeId = lesson.kindId,
            teacher = null,
            room = lesson.room?.raw,
            building = lesson.building?.raw,
            online = lesson.formatId == REMOTE_FORMAT,
            target = LessonTarget.Lesson(
                lesson.toDetailsArgs(date).copy(flowName = groups.joinToString(", ").takeIf(String::isNotEmpty)),
            ),
            note = lesson.note,
            videoUrl = lesson.zoomUrl?.takeIf(String::isNotBlank),
            groups = groups,
        )
    }

    private fun teacher(userIsu: Int?): Int = checkNotNull(userIsu) { "A teacher's timetable needs the teacher's ISU" }

    private companion object {
        /** MyITMO's `format_id` of a remote lesson. */
        const val REMOTE_FORMAT = 3
    }
}
