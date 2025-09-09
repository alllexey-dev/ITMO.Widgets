package me.alllexey123.itmowidgets.providers

import android.content.Context
import api.myitmo.model.Lesson
import api.myitmo.model.Schedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

object ScheduleProvider {

    fun findCurrentOrNextLesson(lessons: List<Lesson>, timeContext: LocalDateTime): Lesson? {
        return findCurrentLesson(lessons, timeContext) ?: findNextLesson(lessons, timeContext)
    }

    // suppose the lessons are in the correct order
    fun findCurrentLesson(lessons: List<Lesson>, timeContext: LocalDateTime): Lesson? {
        var result: Lesson? = null

        val currTime = DateTimeFormatter.ofPattern("HH:mm").format(timeContext)

        for (lesson in lessons) {
            if (lesson.timeEnd >= currTime && lesson.timeStart <= currTime) {
                result = lesson
                break
            }
        }

        return result
    }

    // suppose the lessons are in the correct order
    fun findNextLesson(lessons: List<Lesson>, timeContext: LocalDateTime): Lesson? {
        var result: Lesson? = null

        val currTime = DateTimeFormatter.ofPattern("HH:mm").format(timeContext)

        for (lesson in lessons) {
            if (lesson.timeEnd > currTime) {
                result = lesson
                break
            }
        }

        return result
    }

    fun getDaySchedule(context: Context, date: LocalDate): Schedule {
        val myItmo = MyItmoProvider.getMyItmo(context)

        try {
            val dataResponse = myItmo.api().getPersonalSchedule(date, date).execute().body()

            if (dataResponse == null ) {
                throw RuntimeException("Data response is null")
            }

            if (dataResponse.data == null || dataResponse.data.isEmpty()) {
                throw RuntimeException("Schedule data is empty")
            }

            return dataResponse.data[0]

        } catch (e: Exception) {
            throw RuntimeException("Could not get lessons", e)
        }
    }

    fun getWeekSchedule(context: Context, date: LocalDate): List<Schedule> {
        val weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        val myItmo = MyItmoProvider.getMyItmo(context)

        try {
            val dataResponse = myItmo.api().getPersonalSchedule(weekStart, weekStart.plusDays(6)).execute().body()

            if (dataResponse == null ) {
                throw RuntimeException("Data response is null")
            }

            if (dataResponse.data == null || dataResponse.data.isEmpty()) {
                throw RuntimeException("Schedule data is empty")
            }

            return dataResponse.data

        } catch (e: Exception) {
            throw RuntimeException("Could not get lessons", e)
        }
    }
}