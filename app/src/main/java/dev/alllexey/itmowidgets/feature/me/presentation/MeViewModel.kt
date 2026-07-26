package dev.alllexey.itmowidgets.feature.me.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MeUiState(
    val user: CurrentUser? = null
)

@HiltViewModel
class MeViewModel @Inject constructor(
    private val currentUserProvider: CurrentUserProvider
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(MeUiState())
    val uiState: StateFlow<MeUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            mutableUiState.value = MeUiState(user = currentUserProvider.getCurrentUser())
        }
    }
}
