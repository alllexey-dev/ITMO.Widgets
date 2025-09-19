package me.alllexey123.itmowidgets.utils

import me.alllexey123.itmowidgets.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.regex.Pattern

object ScheduleUtils {

    fun parseTime(date: LocalDate, time: String): LocalDateTime {
        val hoursStr = time.substring(0, time.indexOf(":"))
        val minutesStr = time.substring(time.indexOf(":") + 1)

        val hours = (if (hoursStr.startsWith('0')) hoursStr.substring(1) else hoursStr).toInt()
        val minutes = (if (minutesStr.startsWith('0')) minutesStr.substring(1) else minutesStr).toInt()
        return date.atTime(hours, minutes)
    }

    fun lessonDeclension(count: Int): String {
        if (count % 10 == 1) return "пара"
        if (listOf(5, 6, 7, 8, 9, 0).contains(count % 10)) return "пар"
        else return "пары"
    }

    fun shortenRoom(room: String): String {
        val roomLow = room.lowercase()
        if (roomLow.contains("актовый")) return "Акт. зал"
        val m1 = Pattern.compile("[0-9]{4}/[0-9]").matcher(roomLow)
        if (m1.find()) {
            return m1.group()
        }

        val m2 = Pattern.compile("[1-9]{4}").matcher(roomLow)
        if (m2.find()) {
            return m2.group()
        }

        return room
    }

    fun shortenBuildingName(building: String): String {
        val name = building.lowercase()
        if (name.contains("кронв")) return "Кронва"
        if (name.contains("ломо")) return "Ломо"
        if (name.contains("гривц")) return "Гривцова"
        if (name.contains("бирж")) return "Биржевая"

        if (name.contains("вязем")) return "Вязьма"

        return building.substring(0, 6)
    }

    fun getWorkTypeColor(workTypeId: Int): Int {
        return when (workTypeId) {
            -1 -> R.color.free_color
            1 -> R.color.lecture_color
            2 -> R.color.lab_color
            3 -> R.color.practice_color
            else -> R.color.subtext_color
        }
    }
}