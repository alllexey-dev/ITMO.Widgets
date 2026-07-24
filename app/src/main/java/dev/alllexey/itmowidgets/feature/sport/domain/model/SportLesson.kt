package dev.alllexey.itmowidgets.feature.sport.domain.model

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

    val kind: SportLessonKind
        get() = when (lessonLevel) {
            2 -> SportLessonKind.TRAINING_SECTION
            3 -> SportLessonKind.INTERMEDIATE_SECTION
            4 -> SportLessonKind.TEAM_SECTION
            else -> when (typeId) {
                1 -> SportLessonKind.OPEN
                2 -> SportLessonKind.FREE_ATTENDANCE
                5 -> SportLessonKind.DEBT
                6 -> SportLessonKind.STANDARDS
                7 -> SportLessonKind.EXTERNAL
                8 -> SportLessonKind.ADDITIONAL
                else -> SportLessonKind.UNKNOWN
            }
        }
}

enum class SportLessonKind {
    TRAINING_SECTION,
    INTERMEDIATE_SECTION,
    TEAM_SECTION,
    OPEN,
    FREE_ATTENDANCE,
    DEBT,
    STANDARDS,
    EXTERNAL,
    ADDITIONAL,
    UNKNOWN
}
