package dev.alllexey.itmowidgets.feature.auth.data

import dev.alllexey.itmoapi.itmoid.TokenSet
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.notification.FcmTokenSync
import dev.alllexey.itmowidgets.core.session.BackendDeviceSession
import dev.alllexey.itmowidgets.core.session.BackendIdentitySync
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.session.SessionLifecycleEffects
import dev.alllexey.itmowidgets.core.storage.DemoPreferences
import kotlinx.coroutines.withContext

/**
 * The side effects of every session change, in the order 2.2 ran them. [SessionRepositoryImpl] decides which
 * transition runs, publishes each [dev.alllexey.itmowidgets.core.session.SessionState] and holds the
 * `NonCancellable` sections, so a transition never publishes state itself.
 *
 * [dataCleaners] is read on every transition: the platform's DI graph supplies it without fixing the order here.
 */
class SessionTransitions(
    private val myItmo: MyItmoClient,
    private val currentUserProvider: CurrentUserProvider,
    private val dataCleaners: () -> Collection<SessionDataCleaner>,
    private val lifecycleEffects: SessionLifecycleEffects,
    private val backendIdentitySync: BackendIdentitySync,
    private val backendDeviceSession: BackendDeviceSession,
    private val fcmTokenSync: FcmTokenSync,
    private val diagnostics: AppDiagnostics,
    private val demoPreferences: DemoPreferences,
    private val dispatchers: AppDispatchers
) {

    /** The user the stored tokens describe. */
    suspend fun currentUser(): CurrentUser? = currentUserProvider.getCurrentUser()

    /**
     * Replaces whatever session is on the device with [tokens]. Strict: a failed step throws, and the caller ends
     * the attempt with [abandonSignIn].
     */
    suspend fun signIn(tokens: TokenSet) {
        lifecycleEffects.prepareForSessionChange()
        dataCleaners().forEach { cleaner -> cleaner.clearSessionData() }
        demoPreferences.setDemoActive(false)
        withContext(dispatchers.io) { myItmo.tokens.replaceTokens(tokens) }
    }

    /** Runs after the signed-in state is published. */
    suspend fun completeSignIn() {
        runCatching { lifecycleEffects.onSignedIn() }
        synchronizeSignedInSession()
    }

    /** Leaves no half-replaced session behind after [signIn] or [completeSignIn] failed. */
    suspend fun abandonSignIn() {
        clearTokens()
        clearSessionDataIgnoringFailures()
        runCatching { lifecycleEffects.onSignedOut() }
    }

    /** Backend identity, FCM token and device registration of a signed-in, non-demo session. */
    suspend fun synchronizeSignedInSession() {
        // Identity and token sync record their own failures; device registration reports here.
        runCatching { backendIdentitySync.sync() }
        runCatching { fcmTokenSync.sync() }
        runCatching { backendDeviceSession.registerCurrentDevice() }
            .onFailure { diagnostics.warn("Session", "Device registration after sign-in failed", it) }
    }

    suspend fun startDemo() {
        runCatching { lifecycleEffects.prepareForSessionChange() }
        clearSessionDataIgnoringFailures()
        // An expired session must not stay behind the demo: widgets would keep refreshing it.
        clearTokens()
        demoPreferences.setDemoActive(true)
        // No Backend identity, FCM token, device or background work: the demo stays on the device.
    }

    /** Runs after `SigningOut` is published, so no screen renders its caches emptied. */
    suspend fun signOut() {
        runCatching { backendDeviceSession.unregisterCurrentDevice() }
        runCatching { lifecycleEffects.prepareForSessionChange() }
        clearSessionDataIgnoringFailures()
        clearTokens()
        runCatching { lifecycleEffects.onSignedOut() }
    }

    /** The demo registered no device and holds no tokens. */
    suspend fun signOutOfDemo() {
        clearSessionDataIgnoringFailures()
        runCatching { demoPreferences.setDemoActive(false) }
        runCatching { lifecycleEffects.onSignedOut() }
    }

    private suspend fun clearTokens() {
        withContext(dispatchers.io) { myItmo.tokens.replaceTokens(null) }
    }

    private suspend fun clearSessionDataIgnoringFailures() {
        dataCleaners().forEach { cleaner -> runCatching { cleaner.clearSessionData() } }
    }
}
