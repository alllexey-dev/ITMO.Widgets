package dev.alllexey.itmowidgets.core.recordbook

import dev.alllexey.itmowidgets.core.work.BackgroundCheck

/** The background check of own marks in My ITMO, BARS and the connected sheets; implemented by the recordbook feature. */
interface MarkTracking : BackgroundCheck {
    /** Saves the switch; off stops My ITMO's part and forgets its snapshot, not the unread subjects. */
    suspend fun setMyItmoEnabled(enabled: Boolean)

    /** The same for BARS; off also withdraws the sign-in prompt. */
    suspend fun setBarsEnabled(enabled: Boolean)

    /** Saves the switch; off makes the next read of every sheet a baseline, the totals stay. */
    suspend fun setSheetsEnabled(enabled: Boolean)

    /** Makes the periodic check match the switches and the session; safe to repeat. */
    override suspend fun syncWork()

    override fun stopWork()

    /** One check as soon as the network allows; the debug tools use it. */
    fun checkNow()
}
