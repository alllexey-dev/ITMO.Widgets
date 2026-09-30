package dev.alllexey.itmowidgets.core.schedule

import kotlinx.coroutines.flow.Flow

/** The background check of the own schedule for changes; implemented by the schedule feature. */
interface ScheduleChangeTracking {
    fun observeEnabled(): Flow<Boolean>

    /** Saves the switch; on starts the periodic check, off stops it and forgets the snapshot, not the history. */
    suspend fun setEnabled(enabled: Boolean)

    /** Makes the periodic check match the switch and the session; safe to repeat. */
    suspend fun syncWork()

    fun stopWork()

    /** One check as soon as the network allows; the debug tools use it. */
    fun checkNow()
}
