package dev.alllexey.itmowidgets.domain.model.sport

import dev.alllexey.itmowidgets.core.model.SportQueue
import dev.alllexey.itmowidgets.core.model.SportQueueEntry
import java.time.OffsetDateTime

data class SportLesson(
    override val type: String = "lesson",
    override val isLessonReal: Boolean,
    override val lessonId: Long,
    override val start: OffsetDateTime,
    override val end: OffsetDateTime,
    val sectionId: Long,
    override val sectionName: SectionName,
    override val sectionLevel: Int,
    val lessonGroupId: Long,
    override val lessonLevel: Int,
    val typeId: Int,
    val buildingId: Long?,
    val roomId: Long,
    override val roomName: String,
    val limit: Int,
    val available: Int,
    val comment: String?,
    val timeSlotId: Long,
    val timeSlotStart: String,
    val timeSlotEnd: String,
    val intersection: Boolean,
    val canSignIn: Boolean,
    val unavailableReasons: List<UnavailableReason>,
    override val signed: Boolean,
    override val teacherIsu: Int,
    override val teacherFio: String,

    override val signEntry: SportQueueEntry?,
    val signQueue: SportQueue?,
    override val friendsBookings: List<FriendSportBooking>
) : SportCommon {

    fun isFreeAttendance() = lessonLevel == 1

    fun shortLessonTypeName(): String {
        return when (lessonLevel) {
            2 -> "Секция (обучение)"
            3 -> "Секция (средний)"
            4 -> "Секция (сборная)"
            else -> when (typeId) {
                1 -> "Открытое занятие"
                2 -> "Свободное посещение"
                5 -> "Задолженность"
                6 -> "Нормативы"
                7 -> "Экстернат"
                8 -> "Дополнительное"
                else -> "Неизвестный вид"
            }
        }
    }
}
