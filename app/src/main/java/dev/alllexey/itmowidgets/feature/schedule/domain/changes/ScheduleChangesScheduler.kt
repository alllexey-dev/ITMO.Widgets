package dev.alllexey.itmowidgets.feature.schedule.domain.changes

/** Where the background check runs; WorkManager decides the exact moment. */
interface ScheduleChangesScheduler {
    /** The periodic check; repeating the call keeps its schedule. */
    fun ensurePeriodic()

    /** One check as soon as the network allows, replacing a pending one. */
    fun runOnce()

    /** Stops both the periodic and the one-off check. */
    fun cancel()
}
