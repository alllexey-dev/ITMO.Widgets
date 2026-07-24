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
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class FriendSelectorUiState {
    data object Loading : FriendSelectorUiState()
    data object Disabled : FriendSelectorUiState()
    data object Empty : FriendSelectorUiState()
    data class Content(
        val friends: List<UserSummary>,
        val recentFriends: List<UserSummary>
    ) : FriendSelectorUiState()
    data class Error(val error: AppError) : FriendSelectorUiState()
}

@HiltViewModel
class FriendSelectorViewModel @Inject constructor(
    private val repository: FriendRepository,
    private val history: FriendSelectionHistory
) : ViewModel() {

    private val _uiState = MutableStateFlow<FriendSelectorUiState>(
        FriendSelectorUiState.Loading
    )
    val uiState: StateFlow<FriendSelectorUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        observeFriends()
        refresh()
    }

    private fun observeFriends() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repository.observeFriendList().collect { state ->
                _uiState.value = when (state) {
                    FriendListState.Loading -> FriendSelectorUiState.Loading
                    FriendListState.Disabled -> FriendSelectorUiState.Disabled
                    is FriendListState.Content -> {
                        val friends = state.friends
                        if (friends.isEmpty()) {
                            FriendSelectorUiState.Empty
                        } else {
                            val friendsByIsu = friends.associateBy(UserSummary::isu)
                            val recentFriends = history.getRecentIsu()
                                .mapNotNull(friendsByIsu::get)
                            FriendSelectorUiState.Content(friends, recentFriends)
                        }
                    }
                    is FriendListState.Error -> FriendSelectorUiState.Error(state.error)
                }
            }
        }
    }

    fun refresh() {
        _uiState.value = FriendSelectorUiState.Loading
        viewModelScope.launch {
            repository.refreshFriendList()
        }
    }

    fun recordSelection(isu: Int) {
        viewModelScope.launch {
            history.record(isu)
        }
    }
}
