package dev.alllexey.itmowidgets.feature.auth.data

import dev.alllexey.itmoapi.core.MyItmoException
import dev.alllexey.itmoapi.itmoid.TokenSet
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.network.asAppError
import dev.alllexey.itmowidgets.core.network.isCausedByNetworkFailure
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * The ITMO.ID session on MyItmoApi 2.x. Every token write goes through [myItmo]'s `tokens`, which serialises it with
 * any refresh in flight; [tokenStore] only tells a signed-out device from one whose session has ended.
 *
 * This class is the [SessionState] machine: it picks the transition and publishes each state, while [transitions]
 * runs the side effects in between.
 */
class SessionRepositoryImpl(
    private val tokenStore: SessionTokenStore,
    private val myItmo: MyItmoClient,
    private val clock: Clock,
    private val transitions: SessionTransitions,
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

        mutableState.value = SessionState.SignedIn(transitions.currentUser())
        transitions.synchronizeSignedInSession()
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
            transitions.startDemo()
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
            transitions.signOut()
            mutableState.value = SessionState.SignedOut
        }
    }

    private suspend fun signOutOfDemo() {
        mutableState.value = SessionState.SigningOut
        withContext(NonCancellable) {
            transitions.signOutOfDemo()
            mutableState.value = SessionState.SignedOut
        }
    }

    private suspend fun replaceSession(tokens: TokenSet): AppResult<Unit> {
        return try {
            transitions.signIn(tokens)
            mutableState.value = SessionState.SignedIn(transitions.currentUser())
            transitions.completeSignIn()
            AppResult.Success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            transitions.abandonSignIn()
            mutableState.value = SessionState.SignedOut
            AppResult.Failure(AppError.Unknown())
        }
    }

    private companion object {
        const val MAX_TOKEN_RESPONSE_LENGTH = 32 * 1024
        val DEMO_SESSION = SessionState.SignedIn(DemoPeople.ME, demo = true)
    }
}
