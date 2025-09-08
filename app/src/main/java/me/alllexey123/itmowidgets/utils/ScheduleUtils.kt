package me.alllexey123.itmowidgets.utils

import me.alllexey123.itmowidgets.R

object ScheduleUtils {

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