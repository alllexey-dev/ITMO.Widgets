package dev.alllexey.itmowidgets.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.feature.auth.domain.ItmoAuthUrlPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The interactive ITMO.ID sign-in. The platform browser reports its page lifecycle and the tokens the callback page
 * posts; browser settings, the cookie wipe and the token interceptor stay with the browser host.
 */
class InteractiveLoginViewModel(
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(InteractiveLoginUiState())
    val uiState: StateFlow<InteractiveLoginUiState> = mutableUiState.asStateFlow()

    private val eventQueue = EventQueue<InteractiveLoginEvent>()
    val events: Flow<InteractiveLoginEvent> = eventQueue.events

    fun onPageStarted() {
        mutableUiState.update { if (it.page == LoginPage.Failed) it else it.copy(page = LoginPage.Loading) }
    }

    fun onPageFinished() {
        mutableUiState.update { if (it.page == LoginPage.Failed) it else it.copy(page = LoginPage.Shown) }
    }

    /** The main frame failed to load or was stopped for leaving the allowed pages. */
    fun onMainFrameError() {
        mutableUiState.update { it.copy(page = LoginPage.Failed) }
    }

    /** The host loads a clean sign-in page after this. */
    fun retry() {
        mutableUiState.update { it.copy(page = LoginPage.Loading, error = null) }
    }

    /** Tokens count only when the page that posted them is the ITMO.ID callback. */
    fun onTokensPosted(pageUrl: String, tokenResponseJson: String) {
        if (!ItmoAuthUrlPolicy.isTokenCallback(pageUrl)) return
        if (mutableUiState.value.completingLogin) return

        mutableUiState.update { it.copy(completingLogin = true, error = null) }
        viewModelScope.launch {
            when (val result = sessionRepository.completeItmoIdLogin(tokenResponseJson)) {
                is AppResult.Success -> eventQueue.send(InteractiveLoginEvent.Completed)
                is AppResult.Failure -> mutableUiState.update {
                    it.copy(completingLogin = false, error = result.error.toAuthText())
                }
            }
        }
    }
}
