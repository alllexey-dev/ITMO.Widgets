package dev.alllexey.itmowidgets.core.session

import dev.alllexey.itmoapi.core.MyItmoException
import dev.alllexey.itmoapi.itmoid.TokenRefreshGuard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Tells the session that ITMO.ID rejected it. [guard] wraps MyItmoApi's refresh transaction, and the only
 * [MyItmoException.Auth] thrown inside it is the token endpoint's OAuth error or a refresh token that has expired
 * locally (or a session already gone from storage). Network failures, ITMO.ID 5xx and garbled answers are other
 * exception types, and MyITMO's own 401/403 is thrown outside the refresh, so none of them reach the listeners.
 */
class RefreshRejections {

    private val listeners = MutableStateFlow<List<() -> Unit>>(emptyList())

    fun onRejected(listener: () -> Unit) {
        listeners.update { it + listener }
    }

    /** [delegate] is the platform's lock (iOS: cross-process); the default is MyItmoApi's in-process one. */
    fun guard(delegate: TokenRefreshGuard = TokenRefreshGuard { it() }): TokenRefreshGuard =
        TokenRefreshGuard { action ->
            delegate.withLock {
                try {
                    action()
                } catch (rejected: MyItmoException.Auth) {
                    listeners.value.forEach { it() }
                    throw rejected
                }
            }
        }
}
