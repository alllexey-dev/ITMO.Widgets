package dev.alllexey.itmowidgets.util

import api.myitmo.model.Lesson
import dev.alllexey.itmowidgets.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

object ScheduleUtils {

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

    fun parseTime(date: LocalDate, time: String): LocalDateTime {
        return date.atTime(LocalTime.parse(time))
    }

    fun lessonDeclension(count: Int): String {
        if (count % 10 == 1) return "пара"
        if (listOf(5, 6, 7, 8, 9, 0).contains(count % 10)) return "пар"
        else return "пары"
    }

    fun shortenRoom(room: String?): String? {
        if (room == null) return null
        val roomLow = room.lowercase()
        if (roomLow.contains("актовый")) return "Акт. зал"
        val m1 = Pattern.compile("[0-9]{4}/[0-9]").matcher(roomLow)
        if (m1.find()) {
            return m1.group()
        }

        val m2 = Pattern.compile("[0-9]{4}").matcher(roomLow)
        if (m2.find()) {
            return m2.group()
        }

        return room
    }

    fun shortenBuildingName(building: String?): String? {
        if (building == null) return null
        val name = building.lowercase()
        if (name.contains("кронв")) return "Кронва"
        if (name.contains("ломо")) return "Ломо"
        if (name.contains("гривц")) return "Гривцова"
        if (name.contains("бирж")) return "Биржевая"

        if (name.contains("вязем")) return "Вязьма"

        return building.substring(0, 6)
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


    fun getWorkTypeColor(workTypeId: Int): Int {
        return when (workTypeId) {
            -1 -> R.color.free_color
            1 -> R.color.lecture_color
            2 -> R.color.lab_color
            3 -> R.color.practice_color
            4,5,6,7,8,9 -> R.color.red_lesson_color // idk what is this meant for
            10 -> R.color.sport_color
            11 -> R.color.free_sport_color
            else -> R.color.subtext_color
        }
    }
}