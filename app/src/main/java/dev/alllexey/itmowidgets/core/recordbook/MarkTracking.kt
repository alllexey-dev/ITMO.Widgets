package dev.alllexey.itmowidgets.core.recordbook

/** The background check of own marks in My ITMO and BARS; implemented by the recordbook feature. */
interface MarkTracking {
    /** Saves the switch; off stops My ITMO's part and forgets its snapshot, not the unread subjects. */
    suspend fun setMyItmoEnabled(enabled: Boolean)

    /** The same for BARS; off also withdraws the sign-in prompt. */
    suspend fun setBarsEnabled(enabled: Boolean)

    /** Makes the periodic check match the switches and the session; safe to repeat. */
    suspend fun syncWork()

    fun stopWork()

    /** One check as soon as the network allows; the debug tools use it. */
    fun checkNow()
}
