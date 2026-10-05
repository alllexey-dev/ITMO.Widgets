package dev.alllexey.itmowidgets.client.sport.model

import dev.alllexey.itmowidgets.client.json.WireInstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * One of the user's places in a sport queue. The wire tells the kinds apart by `"type"`: `"free"` for
 * [SportFreeSignEntry], `"auto"` for [SportAutoSignEntry]; another value or a missing `"type"` fails decoding. The
 * subtypes have no `type` property (kotlinx forbids a property named like the discriminator): encode through
 * `SportQueueEntry.serializer()`, the subtype's own serializer drops `"type"`.
 */
@Serializable
sealed interface SportQueueEntry {
    val id: Long
    val position: Int
    val total: Int
    val isCancelled: Boolean
    val status: QueueEntryStatus
    val createdAt: Instant
    val firstNotifiedAt: Instant?
    val lastNotifiedAt: Instant?
    val cancelledAt: Instant?
    val satisfiedAt: Instant?
    val expiredAt: Instant?
    val notificationAttempts: Int
    val maxNotificationAttempts: Int

    /** The real lesson of a free-sign entry, the prototype lesson of an auto-sign entry. */
    val targetLesson: SportLessonDto
}

/** A free-sign entry: a place in the queue of one real lesson ([lessonId]). */
@Serializable
@SerialName("free")
data class SportFreeSignEntry(
    override val id: Long,
    val lessonId: Long,
    override val position: Int,
    override val total: Int,
    override val isCancelled: Boolean,
    override val status: QueueEntryStatus,
    @Serializable(with = WireInstantSerializer::class) override val createdAt: Instant,
    @Serializable(with = WireInstantSerializer::class) override val firstNotifiedAt: Instant?,
    @Serializable(with = WireInstantSerializer::class) override val lastNotifiedAt: Instant?,
    @Serializable(with = WireInstantSerializer::class) override val cancelledAt: Instant?,
    @Serializable(with = WireInstantSerializer::class) override val satisfiedAt: Instant?,
    @Serializable(with = WireInstantSerializer::class) override val expiredAt: Instant?,
    override val notificationAttempts: Int,
    override val maxNotificationAttempts: Int,
    override val targetLesson: SportLessonDto,
    val forceSign: Boolean,
) : SportQueueEntry

/**
 * An auto-sign entry: a standing place for a weekly prototype lesson ([prototypeLessonId]). [realLessonId] and
 * [realLesson] name the concrete lesson once Backend matched one, otherwise both are null.
 */
@Serializable
@SerialName("auto")
data class SportAutoSignEntry(
    override val id: Long,
    val prototypeLessonId: Long,
    val realLessonId: Long?,
    override val position: Int,
    override val total: Int,
    override val isCancelled: Boolean,
    override val status: QueueEntryStatus,
    @Serializable(with = WireInstantSerializer::class) override val createdAt: Instant,
    @Serializable(with = WireInstantSerializer::class) override val firstNotifiedAt: Instant?,
    @Serializable(with = WireInstantSerializer::class) override val lastNotifiedAt: Instant?,
    @Serializable(with = WireInstantSerializer::class) override val cancelledAt: Instant?,
    @Serializable(with = WireInstantSerializer::class) override val satisfiedAt: Instant?,
    @Serializable(with = WireInstantSerializer::class) override val expiredAt: Instant?,
    override val notificationAttempts: Int,
    override val maxNotificationAttempts: Int,
    override val targetLesson: SportLessonDto,
    val realLesson: SportLessonDto?,
) : SportQueueEntry
