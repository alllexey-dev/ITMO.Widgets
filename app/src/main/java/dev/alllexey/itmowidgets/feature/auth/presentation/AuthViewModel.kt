package dev.alllexey.itmowidgets.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.text.UiText
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val manualLogin = MutableStateFlow(ManualLogin())
    private val demoTaps = DemoEntryTaps()
    private val eventQueue = EventQueue<AuthEvent>()

    val uiState: StateFlow<AuthUiState> = combine(sessionRepository.state, manualLogin, ::toUiState)
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            toUiState(sessionRepository.state.value, manualLogin.value)
        )

    val events: Flow<AuthEvent> = eventQueue.events

    fun signInWithRefreshToken(refreshToken: String) {
        if (manualLogin.value.inProgress) return

        manualLogin.value = ManualLogin(inProgress = true)
        viewModelScope.launch {
            val result = sessionRepository.signInWithRefreshToken(refreshToken)
            manualLogin.value = ManualLogin(error = (result as? AppResult.Failure)?.error?.toAuthText())
        }
    }

    /** [atMillis] is a monotonic time of the tap on the logo. */
    fun onLogoTap(atMillis: Long) {
        if (!demoTaps.tap(atMillis)) return
        if (manualLogin.value.inProgress || sessionRepository.state.value is SessionState.SigningOut) return
        viewModelScope.launch {
            // The confirmation goes first: the demo session replaces this screen.
            eventQueue.send(AuthEvent.DemoStarted)
            sessionRepository.startDemo()
        }
    }

    fun clearError() {
        manualLogin.update { it.copy(error = null) }
    }

    private fun toUiState(sessionState: SessionState, login: ManualLogin) = AuthUiState(
        initializing = sessionState is SessionState.Initializing,
        sessionTransitionInProgress = sessionState is SessionState.SigningOut,
        reauthenticationRequired = sessionState is SessionState.ReauthenticationRequired,
        manualLoginInProgress = login.inProgress,
        error = login.error
    )

    /** The refresh-token sign-in this screen runs itself, beside the session it observes. */
    private data class ManualLogin(val inProgress: Boolean = false, val error: UiText? = null)
}

internal fun AppError.toAuthText(): UiText = when (this) {
    AppError.Network -> UiText.Resource(R.string.auth_error_network)
    AppError.Unauthorized -> UiText.Resource(R.string.auth_error_invalid_credentials)
    else -> UiText.Resource(R.string.auth_error_unknown)
}
