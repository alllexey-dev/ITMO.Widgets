package dev.alllexey.itmowidgets.feature.schedule.domain.calendar

/** Where calendar synchronization runs; WorkManager decides the exact moment. */
interface CalendarSyncScheduler {
    /** The periodic sync; repeating the call keeps its schedule. */
    fun ensurePeriodic()

    /** One sync as soon as the network allows, replacing a pending one. */
    fun runOnce()

    /** Stops both the periodic and the one-off sync. */
    fun cancel()
}
