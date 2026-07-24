package dev.alllexey.itmowidgets.feature.sport.domain.model

import java.time.OffsetDateTime

sealed interface SportCommon {
    val type: String
    val isLessonReal: Boolean
    val lessonId: Long
    val sectionName: SectionName
    val start: OffsetDateTime
    val end: OffsetDateTime
    val teacherFio: String
    val teacherIsu: Int
    val sectionLevel: Int
    val lessonLevel: Int
    val roomName: String
    val signed: Boolean
    val signEntry: SportQueueEntry?
    val friendsBookings: List<FriendSportBooking>

    fun extractBuildingAddress(): String? {
        val regex = Regex(
            """([А-Яа-яЁё0-9\-\s.]+?,?\s*(?:д\.?|дом)?\s*\d+[А-Яа-яA-Za-z0-9/\-]*\s*(?:к\.?\s*\d+)?)"""
        )
        val result = regex.find(roomName)?.groupValues?.get(1)
        return result
    }
}
