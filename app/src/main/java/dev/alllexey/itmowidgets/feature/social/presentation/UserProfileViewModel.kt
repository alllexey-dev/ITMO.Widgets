package dev.alllexey.itmowidgets.feature.social.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.social.SocialRepository
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface UserProfileUiState {
    data object Loading : UserProfileUiState
    data class Error(val error: AppError) : UserProfileUiState
    data class Content(
        val profile: UserProfile,
        val isSelf: Boolean,
        /** A relationship action is in flight. */
        val busy: Boolean
    ) : UserProfileUiState
}

sealed interface UserProfileEvent {
    data class ActionFailed(val error: AppError) : UserProfileEvent
    data class ConfirmRemove(val name: String) : UserProfileEvent
}

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SocialRepository,
    private val currentUserProvider: CurrentUserProvider
) : ViewModel() {

    val isu: Int = checkNotNull(savedStateHandle.get<Int>(UserScreenArgs.ISU)) { "Profile needs an ISU" }

    private val _uiState = MutableStateFlow<UserProfileUiState>(UserProfileUiState.Loading)
    val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

    private val events = Channel<UserProfileEvent>(Channel.BUFFERED)
    val eventFlow: Flow<UserProfileEvent> = events.receiveAsFlow()

    private var isSelf = false

    init {
        load()
    }

    fun load() {
        _uiState.value = UserProfileUiState.Loading
        viewModelScope.launch {
            isSelf = currentUserProvider.getCurrentUser()?.isu == isu
            _uiState.value = when (val result = repository.profile(isu)) {
                is AppResult.Success -> UserProfileUiState.Content(result.value, isSelf, busy = false)
                is AppResult.Failure -> UserProfileUiState.Error(result.error)
            }
        }
    }

    fun onPrimaryAction() {
        val profile = content()?.profile ?: return
        when (profile.relationship) {
            RelationshipState.NONE -> act { repository.sendRequest(isu) }
            RelationshipState.OUTGOING -> act { repository.cancelRequest(isu) }
            RelationshipState.INCOMING -> act { repository.acceptRequest(isu) }
            RelationshipState.FRIENDS -> viewModelScope.launch {
                events.send(UserProfileEvent.ConfirmRemove(profile.user.name))
            }
            RelationshipState.BLOCKED -> Unit
        }
    }

    /** Only incoming requests have a second choice: rejecting. */
    fun onSecondaryAction() {
        if (content()?.profile?.relationship == RelationshipState.INCOMING) {
            act { repository.rejectRequest(isu) }
        }
    }

    fun removeFriend() = act { repository.removeFriend(isu) }

    private fun act(action: suspend () -> AppResult<UserProfile>) {
        val current = content() ?: return
        if (current.busy) return
        _uiState.value = current.copy(busy = true)
        viewModelScope.launch {
            when (val result = action()) {
                is AppResult.Success -> _uiState.value = UserProfileUiState.Content(result.value, isSelf, busy = false)
                is AppResult.Failure -> {
                    events.send(UserProfileEvent.ActionFailed(result.error))
                    content()?.let { _uiState.value = it.copy(busy = false) }
                }
            }
        }
    }

    private fun content() = _uiState.value as? UserProfileUiState.Content
}
