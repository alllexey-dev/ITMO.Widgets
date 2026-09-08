package dev.alllexey.itmowidgets.core.sport

import dev.alllexey.itmowidgets.core.util.DataState
import java.time.OffsetDateTime
import kotlinx.coroutines.flow.Flow

/** Own active queues only; never represents a confirmed booking or another user's sport. */
data class PendingSportBooking(
    val queueId: Long,
    val queueKind: QueueKind,
    val lessonId: Long,
    val sectionName: String,
    val start: OffsetDateTime,
    val end: OffsetDateTime,
    val teacherFio: String,
    val roomName: String,
    val isPrediction: Boolean
) {
    enum class QueueKind { FREE, AUTO }
}

/** Read-only projection shared with Schedule; errors must not hide the academic schedule. */
interface PendingSportBookingsRepository {
    fun observePendingBookings(): Flow<DataState<List<PendingSportBooking>>>

    suspend fun refresh()
}
