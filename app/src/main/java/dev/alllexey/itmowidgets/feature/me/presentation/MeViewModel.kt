package dev.alllexey.itmowidgets.feature.me.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MeUiState(
    val user: CurrentUser? = null,
    val signOutInProgress: Boolean = false
)

@HiltViewModel
class MeViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(MeUiState())
    val uiState: StateFlow<MeUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionRepository.state.collect { sessionState ->
                mutableUiState.value = mutableUiState.value.copy(
                    user = (sessionState as? SessionState.SignedIn)?.user
                )
            }
        }
    }

    fun signOut() {
        if (mutableUiState.value.signOutInProgress) return

        mutableUiState.value = mutableUiState.value.copy(signOutInProgress = true)
        viewModelScope.launch {
            sessionRepository.signOut()
            mutableUiState.value = mutableUiState.value.copy(signOutInProgress = false)
        }
    }
}
