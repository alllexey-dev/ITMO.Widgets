package dev.alllexey.itmowidgets.core.session

import dev.alllexey.itmowidgets.core.platform.AppForeground
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** iOS retries identity uploads on foreground returns; sign-in and initialization already sync the session. */
class BackendIdentityForegroundSync(
    private val foreground: AppForeground,
    private val session: SessionRepository,
    private val identity: BackendIdentitySync,
) {
    fun launchIn(scope: CoroutineScope): Job = foreground.returns().onEach {
        val signedIn = session.state.value as? SessionState.SignedIn
        if (signedIn != null && !signedIn.demo) identity.sync(scheduleRetry = false)
    }.launchIn(scope)
}
