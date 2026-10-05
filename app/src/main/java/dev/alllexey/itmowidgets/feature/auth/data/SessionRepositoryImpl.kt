package dev.alllexey.itmowidgets.feature.auth.data

import dev.alllexey.itmoapi.core.MyItmoException
import dev.alllexey.itmoapi.itmoid.TokenSet
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.notification.FcmTokenSync
import dev.alllexey.itmowidgets.core.network.asAppError
import dev.alllexey.itmowidgets.core.network.isCausedByNetworkFailure
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.BackendDeviceSession
import dev.alllexey.itmowidgets.core.session.BackendIdentitySync
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.session.SessionLifecycleEffects
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.storage.DemoPreferences
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import kotlin.time.Clock

/**
 * The ITMO.ID session on MyItmoApi 2.x. Every token write goes through [myItmo]'s `tokens`, which serialises it with
 * any refresh in flight; [tokenStore] only tells a signed-out device from one whose session has ended.
 */
class SessionRepositoryImpl @Inject constructor(
    private val tokenStore: SessionTokenStore,
    private val myItmo: MyItmoClient,
    private val clock: Clock,
    private val currentUserProvider: CurrentUserProvider,
    private val dataCleaners: Set<@JvmSuppressWildcards SessionDataCleaner>,
    private val lifecycleEffects: SessionLifecycleEffects,
    private val backendIdentitySync: BackendIdentitySync,
    private val backendDeviceSession: BackendDeviceSession,
    private val fcmTokenSync: FcmTokenSync,
    private val diagnostics: AppDiagnostics,
    private val demoPreferences: DemoPreferences,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers
) : SessionRepository {

    private val mutableState = MutableStateFlow<SessionState>(SessionState.Initializing)
    override val state: StateFlow<SessionState> = mutableState.asStateFlow()

    override suspend fun initialize() {
        if (mutableState.value !is SessionState.Initializing) return

        if (demo.isActive()) {
            mutableState.value = DEMO_SESSION
            return
        }
        if (!tokenStore.hasRefreshToken()) {
            mutableState.value = SessionState.SignedOut
            return
        }
        if (withContext(dispatchers.io) { myItmo.tokens.isRefreshTokenExpired() }) {
            mutableState.value = SessionState.ReauthenticationRequired
            return
        }

        mutableState.value = SessionState.SignedIn(currentUserProvider.getCurrentUser())
        synchronizeSignedInSession()
    }

    override suspend fun completeItmoIdLogin(
        tokenResponseJson: String
    ): AppResult<Unit> {
        if (tokenResponseJson.length !in 1..MAX_TOKEN_RESPONSE_LENGTH) {
            return AppResult.Failure(AppError.Unauthorized)
        }

        val tokens = try {
            ItmoIdTokenResponse.parse(tokenResponseJson).toTokenSet(clock.now().toEpochMilliseconds())
        } catch (_: Exception) {
            return AppResult.Failure(AppError.Unauthorized)
        }
        return replaceSession(tokens)
    }

    override suspend fun signInWithRefreshToken(
        refreshToken: String
    ): AppResult<Unit> {
        val normalizedToken = refreshToken.trim()
        if (normalizedToken.isEmpty()) {
            return AppResult.Failure(AppError.Unauthorized)
        }

        // One stateless refresh validates the token; the current session stays until it succeeds.
        val tokens = try {
            withContext(dispatchers.io) { myItmo.identity.refresh(normalizedToken) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: MyItmoException) {
            return AppResult.Failure(error.asAppError())
        } catch (error: Exception) {
            return AppResult.Failure(if (error.isCausedByNetworkFailure()) AppError.Network else AppError.Unknown(error))
        }
        return replaceSession(tokens)
    }

    override suspend fun startDemo() {
        if (mutableState.value == DEMO_SESSION) return
        withContext(NonCancellable) {
            runCatching { lifecycleEffects.prepareForSessionChange() }
            clearSessionDataIgnoringFailures()
            // An expired session must not stay behind the demo: widgets would keep refreshing it.
            clearTokens()
            demoPreferences.setDemoActive(true)
            // No Backend identity, FCM token, device or background work: the demo stays on the device.
            mutableState.value = DEMO_SESSION
        }
    }

    override suspend fun signOut() {
        if (
            mutableState.value is SessionState.SigningOut ||
            mutableState.value is SessionState.SignedOut
        ) {
            return
        }
        if ((mutableState.value as? SessionState.SignedIn)?.demo == true) {
            signOutOfDemo()
            return
        }

        // Hide the authenticated graph before its observable caches are cleared.
        // This prevents every visible screen from briefly rendering as empty.
        mutableState.value = SessionState.SigningOut
        withContext(NonCancellable) {
            runCatching { backendDeviceSession.unregisterCurrentDevice() }
            runCatching { lifecycleEffects.prepareForSessionChange() }
            clearSessionDataIgnoringFailures()
            clearTokens()
            runCatching { lifecycleEffects.onSignedOut() }
            mutableState.value = SessionState.SignedOut
        }
    }

    private suspend fun signOutOfDemo() {
        mutableState.value = SessionState.SigningOut
        withContext(NonCancellable) {
            clearSessionDataIgnoringFailures()
            runCatching { demoPreferences.setDemoActive(false) }
            runCatching { lifecycleEffects.onSignedOut() }
            mutableState.value = SessionState.SignedOut
        }
    }

    private suspend fun replaceSession(tokens: TokenSet): AppResult<Unit> {
        return try {
            lifecycleEffects.prepareForSessionChange()
            clearSessionData()
            demoPreferences.setDemoActive(false)
            withContext(dispatchers.io) { myItmo.tokens.replaceTokens(tokens) }
            mutableState.value = SessionState.SignedIn(currentUserProvider.getCurrentUser())
            runCatching { lifecycleEffects.onSignedIn() }
            synchronizeSignedInSession()
            AppResult.Success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            clearTokens()
            clearSessionDataIgnoringFailures()
            runCatching { lifecycleEffects.onSignedOut() }
            mutableState.value = SessionState.SignedOut
            AppResult.Failure(AppError.Unknown())
        }
    }

    private suspend fun synchronizeSignedInSession() {
        // Identity and token sync record their own failures; device registration reports here.
        runCatching { backendIdentitySync.sync() }
        runCatching { fcmTokenSync.sync() }
        runCatching { backendDeviceSession.registerCurrentDevice() }
            .onFailure { diagnostics.warn("Session", "Device registration after sign-in failed", it) }
    }

    private suspend fun clearTokens() {
        withContext(dispatchers.io) { myItmo.tokens.replaceTokens(null) }
    }

    private suspend fun clearSessionData() {
        dataCleaners.forEach { cleaner -> cleaner.clearSessionData() }
    }

    private suspend fun clearSessionDataIgnoringFailures() {
        dataCleaners.forEach { cleaner -> runCatching { cleaner.clearSessionData() } }
    }

    private companion object {
        const val MAX_TOKEN_RESPONSE_LENGTH = 32 * 1024
        val DEMO_SESSION = SessionState.SignedIn(DemoPeople.ME, demo = true)
    }
}
