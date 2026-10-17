package dev.alllexey.itmowidgets.core.session

interface BackendIdentitySync {

    /**
     * Publishes the ITMO.ID identity to Backend. Returns false only when an upload
     * was due and failed. Android schedules background work unless [scheduleRetry]
     * is false; iOS retries on foreground returns.
     */
    suspend fun sync(scheduleRetry: Boolean = true): Boolean
}
