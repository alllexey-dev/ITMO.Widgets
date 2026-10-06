package dev.alllexey.itmowidgets.feature.schedule.data.changes

import dev.alllexey.itmoapi.core.requireResult
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.network.toAppError
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.notification.AppNotifier
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.time.WallClock
import dev.alllexey.itmowidgets.feature.schedule.data.demo.DemoSchedule
import dev.alllexey.itmowidgets.feature.schedule.data.mapper.toModel
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.DetectedChange
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangeDigests
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleCheckResult
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleDiff
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleSnapshot
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.academicSnapshot
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import java.time.Clock
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.toKotlinInstant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Compares the own personal schedule of today..today+7 with the last snapshot on the device. The check reads My ITMO
 * itself: it neither fills the schedule cache nor uploads lessons to Backend.
 *
 * Checks run one at a time; the state has its own lock, so marking changes read never waits for the network.
 */
@Singleton
class ScheduleChangesRepositoryImpl @Inject constructor(
    private val myItmo: MyItmoClient,
    private val store: ScheduleChangesFileStore,
    private val time: AcademicTimeProvider,
    @param:WallClock private val clock: Clock,
    private val notifier: AppNotifier,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers,
) : ScheduleChangesRepository, SessionDataCleaner {

    private val checks = Mutex()
    private val lock = Mutex()
    private val generation = AtomicLong()
    /** Moves with every snapshot reset, so a check that was already asking cannot bring the snapshot back. */
    private val snapshotEpoch = AtomicLong()
    /** The file's state, read once; null until the first reader. */
    private val state = MutableStateFlow<StoredScheduleChanges?>(null)

    override fun observeChanges(): Flow<List<ScheduleChange>> = flow {
        if (demo.isActive()) {
            emit(DemoSchedule.changes(time.today(), clock.instant().toKotlinInstant()))
            return@flow
        }
        lock.withLock { loaded() }
        emitAll(state.filterNotNull().map { stored -> visible(stored.changes) })
    }

    override suspend fun check(): AppResult<ScheduleCheckResult> = checks.withLock {
        if (demo.isActive()) return@withLock AppResult.Success(ScheduleCheckResult.Compared(0))
        val started = generation.get()
        val epoch = snapshotEpoch.get()
        val today = time.today()
        val end = today.plus(WINDOW_DAYS, DateTimeUnit.DAY)
        val current = try {
            withContext(dispatchers.io) { request(today, end) }.academicSnapshot(today, end)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            return@withLock AppResult.Failure(error.toAppError())
        }

        lock.withLock {
            // A sign-out during the request must not leave the previous account's schedule behind.
            if (generation.get() != started) return@withLock AppResult.Failure(AppError.Unauthorized)
            if (snapshotEpoch.get() != epoch) return@withLock AppResult.Success(ScheduleCheckResult.Baseline)
            val stored = loaded()
            val (next, result) = compared(stored, current)
            try {
                persist(next)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                return@withLock AppResult.Failure(AppError.Unknown(error))
            }
            AppResult.Success(result)
        }
    }

    override suspend fun markNotified(ids: Set<String>) {
        if (ids.isEmpty()) return
        update { stored -> stored.copy(changes = stored.changes.map { if (it.id in ids) it.copy(notified = true) else it }) }
    }

    override suspend fun markAllRead() {
        update { stored -> stored.copy(changes = stored.changes.map { if (it.read) it else it.copy(read = true) }) }
        notifier.cancel(AppNotificationChannels.SCHEDULE_CHANGES, ScheduleChangeDigests.NOTIFICATION_ID)
    }

    override suspend fun resetSnapshot() {
        snapshotEpoch.incrementAndGet()
        update { stored -> stored.copy(snapshot = null, emptyHeld = false) }
    }

    override suspend fun clearSessionData() {
        generation.incrementAndGet()
        lock.withLock {
            withContext(dispatchers.io) { store.clear() }
            state.value = StoredScheduleChanges()
        }
    }

    /** The next state and the result of comparing [current] with what is stored. */
    private fun compared(
        stored: StoredScheduleChanges,
        current: ScheduleSnapshot
    ): Pair<StoredScheduleChanges, ScheduleCheckResult> {
        val previous = stored.snapshot?.toModel()
            ?: return stored.copy(snapshot = current.toStored(), emptyHeld = false) to ScheduleCheckResult.Baseline
        val overlap = maxOf(previous.start, current.start)..minOf(previous.end, current.end)
        val previousInOverlap = previous.lessons.count { it.date in overlap }
        if (current.lessons.isEmpty() && previousInOverlap > 0 && !stored.emptyHeld) {
            return stored.copy(emptyHeld = true) to ScheduleCheckResult.EmptyHeld
        }
        if (previousInOverlap == 0 && current.lessons.count { it.date in overlap } > PUBLICATION_THRESHOLD) {
            // A new term's schedule landing on empty weeks is not a list of added lessons.
            return stored.copy(snapshot = current.toStored(), emptyHeld = false) to ScheduleCheckResult.Baseline
        }
        val found = ScheduleDiff.compare(previous, current, time.localNow())
        val detectedAt = clock.millis()
        val next = stored.copy(
            snapshot = current.toStored(),
            emptyHeld = false,
            changes = stored.changes + found.mapIndexed { index, change -> change.toStored("$detectedAt-$index", detectedAt) }
        )
        return next to ScheduleCheckResult.Compared(found.size)
    }

    private suspend fun update(transform: (StoredScheduleChanges) -> StoredScheduleChanges) {
        lock.withLock {
            val next = transform(loaded())
            try {
                persist(next)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // The file is behind until the next write; this process keeps the state in memory.
                state.value = pruned(next)
            }
        }
    }

    /** Writes [next] without expired and surplus changes, then publishes it. Must hold [lock]. */
    private suspend fun persist(next: StoredScheduleChanges) {
        val kept = pruned(next)
        withContext(dispatchers.io) { store.write(kept) }
        state.value = kept
    }

    private fun pruned(next: StoredScheduleChanges): StoredScheduleChanges {
        val oldest = clock.millis() - RETENTION.toMillis()
        val changes = next.changes.filter { it.detectedAt >= oldest }.sortedBy { it.detectedAt }.takeLast(MAX_CHANGES)
        return next.copy(changes = changes)
    }

    /** A corrupt file is removed and the state starts empty. Must hold [lock]. */
    private suspend fun loaded(): StoredScheduleChanges = state.value ?: withContext(dispatchers.io) {
        try {
            store.read() ?: StoredScheduleChanges()
        } catch (_: Exception) {
            store.clear()
            StoredScheduleChanges()
        }
    }.also { state.value = it }

    private fun visible(changes: List<StoredChange>): List<ScheduleChange> {
        val oldest = clock.millis() - RETENTION.toMillis()
        return changes.filter { it.detectedAt >= oldest }
            .sortedByDescending { it.detectedAt }
            .map { it.toModel() }
    }

    /** `null` when My ITMO answered without data. */
    private suspend fun request(start: LocalDate, end: LocalDate): List<DaySchedule> =
        myItmo.schedule.getPersonalSchedule(start, end).requireResult().map { it.toModel() }

    private fun DetectedChange.toStored(id: String, detectedAt: Long): StoredChange {
        val subject = after ?: before
        return StoredChange(
            id = id,
            detectedAt = detectedAt,
            kind = kind.name,
            fields = fields.sortedBy { it.ordinal }.map { it.name },
            subjectName = subject?.subjectName.orEmpty(),
            typeId = subject?.typeId ?: 0,
            flowName = subject?.flowName,
            before = before?.toStored(),
            after = after?.toStored(),
            read = false,
            notified = false
        )
    }

    private companion object {
        const val WINDOW_DAYS = 7
        const val PUBLICATION_THRESHOLD = 5
        const val MAX_CHANGES = 500
        val RETENTION: Duration = Duration.ofDays(30)
    }
}
