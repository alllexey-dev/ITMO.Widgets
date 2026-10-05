package dev.alllexey.itmowidgets.core.session

import dev.alllexey.itmowidgets.core.result.AppResult
import kotlinx.coroutines.flow.StateFlow

sealed interface SessionState {

    data object Initializing : SessionState

    data object SigningOut : SessionState

    data object SignedOut : SessionState

    data object ReauthenticationRequired : SessionState

    /** [demo] is the hidden demo session: no tokens, fictional data, nothing reaches the network. */
    data class SignedIn(val user: CurrentUser?, val demo: Boolean = false) : SessionState
}

interface SessionRepository {

    val state: StateFlow<SessionState>

    suspend fun initialize()

    suspend fun completeItmoIdLogin(tokenResponseJson: String): AppResult<Unit>

    suspend fun signInWithRefreshToken(refreshToken: String): AppResult<Unit>

    /** Replaces any session with the demo one; it lasts until [signOut]. */
    suspend fun startDemo()

    suspend fun signOut()
}
