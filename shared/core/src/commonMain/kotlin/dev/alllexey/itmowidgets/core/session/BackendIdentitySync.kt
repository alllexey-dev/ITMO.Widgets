package dev.alllexey.itmowidgets.core.session

interface BackendIdentitySync {

    /**
     * Publishes the ITMO.ID identity to Backend. Returns false only when an upload
     * was due and failed; a failed upload is retried by [IdentitySyncWork] unless
     * [scheduleRetry] is false, which the worker itself uses.
     */
    suspend fun sync(scheduleRetry: Boolean = true): Boolean
}
