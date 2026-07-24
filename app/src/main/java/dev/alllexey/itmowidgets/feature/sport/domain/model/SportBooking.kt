package dev.alllexey.itmowidgets.feature.sport.domain.model

import java.time.OffsetDateTime

data class SportBooking(
    override val type: String = "booking",
    override val isLessonReal: Boolean,
    override val lessonId: Long,
    override val sectionName: SectionName,
    override val start: OffsetDateTime,
    override val end: OffsetDateTime,
    override val roomName: String,
    override val teacherFio: String,
    override val teacherIsu: Int,
    override val sectionLevel: Int,
    override val lessonLevel: Int,
    override val signed: Boolean,
    override val signEntry: SportQueueEntry?,
    override val friendsBookings: List<FriendSportBooking>
) : SportCommon
