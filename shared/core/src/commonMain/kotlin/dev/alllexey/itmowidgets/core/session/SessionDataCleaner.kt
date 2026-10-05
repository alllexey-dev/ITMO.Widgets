package dev.alllexey.itmowidgets.core.session

fun interface SessionDataCleaner {

    /**
     * Dropping cached session data touches the disk, so implementations are
     * expected to move that work off the caller's thread.
     */
    suspend fun clearSessionData()
}
