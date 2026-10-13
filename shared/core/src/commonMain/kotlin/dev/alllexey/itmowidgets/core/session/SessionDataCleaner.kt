package dev.alllexey.itmowidgets.core.session

fun interface SessionDataCleaner {

    /**
     * Dropping cached session data touches the disk, so implementations are
     * expected to move that work off the caller's thread.
     */
    suspend fun clearSessionData()
}

/**
 * Clears the platform's web store: the WebView's or WebKit's cookies and site storage. The interactive ITMO.ID sign-in
 * skips it, since its page left the SSO session that issued the new tokens there and BARS signs in with that session
 * (ADR 0012). Every other transition, sign-out included, runs it.
 */
interface WebStoreDataCleaner : SessionDataCleaner
