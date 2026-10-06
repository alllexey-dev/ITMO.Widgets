package dev.alllexey.itmowidgets.feature.social.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.presentation.BusyKeys
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.presentation.RefreshTracker
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.social.FriendRequests
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.feature.social.Res
import dev.alllexey.itmowidgets.shared.feature.social.friends_section_incoming
import dev.alllexey.itmowidgets.shared.feature.social.friends_section_outgoing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FriendsViewModel(
    private val repository: SocialRepository
) : ViewModel() {

    private val tab = MutableStateFlow(FriendsTab.FRIENDS)
    private val refreshes = RefreshTracker(viewModelScope)
    private val busyRows = BusyKeys<Int>(viewModelScope)
    private val eventQueue = EventQueue<FriendsEvent>()

    val uiState: StateFlow<FriendsUiState> = combine(
        repository.observeFriends(),
        repository.observeRequests(),
        tab,
        refreshes.refreshing,
        busyRows.busy,
        ::toUiState
    ).stateIn(viewModelScope, SharingStarted.Eagerly, FriendsUiState.Loading)

    val events: Flow<FriendsEvent> = eventQueue.events

    init {
        refresh(RefreshMode.Silent)
    }

    fun selectTab(selected: FriendsTab) {
        tab.value = selected
    }

    /** A pull or a retry shows the indicator; the refresh on entry stays silent behind the list. */
    fun refresh(mode: RefreshMode) {
        refreshes.launch(mode) { repository.refresh() }
    }

    fun onAction(row: UserRowUi, action: UserAction) {
        when (action) {
            UserAction.ACCEPT -> act(row.isu) { repository.acceptRequest(row.isu) }
            UserAction.REJECT -> act(row.isu) { repository.rejectRequest(row.isu) }
            UserAction.CANCEL -> act(row.isu) { repository.cancelRequest(row.isu) }
            UserAction.REMOVE -> viewModelScope.launch {
                eventQueue.send(FriendsEvent.ConfirmRemove(row.isu, row.displayName))
            }
            UserAction.ADD, UserAction.INVITE -> Unit
        }
    }

    fun removeFriend(isu: Int) {
        act(isu) { repository.removeFriend(isu) }
    }

    private fun act(isu: Int, action: suspend () -> AppResult<UserProfile>) {
        busyRows.launch(isu) {
            val result = action()
            if (result is AppResult.Failure) eventQueue.send(FriendsEvent.ActionFailed(result.error))
        }
    }

    private fun toUiState(
        friends: LoadState<List<UserProfile>>,
        requests: LoadState<FriendRequests>,
        tab: FriendsTab,
        refreshing: Boolean,
        busy: Set<Int>
    ): FriendsUiState {
        if (friends == LoadState.Disabled || requests == LoadState.Disabled) return FriendsUiState.Disabled
        val friendList = (friends as? LoadState.Content)?.value
        val requestList = (requests as? LoadState.Content)?.value
        if (friendList == null || requestList == null) {
            val error = (friends as? LoadState.Error)?.error ?: (requests as? LoadState.Error)?.error
            // Keep showing progress while a refresh may still replace an error.
            return if (error != null && !refreshing) FriendsUiState.Error(error) else FriendsUiState.Loading
        }
        val items = when (tab) {
            FriendsTab.FRIENDS -> friendList.map { profile ->
                UserListItem.User(profile.toRow(busy, primary = null, secondary = UserAction.REMOVE))
            }
            FriendsTab.REQUESTS -> buildList<UserListItem> {
                if (requestList.incoming.isNotEmpty()) {
                    add(UserListItem.Header(UiText.Res(Res.string.friends_section_incoming)))
                    requestList.incoming.forEach {
                        add(UserListItem.User(it.toRow(busy, primary = UserAction.ACCEPT, secondary = UserAction.REJECT)))
                    }
                }
                if (requestList.outgoing.isNotEmpty()) {
                    add(UserListItem.Header(UiText.Res(Res.string.friends_section_outgoing)))
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
            refreshing = refreshing,
            empty = when {
                items.isNotEmpty() -> null
                tab == FriendsTab.FRIENDS -> FriendsEmpty.NO_FRIENDS
                else -> FriendsEmpty.NO_REQUESTS
            }
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
