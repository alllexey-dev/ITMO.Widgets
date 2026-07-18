package dev.alllexey.itmowidgets.feature.friendselector

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.model.UserData
import dev.alllexey.itmowidgets.core.storage.FriendSelectionHistory
import dev.alllexey.itmowidgets.core.util.fold
import dev.alllexey.itmowidgets.domain.repository.FriendRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class FriendSelectorUiState {
    data object Loading : FriendSelectorUiState()
    data object Disabled : FriendSelectorUiState()
    data class Success(
        val friends: List<UserData>,
        val recentFriends: List<UserData>
    ) : FriendSelectorUiState()
    data class Error(val message: String) : FriendSelectorUiState()
}

@HiltViewModel
class FriendSelectorViewModel @Inject constructor(
    private val repository: FriendRepository,
    private val history: FriendSelectionHistory
) : ViewModel() {

    private val _uiState = MutableLiveData<FriendSelectorUiState>(FriendSelectorUiState.Loading)
    val uiState: LiveData<FriendSelectorUiState> = _uiState

    private var observeJob: Job? = null

    init {
        observeFriends()
        refresh()
    }

    private fun observeFriends() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repository.observeFriendList().collect { state ->
                _uiState.value = state.fold(
                    onDisabled = { FriendSelectorUiState.Disabled },
                    onSuccess = { friends ->
                        val friendsByIsu = friends.associateBy(UserData::isu)
                        val recentFriends = history.getRecentIsu().mapNotNull(friendsByIsu::get)
                        FriendSelectorUiState.Success(friends, recentFriends)
                    },
                    onError = { FriendSelectorUiState.Error(it.message ?: "Произошла ошибка") }
                )
            }
        }
    }

    fun refresh() {
        _uiState.value = FriendSelectorUiState.Loading
        viewModelScope.launch {
            try {
                repository.refreshFriendList()
            } catch (e: Exception) {
                _uiState.value = FriendSelectorUiState.Error(
                    e.message ?: "Failed to load friends"
                )
            }
        }
    }

    fun recordSelection(isu: Int) {
        history.record(isu)
    }
}
