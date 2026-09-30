package dev.alllexey.itmowidgets.feature.schedule.domain.changes

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import kotlinx.coroutines.flow.Flow

/** How one check ended when My ITMO answered. */
sealed interface ScheduleCheckResult {
    /** The answer became the snapshot without changes: the first check, or a new schedule after empty weeks. */
    data object Baseline : ScheduleCheckResult

    /** An empty answer against a non-empty snapshot is held once, so a single empty reply cancels nothing. */
    data object EmptyHeld : ScheduleCheckResult

    data class Compared(val found: Int) : ScheduleCheckResult
}

/** The own schedule's snapshot on the device and the changes found against it. Backend is not involved. */
interface ScheduleChangesRepository {
    /** Changes of the last 30 days, newest first; the file is read on the first collection. */
    fun observeChanges(): Flow<List<ScheduleChange>>

    /** Asks My ITMO for today..today+7, compares with the snapshot and stores what changed. */
    suspend fun check(): AppResult<ScheduleCheckResult>

    suspend fun markNotified(ids: Set<String>)

    /** Marks every change read and removes the digest notification. */
    suspend fun markAllRead()

    /** Forgets the snapshot and keeps the history, so the next check is a baseline. */
    suspend fun resetSnapshot()
}
