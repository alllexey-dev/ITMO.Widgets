package dev.alllexey.itmowidgets.feature.recordbook.domain.marks

/** Where the background mark check runs; WorkManager decides the exact moment. */
interface MarksScheduler {
    /** The periodic check; repeating the call keeps its schedule. */
    fun ensurePeriodic()

    /** One check as soon as the network allows, replacing a pending one. */
    fun runOnce()

    /** Stops both the periodic and the one-off check. */
    fun cancel()
}
