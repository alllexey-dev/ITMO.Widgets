package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The session of one test, held in [mutableState]. Logins succeed without touching it, [initialize] moves it to
 * [resolvesTo] when set, [signOut] signs out; every call is counted.
 */
class FakeSessionRepository(
    initialState: SessionState,
    private val resolvesTo: SessionState? = null,
) : SessionRepository {
    val mutableState = MutableStateFlow(initialState)
    override val state: StateFlow<SessionState> = mutableState
    var initializeCalls = 0
        private set
    var demoStarts = 0
        private set
    var signOutRequests = 0
        private set

    override suspend fun initialize() {
        initializeCalls += 1
        resolvesTo?.let { mutableState.value = it }
    }

    override suspend fun completeItmoIdLogin(tokenResponseJson: String): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun signInWithRefreshToken(refreshToken: String): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun startDemo() {
        demoStarts += 1
    }

    override suspend fun signOut() {
        signOutRequests += 1
        mutableState.value = SessionState.SignedOut
    }
}
