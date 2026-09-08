package dev.alllexey.itmowidgets.feature.sport.data.repository

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.util.CustomDataState
import dev.alllexey.itmowidgets.core.util.DataState
import dev.alllexey.itmowidgets.core.util.dataOrNull
import dev.alllexey.itmowidgets.core.util.errorOrNull
import dev.alllexey.itmowidgets.feature.sport.data.mapper.toBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntryStatus
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withTimeoutOrNull

/** Uses the same queue updates as Sport, without requiring friend data or modifying academic caches. */
class PendingSportBookingsRepositoryImpl @Inject constructor(
    private val bookings: SportBookingRepository,
    private val sportData: SportDataRepository,
    private val customServices: CustomServicesRepository,
    private val timeProvider: AcademicTimeProvider
) : PendingSportBookingsRepository {

    override fun observePendingBookings() = pendingProjection()
        .onStart { emit(DataState.Success(emptyList())) }
        .distinctUntilChanged()

    override suspend fun getPendingBookings(): DataState<List<PendingSportBooking>> {
        if (!customServices.isEnabled()) return DataState.Success(emptyList())
        // Source replay may still be absent before the first refresh (or after a
        // cancelled initialization). Do not hang a widget worker or mistake the
        // UI observer's initial empty state for a completed source snapshot.
        return withTimeoutOrNull(SNAPSHOT_WAIT_TIMEOUT_MILLIS) {
            pendingProjection().first()
        } ?: DataState.Error(AppError.Unknown())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun pendingProjection() = customServices.observeEnabled().flatMapLatest { enabled ->
        if (!enabled) {
            flowOf<DataState<List<PendingSportBooking>>>(DataState.Success(emptyList()))
        } else {
            combine(
                bookings.observeConfirmedSportBookings(),
                sportData.observeSportQueueEntries()
            ) { confirmed, queues ->
                if (queues is CustomDataState.Disabled) {
                    return@combine DataState.Success(emptyList())
                }
                val error = queues.errorOrNull() ?: confirmed.errorOrNull()
                if (error != null) return@combine DataState.Error(error)

                val signedIds = confirmed.dataOrNull().orEmpty().mapTo(mutableSetOf()) { it.lessonId }
                val now = timeProvider.now()
                val pending = queues.dataOrNull().orEmpty()
                    .filter { !it.isCancelled && it.status in SportQueueEntryStatus.notifiableStatuses }
                    .map { it.toBooking() }
                    .filter { it.lessonId !in signedIds && it.start.isAfter(now) }
                    .distinctBy { it.lessonId }
                    .sortedBy { it.start }
                    .map { booking ->
                        val entry = checkNotNull(booking.signEntry)
                        PendingSportBooking(
                            queueId = entry.id,
                            queueKind = if (entry is SportAutoSignEntry) PendingSportBooking.QueueKind.AUTO
                                else PendingSportBooking.QueueKind.FREE,
                            lessonId = booking.lessonId,
                            sectionName = booking.sectionName.raw.trim(),
                            start = booking.start,
                            end = booking.end,
                            teacherFio = booking.teacherFio.trim(),
                            roomName = booking.roomName.trim(),
                            isPrediction = !booking.isLessonReal
                        )
                    }
                DataState.Success(pending)
            }
        }
    }

    override suspend fun refresh() {
        if (!customServices.isEnabled()) return
        coroutineScope {
            awaitAll(
                async { bookings.refreshSportBookings() },
                async { sportData.refreshSportQueueEntries() }
            )
        }
    }

    private companion object {
        const val SNAPSHOT_WAIT_TIMEOUT_MILLIS = 1_000L
    }
}
