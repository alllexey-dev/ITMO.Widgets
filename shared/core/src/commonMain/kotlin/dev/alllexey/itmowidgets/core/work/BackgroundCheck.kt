package dev.alllexey.itmowidgets.core.work

/**
 * A check that runs on the device without the app open. The app start and the session lifecycle call every check of
 * the set the same way, so each call is idempotent and the order of the set does not matter.
 */
interface BackgroundCheck {
    /** Makes the periodic work match the check's switches and the session; safe to repeat. */
    suspend fun syncWork()

    /** Stops the periodic and the one-off work; safe to repeat. */
    fun stopWork()
}

/** Where one background check runs; the platform scheduler decides the exact moment. */
interface CheckScheduler {
    /** The periodic run; repeating the call keeps its schedule. */
    fun ensurePeriodic()

    /** One run as soon as the network allows, replacing a pending one. */
    fun runOnce()

    /** Stops both the periodic and the one-off run. */
    fun cancel()
}
