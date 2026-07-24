package dev.alllexey.itmowidgets.feature.sport.domain.model

import java.time.OffsetDateTime

enum class SportQueueEntryStatus {
    WAITING,
    NOTIFIED,
    GAVE_UP_NOTIFYING,
    SATISFIED,
    EXPIRED;

    companion object {
        val notifiableStatuses = setOf(WAITING, NOTIFIED)
    }
}

data class SportQueueLesson(
    val id: Long,
    val sectionId: Long,
    val sectionName: String,
    val sectionLevel: Long,
    val level: Long,
    val typeId: Long,
    val buildingId: Long,
    val roomName: String,
    val start: OffsetDateTime,
    val end: OffsetDateTime,
    val timeSlotId: Long,
    val teacherIsu: Long,
    val teacherFio: String
)

sealed interface SportQueueEntry {
    val type: String
    val id: Long
    val position: Int
    val total: Int
    val isCancelled: Boolean
    val status: SportQueueEntryStatus
    val createdAt: OffsetDateTime
    val firstNotifiedAt: OffsetDateTime?
    val lastNotifiedAt: OffsetDateTime?
    val cancelledAt: OffsetDateTime?
    val satisfiedAt: OffsetDateTime?
    val expiredAt: OffsetDateTime?
    val notificationAttempts: Int
    val maxNotificationAttempts: Int
    val targetLesson: SportQueueLesson
}

data class SportFreeSignEntry(
    override val id: Long,
    val lessonId: Long,
    override val position: Int,
    override val total: Int,
    override val isCancelled: Boolean,
    override val status: SportQueueEntryStatus,
    override val createdAt: OffsetDateTime,
    override val firstNotifiedAt: OffsetDateTime?,
    override val lastNotifiedAt: OffsetDateTime?,
    override val cancelledAt: OffsetDateTime?,
    override val satisfiedAt: OffsetDateTime?,
    override val expiredAt: OffsetDateTime?,
    override val notificationAttempts: Int,
    override val maxNotificationAttempts: Int,
    override val targetLesson: SportQueueLesson,
    val forceSign: Boolean
) : SportQueueEntry {
    override val type: String = "free"
}

data class SportAutoSignEntry(
    override val id: Long,
    val prototypeLessonId: Long,
    val realLessonId: Long?,
    override val position: Int,
    override val total: Int,
    override val isCancelled: Boolean,
    override val status: SportQueueEntryStatus,
    override val createdAt: OffsetDateTime,
    override val firstNotifiedAt: OffsetDateTime?,
    override val lastNotifiedAt: OffsetDateTime?,
    override val cancelledAt: OffsetDateTime?,
    override val satisfiedAt: OffsetDateTime?,
    override val expiredAt: OffsetDateTime?,
    override val notificationAttempts: Int,
    override val maxNotificationAttempts: Int,
    override val targetLesson: SportQueueLesson,
    val realLesson: SportQueueLesson?
) : SportQueueEntry {
    override val type: String = "auto"
}

sealed interface SportQueue {
    val type: String
    val lessonId: Long
    val total: Int
}

data class SportFreeSignQueue(
    override val lessonId: Long,
    override val total: Int
) : SportQueue {
    override val type: String = "free"
}

data class SportAutoSignQueue(
    override val lessonId: Long,
    override val total: Int,
    val realLessonId: Long?
) : SportQueue {
    override val type: String = "auto"
}

data class SportAutoSignLimits(
    val limit: Int,
    val available: Int,
    val nextAvailableAt: OffsetDateTime
)
