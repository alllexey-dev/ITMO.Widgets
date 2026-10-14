package dev.alllexey.itmowidgets.feature.schedule.domain.model

import dev.alllexey.itmowidgets.core.schedule.ROOM_BOOKING_TYPE_ID
import kotlin.jvm.JvmInline
import kotlinx.datetime.LocalTime

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

    private companion object {
        /** MyITMO's `flow_type_id` of room bookings. */
        const val ROOM_BOOKING_FLOW = 5
    }

    @JvmInline
    value class TypeId(val raw: Int) {
    }

    /** The type the app shows: MyITMO's, or [ROOM_BOOKING_TYPE_ID] for a lesson of the room booking flow. */
    val kindId: Int get() = if (flowTypeId == ROOM_BOOKING_FLOW) ROOM_BOOKING_TYPE_ID else typeId.raw

    fun hasLocation(): Boolean {
        return room != null || building != null
    }

}
