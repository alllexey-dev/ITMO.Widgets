package dev.alllexey.itmowidgets.core.util

import api.myitmo.model.schedule.Lesson
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object ScheduleUtil {

    fun generateDates(start: LocalDate, end: LocalDate): List<LocalDate> {
        val list = mutableListOf<LocalDate>()
        var d = start
        while (!d.isAfter(end)) {
            list.add(d)
            d = d.plusDays(1)
        }
        return list
    }

    fun findCurrentOrNextLesson(lessons: List<Lesson>, timeContext: LocalDateTime): Lesson? {
        return findCurrentLesson(lessons, timeContext) ?: findNextLesson(lessons, timeContext)
    }

    // suppose the lessons are in the correct order
    fun findCurrentLesson(lessons: List<Lesson>, timeContext: LocalDateTime): Lesson? {
        var result: Lesson? = null

        val currTime = DateTimeFormatter.ofPattern("HH:mm").format(timeContext)

        for (lesson in lessons) {
            if (lesson.timeEnd > currTime && lesson.timeStart <= currTime) {
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

    fun lessonDeclension(count: Int): String {
        if (count % 10 == 1) return "пара"
        return if (listOf(5, 6, 7, 8, 9, 0).contains(count % 10)) "пар"
        else "пары"
    }

    fun getRuDayOfWeek(dayOfWeek: DayOfWeek): String {
        return when (dayOfWeek) {
            DayOfWeek.MONDAY -> "Понедельник"
            DayOfWeek.TUESDAY -> "Вторник"
            DayOfWeek.WEDNESDAY -> "Среда"
            DayOfWeek.THURSDAY -> "Четверг"
            DayOfWeek.FRIDAY -> "Пятница"
            DayOfWeek.SATURDAY -> "Суббота"
            DayOfWeek.SUNDAY -> "Воскресение"
        }
    }

    fun getRussianMonthInGenitiveCase(monthNumber: Int): String {
        val months = arrayOf(
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря"
        )

        return months[monthNumber - 1]
    }
}
