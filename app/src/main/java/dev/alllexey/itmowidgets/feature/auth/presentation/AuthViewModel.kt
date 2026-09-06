package dev.alllexey.itmowidgets.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.text.UiText
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class AuthUiState(
    val initializing: Boolean = true,
    val sessionTransitionInProgress: Boolean = false,
    val reauthenticationRequired: Boolean = false,
    val manualLoginInProgress: Boolean = false,
    val error: UiText? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(
        AuthUiState().withSessionState(sessionRepository.state.value)
    )
    val uiState: StateFlow<AuthUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionRepository.state.collectLatest { sessionState ->
                mutableUiState.value = mutableUiState.value.withSessionState(sessionState)
            }
        }
    }

    fun signInWithRefreshToken(refreshToken: String) {
        if (mutableUiState.value.manualLoginInProgress) return

        mutableUiState.value = mutableUiState.value.copy(
            manualLoginInProgress = true,
            error = null
        )
        viewModelScope.launch {
            val result = sessionRepository.signInWithRefreshToken(refreshToken)
            mutableUiState.value = mutableUiState.value.copy(
                manualLoginInProgress = false,
                error = (result as? AppResult.Failure)?.error?.toAuthText()
            )
        }
    }

    fun clearError() {
        mutableUiState.value = mutableUiState.value.copy(error = null)
    }
}

private fun AuthUiState.withSessionState(sessionState: SessionState): AuthUiState = copy(
    initializing = sessionState is SessionState.Initializing,
    sessionTransitionInProgress = sessionState is SessionState.SigningOut,
    reauthenticationRequired = sessionState is SessionState.ReauthenticationRequired
)

internal fun AppError.toAuthText(): UiText = when (this) {
    AppError.Network -> UiText.Resource(R.string.auth_error_network)
    AppError.Unauthorized -> UiText.Resource(R.string.auth_error_invalid_credentials)
    else -> UiText.Resource(R.string.auth_error_unknown)
}
