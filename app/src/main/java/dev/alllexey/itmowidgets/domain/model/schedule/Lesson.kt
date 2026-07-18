package dev.alllexey.itmowidgets.domain.model.schedule

import dev.alllexey.itmowidgets.R
import java.time.LocalTime

data class Lesson(

    val pairId: Long,
    val start: LocalTime,
    val end: LocalTime,
    val type: String, // Lesson#workType = Lesson#type
    /**
     * 1 - лекция
     * 2 - лабораторная
     * 3 - практика
     * 4 - ?
     * 5 - экзамен
     * 6 - зачёт
     * 7, 8, 9 - ?
     * 10 - консультация
     * 11 - спорт
     */
    val typeId: TypeId, // Lesson#workTypeId
    val note: String?,

    val subjectName: String,
    val subjectId: Long,
    val groupName: String,
    val flowId: Long,
    /**
     * 2 - пары
     * 3 - спорт
     * 5 - бронь кабинетов (?)
     */
    val flowTypeId: Int,

    val teacherIsu: Long?,
    val teacherFio: String?,

    val room: Room?,
    val building: Building?,
    val buildingId: Int?,
    /**
     * 13 - Кронва
     * 273 - Ломо
     * 5 - Вязьма
     * 319 - Виртуальные аудитории
     */
    val mainBuildingId: Int?,
    val format: String,
    val formatId: Int, // 1: Очный, 2: Очно - дистанционный, 3: Дистанционный

    val zoomUrl: String?,
    val zoomPassword: String?,
    val zoomInfo: String?

) {

    @JvmInline
    value class TypeId(val raw: Int) {
        fun color(): Int {
            return when (raw) {
                -1 -> R.color.free_color
                1 -> R.color.lecture_color
                2 -> R.color.lab_color
                3 -> R.color.practice_color
                5 -> R.color.red_lesson_color // exam
                6 -> R.color.red_lesson_color // credit
                4, // IDK
                7, // IDK
                8, // IDK
                9 -> R.color.red_lesson_color // IDK
                10 -> R.color.consultation_color
                11 -> R.color.free_sport_color
                else -> R.color.subtext_color
            }
        }

        fun name(): String {
            return when (raw) {
                -1 -> "Нет пар"
                1 -> "Лекция"
                2 -> "Лабораторная"
                3 -> "Практика"
                5 -> "Экзамен"
                6 -> "Зачёт"
                7, 8, 9 -> "Пара" // IDK
                10 -> "Консультация"
                11 -> "Спорт"
                else -> "Пара"
            }
        }
    }

    fun hasLocation(): Boolean {
        return room != null || building != null
    }

}
