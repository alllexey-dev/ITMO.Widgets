package dev.alllexey.itmowidgets.feature.social.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.social.SocialRepository
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface UserFriendsUiState {
    data object Loading : UserFriendsUiState
    data class Error(val error: AppError) : UserFriendsUiState
    data class Content(val items: List<UserListItem>, val refreshing: Boolean = false) : UserFriendsUiState
}

@HiltViewModel
class UserFriendsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SocialRepository
) : ViewModel() {
    val isu: Int = checkNotNull(savedStateHandle[UserScreenArgs.ISU])
    val name: String = savedStateHandle[UserScreenArgs.NAME] ?: ""
    private val state = MutableStateFlow<UserFriendsUiState>(UserFriendsUiState.Loading)
    val uiState = state.asStateFlow()
    private val errors = Channel<AppError>(Channel.BUFFERED)
    val refreshErrors = errors.receiveAsFlow()
    private var request: Job? = null

    init { load() }

    fun load() {
        if (request?.isActive == true) return
        // The last answer for this person renders at once; the network only updates the list.
        val previous = state.value as? UserFriendsUiState.Content
            ?: repository.cachedUserFriends(isu)?.let { UserFriendsUiState.Content(it.toItems()) }
        state.value = previous?.copy(refreshing = true) ?: UserFriendsUiState.Loading
        request = viewModelScope.launch {
            when (val result = repository.userFriends(isu)) {
                is AppResult.Success -> state.value = UserFriendsUiState.Content(result.value.toItems())
                is AppResult.Failure -> {
                    // A revoked permission or session must discard content, not keep a private stale list.
                    if (previous != null && result.error !in setOf(
                            AppError.Forbidden, AppError.Unauthorized, AppError.CustomServicesDisabled, AppError.NotFound
                        )) {
                        state.value = previous.copy(refreshing = false)
                        errors.send(result.error)
                    } else state.value = UserFriendsUiState.Error(result.error)
                }
            }
        }
    }

    private fun List<UserProfile>.toItems(): List<UserListItem> = map { profile ->
        UserListItem.User(UserRowUi(
            isu = profile.isu,
            name = profile.user.name,
            pictureUrl = profile.user.pictureUrl,
            subtitle = profile.user.subtitleText(),
            status = profile.relationship.statusText()
        ))
    }
}
