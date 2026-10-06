package dev.alllexey.itmowidgets.feature.auth.di

import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.notification.FcmTokenSync
import dev.alllexey.itmowidgets.core.session.BackendDeviceSession
import dev.alllexey.itmowidgets.core.session.BackendIdentitySync
import dev.alllexey.itmowidgets.core.session.SessionLifecycleEffects
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionSnapshot
import dev.alllexey.itmowidgets.core.session.SessionSnapshotWriter
import dev.alllexey.itmowidgets.core.session.SessionState
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * The session's ports of the iOS app process for `SessionTransitions`, what `:app` gives Android through
 * `CoreBridge`. iOS has no background work, notifications or widgets to stop yet, and the App Group snapshots
 * follow the session state ([SessionSnapshotSync]) and the data (each feature's writer), so the effects do nothing;
 * the cleaners run through `SessionDataCleaners`. The card that adds iOS background refresh (IO-16) or notifications
 * (IO-14) gives these effects their work.
 */
internal object IosSessionLifecycleEffects : SessionLifecycleEffects {
    override suspend fun prepareForSessionChange() = Unit

    override suspend fun onSignedIn() = Unit

    override suspend fun onSignedOut() = Unit
}

/** No push token on iOS before the APNs registration (IO-13a replaces it). */
internal object NoFcmTokenSync : FcmTokenSync {
    override suspend fun sync() = Unit
}

/** No device registration before IO-13a: Backend learns of an iOS device only with a push token. */
internal object NoBackendDeviceSession : BackendDeviceSession {
    override suspend fun registerCurrentDevice() = Unit

    override suspend fun unregisterCurrentDevice() = Unit
}

/**
 * No identity upload before an iOS card turns custom services on: nothing on iOS reads Backend yet. Reports
 * success, as Android's sync does when nothing is due.
 */
internal object NoBackendIdentitySync : BackendIdentitySync {
    override suspend fun sync(scheduleRetry: Boolean): Boolean = true
}

/**
 * Keeps `session-v1.json` on the signed-in session, demo included: the demo start runs no lifecycle effect, and the
 * extensions learn of the demo only from this file. A sign-out needs no write: the App Group cleaner removes the
 * file, and a missing file means signed out. Push alerts stay off until IO-13a asks for them.
 */
internal class SessionSnapshotSync(
    private val session: SessionRepository,
    private val writer: SessionSnapshotWriter,
    private val log: AppLog,
) {

    fun launchIn(scope: CoroutineScope): Job = session.state
        .map { state -> (state as? SessionState.SignedIn)?.let(::snapshotOf) }
        // Distinct over every state, so signing in again after a sign-out writes the removed file again.
        .distinctUntilChanged()
        .onEach(::write)
        .launchIn(scope)

    private fun snapshotOf(state: SessionState.SignedIn) =
        SessionSnapshot(isu = state.user?.isu, demo = state.demo, alertsAllowed = false)

    private fun write(snapshot: SessionSnapshot?) {
        if (snapshot == null) return
        try {
            writer.write(snapshot)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            log.warn(TAG, "Could not write the session snapshot", error)
        }
    }

    private companion object {
        const val TAG = "SessionSnapshot"
    }
}
