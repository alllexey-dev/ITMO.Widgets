package dev.alllexey.itmowidgets.core.sport

import dev.alllexey.itmowidgets.core.util.DataState
import java.time.OffsetDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

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

/** Read-only projection shared with Schedule and widgets; errors must not hide academic data. */
interface PendingSportBookingsRepository {
    fun observePendingBookings(): Flow<DataState<List<PendingSportBooking>>>

    suspend fun refresh()

    /**
     * Reads the available source snapshot without network requests. Call [refresh]
     * first when fresh data is needed. Production returns an error, not a synthetic
     * empty success, if the sources have not initialized within a bounded wait.
     */
    suspend fun getPendingBookings(): DataState<List<PendingSportBooking>> = observePendingBookings().first()
}
