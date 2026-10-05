package dev.alllexey.itmowidgets.feature.sport.data.repository

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.result.errorOrNull
import dev.alllexey.itmowidgets.core.result.valueOrNull
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.time.javaNow
import dev.alllexey.itmowidgets.feature.sport.data.mapper.toBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntryStatus
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import javax.inject.Inject
import kotlin.time.toKotlinInstant
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
        .onStart { emit(AppResult.Success(emptyList())) }
        .distinctUntilChanged()

    override suspend fun getPendingBookings(): AppResult<List<PendingSportBooking>> {
        if (!customServices.isEnabled()) return AppResult.Success(emptyList())
        // Source replay may still be absent before the first refresh (or after a
        // cancelled initialization). Do not hang a widget worker or mistake the
        // UI observer's initial empty state for a completed source snapshot.
        return withTimeoutOrNull(SNAPSHOT_WAIT_TIMEOUT_MILLIS) {
            pendingProjection().first()
        } ?: AppResult.Failure(AppError.Unknown())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun pendingProjection() = customServices.observeEnabled().flatMapLatest { enabled ->
        if (!enabled) {
            flowOf<AppResult<List<PendingSportBooking>>>(AppResult.Success(emptyList()))
        } else {
            combine(
                bookings.observeConfirmedSportBookings(),
                sportData.observeSportQueueEntries()
            ) { confirmed, queues ->
                if (queues is LoadState.Disabled) {
                    return@combine AppResult.Success(emptyList())
                }
                val error = queues.errorOrNull() ?: confirmed.errorOrNull()
                if (error != null) return@combine AppResult.Failure(error)

                val signedIds = confirmed.valueOrNull().orEmpty().mapTo(mutableSetOf()) { it.lessonId }
                val now = timeProvider.javaNow()
                val pending = queues.valueOrNull().orEmpty()
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
                            start = booking.start.toInstant().toKotlinInstant(),
                            end = booking.end.toInstant().toKotlinInstant(),
                            teacherFio = booking.teacherFio.trim(),
                            roomName = booking.roomName.trim(),
                            isPrediction = !booking.isLessonReal,
                            teacherIsu = booking.teacherIsu
                        )
                    }
                AppResult.Success(pending)
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
