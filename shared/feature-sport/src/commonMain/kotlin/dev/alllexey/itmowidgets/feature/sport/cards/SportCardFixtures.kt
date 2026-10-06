package dev.alllexey.itmowidgets.feature.sport.cards

import dev.alllexey.itmowidgets.feature.sport.domain.model.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

internal object SportCardFixtures {
    val start: Instant = Instant.parse("2026-09-08T18:30:00+03:00")
    fun lesson(id: Long = 1) = SportLesson(
        isLessonReal = true, lessonId = id, start = start, end = start + 90.minutes,
        sectionId = 1, sectionName = SectionName("Фитнес (функциональная тренировка)"),
        sectionLevel = 1, lessonGroupId = 1, lessonLevel = 1, typeId = 2,
        buildingId = 1, roomId = 1, roomName = "Кронверкский пр., 49 · спортивный зал",
        limit = 20, available = 7, comment = "Возьмите сменную обувь и воду.",
        timeSlotId = 1, timeSlotStart = "18:30", timeSlotEnd = "20:00",
        intersection = false, canSignIn = true, unavailableReasons = emptyList(), signed = false,
        teacherIsu = 100, teacherFio = "Тестовый преподаватель с длинным именем",
        signEntry = null, signQueue = null, friendsBookings = emptyList()
    )
    fun booking(id: Long = 1) = SportBooking(
        isLessonReal = true, lessonId = id, sectionName = lesson().sectionName,
        start = start, end = start + 90.minutes, roomName = lesson().roomName,
        teacherFio = lesson().teacherFio, teacherIsu = 100, sectionLevel = 1, lessonLevel = 2,
        signed = true, signEntry = null, friendsBookings = emptyList()
    )
    fun entry(status: SportQueueEntryStatus = SportQueueEntryStatus.WAITING) = SportFreeSignEntry(
        id = 1, lessonId = 1, position = 3, total = 12, isCancelled = false, status = status,
        createdAt = start - 2.days, firstNotifiedAt = start - 4.hours,
        lastNotifiedAt = start - 3.hours, cancelledAt = null, satisfiedAt = null, expiredAt = null,
        notificationAttempts = 2, maxNotificationAttempts = 5,
        targetLesson = SportQueueLesson(1, 1, lesson().sectionName.raw, 1, 1, 2, 1,
            lesson().roomName, start, start + 90.minutes, 1, 100, lesson().teacherFio),
        forceSign = false
    )
}
