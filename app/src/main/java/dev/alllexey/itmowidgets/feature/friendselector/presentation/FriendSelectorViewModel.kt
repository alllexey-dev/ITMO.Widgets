package dev.alllexey.itmowidgets.feature.friendselector.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.friend.FriendListState
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.social.PeopleSearchRepository
import dev.alllexey.itmowidgets.feature.friendselector.domain.FriendSelectionHistory
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Whose schedules the list offers: mutual friends, or every ITMO.Widgets user found by name. */
enum class FriendSelectorScope { FRIENDS, ALL }

/** Registered people found by name; only these can have a viewable schedule. */
sealed interface PeopleResults {
    data object Idle : PeopleResults
    data object Loading : PeopleResults
    data class Content(val people: List<UserSummary>) : PeopleResults
    data class Error(val error: AppError) : PeopleResults
}

sealed class FriendSelectorUiState {
    data object Loading : FriendSelectorUiState()
    data object Disabled : FriendSelectorUiState()
    data object Empty : FriendSelectorUiState()
    data class Content(
        val friends: List<UserSummary>,
        val recentFriends: List<UserSummary>,
        val currentUser: UserSummary? = null,
        val scope: FriendSelectorScope = FriendSelectorScope.FRIENDS,
        val people: PeopleResults = PeopleResults.Idle
    ) : FriendSelectorUiState()
    data class Error(val error: AppError) : FriendSelectorUiState()
}

@OptIn(FlowPreview::class)
@HiltViewModel
class FriendSelectorViewModel @Inject constructor(
    private val repository: FriendRepository,
    private val history: FriendSelectionHistory,
    private val peopleSearch: PeopleSearchRepository
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val scope = MutableStateFlow(FriendSelectorScope.FRIENDS)
    private val query = MutableStateFlow("")
    private val people = MutableStateFlow<PeopleResults>(PeopleResults.Idle)

    private val _uiState = MutableStateFlow<FriendSelectorUiState>(
        FriendSelectorUiState.Loading
    )
    val uiState: StateFlow<FriendSelectorUiState> = _uiState.asStateFlow()

    /** The own-schedule chip needs the avatar even when the friend list is empty or failed. */
    val currentUser: StateFlow<UserSummary?> = repository.observeCurrentUser()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var observeJob: Job? = null
    private var searchJob: Job? = null

    init {
        observeFriends()
        observeQuery()
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
                refreshing,
                scope,
                people
            ) { state, user, isRefreshing, scope, people ->
                toUiState(state, user, isRefreshing, scope, people)
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    /** People search only runs in the wide scope; the friends scope filters locally. */
    private fun observeQuery() {
        viewModelScope.launch {
            combine(scope, query.map { it.trim() }) { scope, query -> scope to query }
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { (scope, query) -> if (scope == FriendSelectorScope.ALL) search(query) }
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

    fun selectScope(selected: FriendSelectorScope) {
        if (scope.value == selected) return
        scope.value = selected
        if (selected == FriendSelectorScope.FRIENDS) {
            searchJob?.cancel()
            people.value = PeopleResults.Idle
        }
    }

    fun onQueryChanged(text: String) {
        query.value = text
        if (text.isBlank() && scope.value == FriendSelectorScope.ALL) {
            searchJob?.cancel()
            people.value = PeopleResults.Idle
        }
    }

    fun retrySearch() {
        search(query.value.trim())
    }

    private fun search(normalized: String) {
        searchJob?.cancel()
        if (normalized.isEmpty()) {
            people.value = PeopleResults.Idle
            return
        }
        searchJob = viewModelScope.launch {
            people.value = PeopleResults.Loading
            people.value = when (val page = peopleSearch.search(normalized)) {
                is AppResult.Success -> PeopleResults.Content(
                    page.value.results.mapNotNull { it.registered?.user }
                )
                is AppResult.Failure -> PeopleResults.Error(page.error)
            }
        }
    }

    private suspend fun toUiState(
        state: FriendListState,
        currentUser: UserSummary?,
        isRefreshing: Boolean,
        scope: FriendSelectorScope,
        people: PeopleResults
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
                if (state.friends.isEmpty() && scope == FriendSelectorScope.FRIENDS) {
                    FriendSelectorUiState.Empty
                } else {
                    val friendsByIsu = state.friends.associateBy(UserSummary::isu)
                    val recentFriends = history.getRecentIsu()
                        .mapNotNull(friendsByIsu::get)
                    FriendSelectorUiState.Content(
                        friends = state.friends,
                        recentFriends = recentFriends,
                        currentUser = currentUser,
                        scope = scope,
                        people = people
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

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
