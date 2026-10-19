package dev.alllexey.itmowidgets.feature.auth.di

import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.session.SessionLifecycleEffects
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionSnapshot
import dev.alllexey.itmowidgets.core.session.SessionSnapshotWriter
import dev.alllexey.itmowidgets.core.session.SessionState
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * The session's ports of the iOS app process for `SessionTransitions`, what the platform's Koin modules give
 * Android. iOS has no background work, notifications or widgets to stop yet, and the App Group snapshots
 * follow the session state ([SessionSnapshotSync]) and the data (each feature's writer), so the effects do nothing;
 * the cleaners run through `SessionDataCleaners`.
 */
internal object IosSessionLifecycleEffects : SessionLifecycleEffects {
    override suspend fun prepareForSessionChange() = Unit

    override suspend fun onSignedIn() = Unit

    override suspend fun onSignedOut() = Unit
}

/**
 * Keeps `session-v1.json` on the signed-in session, demo included: the demo start runs no lifecycle effect, and the
 * extensions learn of the demo only from this file. A sign-out needs no write: the App Group cleaner removes the
 * file, and a missing file means signed out. [alertsAllowed] is the last notification settings answer the app read
 * (`IosPushDevice`, on every return to the foreground); [servicesEnabled] the services opt-in, which the notification
 * service checks before it books (it opens no DataStore); [installId] the `X-Client` install id of a real session
 * (`InstallIdPreferences`), which the notification service sends too.
 */
internal class SessionSnapshotSync(
    private val session: SessionRepository,
    private val alertsAllowed: Flow<Boolean>,
    private val servicesEnabled: Flow<Boolean>,
    private val installId: suspend () -> String?,
    private val writer: SessionSnapshotWriter,
    private val log: AppLog,
) {

    fun launchIn(scope: CoroutineScope): Job =
        combine(session.state, alertsAllowed, servicesEnabled) { state, alerts, services ->
            (state as? SessionState.SignedIn)?.let {
                SessionSnapshot(
                    isu = it.user?.isu,
                    demo = it.demo,
                    alertsAllowed = alerts,
                    servicesEnabled = services,
                    installId = if (it.demo) null else installId()
                )
            }
        }
            // Distinct over every state, so signing in again after a sign-out writes the removed file again.
            .distinctUntilChanged()
            .onEach(::write)
            .launchIn(scope)

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
