package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Copies WebKit's ITMO.ID cookies to the Keychain after a WebView session (the ITMO.ID sign-in, the BARS sign-in, the
 * hidden BARS renewal), so the background can replay them (SP-21). A failure is logged by type only and keeps the
 * previous copy.
 */
class ItmoIdCookieExport(
    private val source: WebKitCookieSource,
    private val cookies: KeychainItmoIdCookies,
    private val dispatchers: AppDispatchers,
    private val log: AppLog,
) {

    suspend fun run() {
        try {
            val all = withContext(dispatchers.main) {
                suspendCancellableCoroutine { continuation ->
                    source.webKitCookies { cookies -> if (continuation.isActive) continuation.resume(cookies) }
                }
            }
            cookies.replaceFromWebKit(all)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (failure: Exception) {
            log.warn(TAG, "Could not copy the ITMO.ID cookies: ${failure::class.simpleName}")
        }
    }

    private companion object {
        const val TAG = "ItmoIdCookies"
    }
}

/**
 * Runs [ItmoIdCookieExport] when an interactive ITMO.ID sign-in has just finished: the session turns signed in from
 * signed out or from a required re-sign-in. A launch that reads a stored session copies nothing, so a copy the
 * background renewed is never replaced with WebKit's older cookies; the demo has no ITMO.ID session.
 */
internal class ItmoIdCookieExportOnSignIn(
    private val session: SessionRepository,
    private val export: ItmoIdCookieExport,
) {

    fun launchIn(scope: CoroutineScope): Job = session.state
        .runningFold(Transition(null, session.state.value)) { last, state -> Transition(last.current, state) }
        .filter { it.isInteractiveSignIn }
        .onEach { export.run() }
        .launchIn(scope)

    private class Transition(val previous: SessionState?, val current: SessionState) {
        val isInteractiveSignIn: Boolean
            get() = (current as? SessionState.SignedIn)?.demo == false &&
                (previous == SessionState.SignedOut || previous == SessionState.ReauthenticationRequired)
    }
}
