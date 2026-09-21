package dev.alllexey.itmowidgets.feature.social.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.social.FriendRequests
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.core.social.SocialState
import dev.alllexey.itmowidgets.core.text.UiText
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

enum class FriendsTab { FRIENDS, REQUESTS }

sealed interface FriendsUiState {
    data object Loading : FriendsUiState
    data object Disabled : FriendsUiState
    data class Error(val error: AppError) : FriendsUiState
    data class Content(
        val tab: FriendsTab,
        val items: List<UserListItem>,
        val incomingCount: Int,
        val refreshing: Boolean
    ) : FriendsUiState
}

sealed interface FriendsEvent {
    data class ActionFailed(val error: AppError) : FriendsEvent
    data class ConfirmRemove(val isu: Int, val name: String) : FriendsEvent
}

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val repository: SocialRepository
) : ViewModel() {

    private val tab = MutableStateFlow(FriendsTab.FRIENDS)
    private val refreshing = MutableStateFlow(false)
    private var inFlight = false
    private val busy = MutableStateFlow<Set<Int>>(emptySet())
    private val events = Channel<FriendsEvent>(Channel.BUFFERED)

    val uiState: StateFlow<FriendsUiState> = combine(
        repository.observeFriends(),
        repository.observeRequests(),
        tab,
        refreshing,
        busy
    ) { friends, requests, tab, refreshing, busy ->
        toUiState(friends, requests, tab, refreshing, busy)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FriendsUiState.Loading)

    val eventFlow: Flow<FriendsEvent> = events.receiveAsFlow()

    init {
        refresh(silent = true)
    }

    fun selectTab(selected: FriendsTab) {
        tab.value = selected
    }

    /** A pull shows the indicator; the automatic refresh on entry stays silent behind the list. */
    fun refresh(silent: Boolean = false) {
        if (inFlight) return
        inFlight = true
        if (!silent) refreshing.value = true
        viewModelScope.launch {
            try {
                repository.refresh()
            } finally {
                inFlight = false
                refreshing.value = false
            }
        }
    }

    fun onAction(row: UserRowUi, action: UserAction) {
        when (action) {
            UserAction.ACCEPT -> act(row.isu) { repository.acceptRequest(row.isu) }
            UserAction.REJECT -> act(row.isu) { repository.rejectRequest(row.isu) }
            UserAction.CANCEL -> act(row.isu) { repository.cancelRequest(row.isu) }
            UserAction.REMOVE -> viewModelScope.launch {
                events.send(FriendsEvent.ConfirmRemove(row.isu, row.name))
            }
            UserAction.ADD, UserAction.INVITE -> Unit
        }
    }

    fun removeFriend(isu: Int) = act(isu) { repository.removeFriend(isu) }

    private fun act(isu: Int, action: suspend () -> AppResult<UserProfile>) {
        if (isu in busy.value) return
        busy.value = busy.value + isu
        viewModelScope.launch {
            try {
                val result = action()
                if (result is AppResult.Failure) events.send(FriendsEvent.ActionFailed(result.error))
            } finally {
                busy.value = busy.value - isu
            }
        }
    }

    private fun toUiState(
        friends: SocialState<List<UserProfile>>,
        requests: SocialState<FriendRequests>,
        tab: FriendsTab,
        refreshing: Boolean,
        busy: Set<Int>
    ): FriendsUiState {
        if (friends == SocialState.Disabled || requests == SocialState.Disabled) return FriendsUiState.Disabled
        val friendList = (friends as? SocialState.Content)?.value
        val requestList = (requests as? SocialState.Content)?.value
        if (friendList == null || requestList == null) {
            val error = (friends as? SocialState.Error)?.error ?: (requests as? SocialState.Error)?.error
            // Keep showing progress while a refresh may still replace an error.
            return if (error != null && !refreshing) FriendsUiState.Error(error) else FriendsUiState.Loading
        }
        val items = when (tab) {
            FriendsTab.FRIENDS -> friendList.map { profile ->
                UserListItem.User(profile.toRow(busy, primary = null, secondary = UserAction.REMOVE))
            }
            FriendsTab.REQUESTS -> buildList<UserListItem> {
                if (requestList.incoming.isNotEmpty()) {
                    add(UserListItem.Header(UiText.Resource(R.string.friends_section_incoming)))
                    requestList.incoming.forEach {
                        add(UserListItem.User(it.toRow(busy, primary = UserAction.ACCEPT, secondary = UserAction.REJECT)))
                    }
                }
                if (requestList.outgoing.isNotEmpty()) {
                    add(UserListItem.Header(UiText.Resource(R.string.friends_section_outgoing)))
                    requestList.outgoing.forEach {
                        add(UserListItem.User(it.toRow(busy, primary = null, secondary = UserAction.CANCEL)))
                    }
                }
            }
        }
        return FriendsUiState.Content(
            tab = tab,
            items = items,
            incomingCount = requestList.incoming.size,
            refreshing = refreshing
        )
    }

    private fun UserProfile.toRow(busy: Set<Int>, primary: UserAction?, secondary: UserAction?) = UserRowUi(
        isu = isu,
        name = user.name,
        pictureUrl = user.pictureUrl,
        subtitle = user.subtitleText(),
        status = null,
        primary = primary,
        secondary = secondary,
        busy = isu in busy
    )
}
