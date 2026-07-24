package dev.alllexey.itmowidgets.feature.schedule.domain.model

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
    }

    fun hasLocation(): Boolean {
        return room != null || building != null
    }

}
