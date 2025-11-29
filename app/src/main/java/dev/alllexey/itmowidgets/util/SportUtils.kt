package dev.alllexey.itmowidgets.util

import api.myitmo.model.sport.SportLesson

object SportUtils {

    fun shortenSectionName(sportLesson: String?): String? {
        return when (sportLesson) {
            "Спортивный туризм (северная ходьба)" -> "Северная ходьба"
            "Фитнес (функциональная тренировка)" -> "Фитнес (функциональный)"
            "Современные танцы (Клуб парных танцев \"Потанцуем\")" -> "Современные танцы"
            "Спортивный туризм (северная ходьба - маршруты)" -> "Северная ходьба (маршруты)"
            "Современные танцы (Студия Flame)" -> "Современные танцы"
            else -> sportLesson
        }
    }

    fun getSportLessonTypeName(sportLesson: SportLesson): String {
        return when (sportLesson.lessonLevel) {
            2L -> "Секция (обучение)"
            3L -> "Секция (средний)"
            4L -> "Секция (сборная)"
            else -> return when (sportLesson.typeId) {
                1L -> "Открытое занятие"
                2L -> "Свободное посещение"
                5L -> "Задолженность"
                6L -> "Нормативы"
                7L -> "Экстернат"
                8L -> "Дополнительное"
                else -> "Неизвестный вид"
            }
        }
    }
}