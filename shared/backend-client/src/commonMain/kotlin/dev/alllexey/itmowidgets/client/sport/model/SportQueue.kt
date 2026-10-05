package dev.alllexey.itmowidgets.client.sport.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The size of one lesson's queue. The same closed `"type"` set as [SportQueueEntry] (`"free"`, `"auto"`), with the
 * same rule: encode through `SportQueue.serializer()` to keep `"type"`.
 */
@Serializable
sealed interface SportQueue {
    val lessonId: Long
    val total: Int
}

/** The free-sign queue of a real lesson. */
@Serializable
@SerialName("free")
data class SportFreeSignQueue(
    override val lessonId: Long,
    override val total: Int,
) : SportQueue

/** The auto-sign queue of a prototype lesson ([lessonId]); [realLessonId] is the matched real lesson, if any. */
@Serializable
@SerialName("auto")
data class SportAutoSignQueue(
    override val lessonId: Long,
    override val total: Int,
    val realLessonId: Long?,
) : SportQueue
