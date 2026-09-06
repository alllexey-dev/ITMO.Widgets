package dev.alllexey.itmowidgets.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.text.UiText
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class InteractiveLoginUiState(
    val completingLogin: Boolean = false,
    val error: UiText? = null
)

sealed interface InteractiveLoginEvent {
    data object Completed : InteractiveLoginEvent
}

@HiltViewModel
class InteractiveLoginViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(InteractiveLoginUiState())
    val uiState: StateFlow<InteractiveLoginUiState> = mutableUiState.asStateFlow()

    private val eventChannel = Channel<InteractiveLoginEvent>(Channel.BUFFERED)
    val events: Flow<InteractiveLoginEvent> = eventChannel.receiveAsFlow()

    fun completeLogin(tokenResponseJson: String) {
        if (mutableUiState.value.completingLogin) return

        mutableUiState.value = InteractiveLoginUiState(completingLogin = true)
        viewModelScope.launch {
            when (val result = sessionRepository.completeItmoIdLogin(tokenResponseJson)) {
                is AppResult.Success -> eventChannel.send(InteractiveLoginEvent.Completed)
                is AppResult.Failure -> {
                    mutableUiState.value = InteractiveLoginUiState(
                        error = result.error.toAuthText()
                    )
                }
            }
        }
    }

    fun clearError() {
        mutableUiState.value = mutableUiState.value.copy(error = null)
    }
}
