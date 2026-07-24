package dev.alllexey.itmowidgets.feature.friendselector.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.friend.FriendListState
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.feature.friendselector.domain.FriendSelectionHistory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class FriendSelectorUiState {
    data object Loading : FriendSelectorUiState()
    data object Disabled : FriendSelectorUiState()
    data object Empty : FriendSelectorUiState()
    data class Content(
        val friends: List<UserSummary>,
        val recentFriends: List<UserSummary>,
        val currentUser: UserSummary? = null
    ) : FriendSelectorUiState()
    data class Error(val error: AppError) : FriendSelectorUiState()
}

@HiltViewModel
class FriendSelectorViewModel @Inject constructor(
    private val repository: FriendRepository,
    private val history: FriendSelectionHistory
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)

    private val _uiState = MutableStateFlow<FriendSelectorUiState>(
        FriendSelectorUiState.Loading
    )
    val uiState: StateFlow<FriendSelectorUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        observeFriends()
        refresh()
    }

    /**
     * The screen state is derived purely from the repository state and the in-flight
     * flag. Writing `Loading` directly would strand the UI whenever a refresh ends on
     * the value the repository already held, because its flow conflates equal states.
     */
    private fun observeFriends() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            combine(
                repository.observeFriendList(),
                repository.observeCurrentUser(),
                refreshing
            ) { state, user, isRefreshing ->
                toUiState(state, user, isRefreshing)
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            try {
                repository.refreshFriendList()
            } finally {
                refreshing.value = false
            }
        }
    }

    private suspend fun toUiState(
        state: FriendListState,
        currentUser: UserSummary?,
        isRefreshing: Boolean
    ): FriendSelectorUiState {
        val friends = (state as? FriendListState.Content)?.friends
        // Keep an existing list on screen while reloading; otherwise show progress.
        if (isRefreshing && friends.isNullOrEmpty()) {
            return FriendSelectorUiState.Loading
        }

        return when (state) {
            FriendListState.Loading -> FriendSelectorUiState.Loading
            FriendListState.Disabled -> FriendSelectorUiState.Disabled
            is FriendListState.Content -> {
                if (state.friends.isEmpty()) {
                    FriendSelectorUiState.Empty
                } else {
                    val friendsByIsu = state.friends.associateBy(UserSummary::isu)
                    val recentFriends = history.getRecentIsu()
                        .mapNotNull(friendsByIsu::get)
                    FriendSelectorUiState.Content(
                        friends = state.friends,
                        recentFriends = recentFriends,
                        currentUser = currentUser
                    )
                }
            }
            is FriendListState.Error -> FriendSelectorUiState.Error(state.error)
        }
    }

    fun recordSelection(isu: Int) {
        viewModelScope.launch {
            history.record(isu)
        }
    }
}
