package dev.alllexey.itmowidgets.feature.sport.domain.model

import kotlin.time.Instant

sealed interface SportCommon {
    val type: String
    val isLessonReal: Boolean
    val lessonId: Long
    val sectionName: SectionName
    val start: Instant
    val end: Instant
    val teacherFio: String
    val teacherIsu: Int
    val sectionLevel: Int
    val lessonLevel: Int
    val roomName: String
    val signed: Boolean
    val signEntry: SportQueueEntry?
    val friendsBookings: List<FriendSportBooking>

    fun extractBuildingAddress(): String? = buildingAddressOf(roomName)
}

/** The building's address in MyITMO's room text of a sport lesson: `Кронверкский пр., 49` of `Кронверкский пр., 49, спортивный зал`. */
fun buildingAddressOf(roomName: String): String? {
    val regex = Regex(
        """([А-Яа-яЁё0-9\-\s.]+?,?\s*(?:д\.?|дом)?\s*\d+[А-Яа-яA-Za-z0-9/\-]*\s*(?:к\.?\s*\d+)?)"""
    )
    return regex.find(roomName)?.groupValues?.get(1)
}
