package dev.alllexey.itmowidgets.core.session

import dev.alllexey.itmoapi.itmoid.TokenRefreshGuard
import dev.alllexey.itmowidgets.core.storage.CrossProcessLock

/** The lock every process over the shared session takes around a MyItmoApi token refresh. */
const val MY_ITMO_REFRESH_LOCK = "myitmo-refresh"

/**
 * MyItmoApi's refresh seam over this lock: the client's `TokenManager` re-reads the storage once it holds
 * [MY_ITMO_REFRESH_LOCK], so of the app and the notification service only the first refreshes and the other takes
 * the rotated tokens. The lock is held across the refresh request only, never across a suspension of the app
 * (0xdead10cc, see `FileCrossProcessLock`).
 */
fun CrossProcessLock.myItmoRefreshGuard(): TokenRefreshGuard =
    TokenRefreshGuard { action -> withLock(MY_ITMO_REFRESH_LOCK) { action() } }
