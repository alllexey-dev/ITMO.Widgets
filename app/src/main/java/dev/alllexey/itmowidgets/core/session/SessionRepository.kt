package dev.alllexey.itmowidgets.core.session

import dev.alllexey.itmowidgets.core.result.AppResult
import kotlinx.coroutines.flow.StateFlow

sealed interface SessionState {

    data object Initializing : SessionState

    data object SigningOut : SessionState

    data object SignedOut : SessionState

    data object ReauthenticationRequired : SessionState

    data class SignedIn(val user: CurrentUser?) : SessionState
}

interface SessionRepository {

    val state: StateFlow<SessionState>

    suspend fun initialize()

    suspend fun completeItmoIdLogin(tokenResponseJson: String): AppResult<Unit>

    suspend fun signInWithRefreshToken(refreshToken: String): AppResult<Unit>

    suspend fun signOut()
}
