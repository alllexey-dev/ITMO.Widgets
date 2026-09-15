package dev.alllexey.itmowidgets.feature.auth.data

import dev.alllexey.itmowidgets.core.notification.FcmTokenSync
import api.myitmo.MyItmo
import api.myitmo.model.other.TokenResponse
import com.google.gson.Gson
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
import dev.alllexey.itmowidgets.core.session.SessionTokens
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class SessionRepositoryImpl @Inject constructor(
    private val tokenStore: SessionTokenStore,
    private val myItmo: MyItmo,
    private val gson: Gson,
    private val currentUserProvider: CurrentUserProvider,
    private val refreshTokenAuthenticator: RefreshTokenAuthenticator,
    private val dataCleaners: Set<@JvmSuppressWildcards SessionDataCleaner>,
    private val lifecycleEffects: SessionLifecycleEffects,
    private val backendIdentitySync: BackendIdentitySync,
    private val backendDeviceSession: BackendDeviceSession,
    private val fcmTokenSync: FcmTokenSync
) : SessionRepository {

    private val mutableState = MutableStateFlow<SessionState>(SessionState.Initializing)
    override val state: StateFlow<SessionState> = mutableState.asStateFlow()

    override suspend fun initialize() {
        if (mutableState.value !is SessionState.Initializing) return

        if (!tokenStore.hasRefreshToken()) {
            mutableState.value = SessionState.SignedOut
            return
        }
        if (myItmo.isRefreshTokenExpired) {
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
            gson.fromJson(tokenResponseJson, TokenResponse::class.java)?.toSessionTokens()
                ?: return AppResult.Failure(AppError.Unauthorized)
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

        val tokens = try {
            refreshTokenAuthenticator.authenticate(normalizedToken)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            return AppResult.Failure(
                if (error.isCausedByNetworkFailure()) {
                    AppError.Network
                } else {
                    AppError.Unauthorized
                }
            )
        }
        return replaceSession(tokens)
    }

    override suspend fun signOut() {
        if (
            mutableState.value is SessionState.SigningOut ||
            mutableState.value is SessionState.SignedOut
        ) {
            return
        }

        // Hide the authenticated graph before its observable caches are cleared.
        // This prevents every visible screen from briefly rendering as empty.
        mutableState.value = SessionState.SigningOut
        withContext(NonCancellable) {
            runCatching { backendDeviceSession.unregisterCurrentDevice() }
            runCatching { lifecycleEffects.prepareForSessionChange() }
            clearSessionDataIgnoringFailures()
            withContext(Dispatchers.IO) { tokenStore.clearTokens() }
            runCatching { lifecycleEffects.onSignedOut() }
            mutableState.value = SessionState.SignedOut
        }
    }

    private suspend fun replaceSession(tokens: SessionTokens): AppResult<Unit> {
        return try {
            lifecycleEffects.prepareForSessionChange()
            clearSessionData()
            withContext(Dispatchers.IO) { tokenStore.replaceWithTokens(tokens) }
            mutableState.value = SessionState.SignedIn(currentUserProvider.getCurrentUser())
            runCatching { lifecycleEffects.onSignedIn() }
            synchronizeSignedInSession()
            AppResult.Success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            withContext(Dispatchers.IO) { tokenStore.clearTokens() }
            clearSessionDataIgnoringFailures()
            runCatching { lifecycleEffects.onSignedOut() }
            mutableState.value = SessionState.SignedOut
            AppResult.Failure(AppError.Unknown())
        }
    }

    private suspend fun synchronizeSignedInSession() {
        runCatching { backendIdentitySync.sync() }
        runCatching { fcmTokenSync.sync() }
        runCatching { backendDeviceSession.registerCurrentDevice() }
    }

    private suspend fun clearSessionData() {
        dataCleaners.forEach { cleaner -> cleaner.clearSessionData() }
    }

    private suspend fun clearSessionDataIgnoringFailures() {
        dataCleaners.forEach { cleaner -> runCatching { cleaner.clearSessionData() } }
    }

    private companion object {
        const val MAX_TOKEN_RESPONSE_LENGTH = 32 * 1024
    }
}
