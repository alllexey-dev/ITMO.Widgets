package dev.alllexey.itmowidgets.feature.social.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.presentation.BusyKeys
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.presentation.RefreshTracker
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.social.FriendRequests
import dev.alllexey.itmowidgets.core.social.PeopleSearchRepository
import dev.alllexey.itmowidgets.core.social.PersonSearchResult
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.feature.social.SocialStrings
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Друзья: the viewer's requests and friends from [social], and people search through [search] in the same screen.
 * The lists refresh once on creation; every action folds its answer into the repository, which the lists follow,
 * and a search row takes the answered profile in place. A query searches 300 ms after the last keystroke; clearing it
 * returns to the lists at once.
 */
@OptIn(FlowPreview::class)
class FriendsViewModel(
    private val social: SocialRepository,
    private val search: PeopleSearchRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val phase = MutableStateFlow<SearchPhase>(SearchPhase.Idle)
    private val results = MutableStateFlow<List<PersonSearchResult>>(emptyList())
    private val nextOffset = MutableStateFlow<Int?>(null)
    private val refreshes = RefreshTracker(viewModelScope)
    /** The next page of the search; its `refreshing` is the list footer. */
    private val pages = RefreshTracker(viewModelScope)
    private val busyRows = BusyKeys<Int>(viewModelScope)
    private val eventQueue = EventQueue<FriendsEvent>()

    private val lists = combine(
        social.observeFriends(),
        social.observeRequests(),
        refreshes.refreshing,
        busyRows.busy,
        ::toLists,
    )
    private val searchState = combine(phase, results, nextOffset, busyRows.busy, pages.refreshing, ::toSearch)

    val uiState: StateFlow<FriendsUiState> = combine(query, lists, searchState, social.observeCurrentUser()) { text, lists, found, viewer ->
        FriendsUiState(query = text, lists = lists, search = found, viewer = viewer)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FriendsUiState())

    val events: Flow<FriendsEvent> = eventQueue.events

    // A new query replaces the search and the page in flight, which a refresh would join instead.
    private var searchJob: Job? = null
    private var pageJob: Job? = null

    init {
        refresh(RefreshMode.Silent)
        viewModelScope.launch {
            query.map { it.trim() }
                .debounce(DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { normalized -> startSearch(normalized) }
        }
    }

    /** A pull or a retry shows the indicator; the refresh on entry stays silent behind the list. */
    fun refresh(mode: RefreshMode) {
        refreshes.launch(mode) { social.refresh() }
    }

    fun onQueryChanged(text: String) {
        query.value = text
        if (text.isBlank()) {
            cancelSearch()
            results.value = emptyList()
            nextOffset.value = null
            phase.value = SearchPhase.Idle
        }
    }

    /** Searches the current query again, the retry of a failed search. */
    fun retrySearch() {
        startSearch(query.value.trim())
    }

    fun loadMore() {
        val offset = nextOffset.value ?: return
        val current = query.value.trim()
        pageJob = pages.launch(RefreshMode.Pull) {
            when (val page = search.search(current, offset)) {
                is AppResult.Success -> {
                    results.update { it + page.value.results }
                    nextOffset.value = page.value.nextOffset
                }
                is AppResult.Failure -> eventQueue.send(FriendsEvent.ActionFailed(page.error))
            }
        }
    }

    fun onAction(row: UserRowUi, action: UserAction) {
        when (action) {
            UserAction.ADD -> act(row.isu) { social.sendRequest(row.isu) }
            UserAction.ACCEPT -> act(row.isu) { social.acceptRequest(row.isu) }
            UserAction.REJECT -> act(row.isu) { social.rejectRequest(row.isu) }
            UserAction.CANCEL -> act(row.isu) { social.cancelRequest(row.isu) }
            UserAction.INVITE -> viewModelScope.launch { eventQueue.send(FriendsEvent.Invite(row.name)) }
            // Removing a friend lives in their card.
            UserAction.REMOVE -> Unit
        }
    }

    private fun act(isu: Int, action: suspend () -> AppResult<UserProfile>) {
        busyRows.launch(isu) {
            when (val result = action()) {
                is AppResult.Success -> results.update { people ->
                    people.map { person -> if (person.isu == isu) person.copy(registered = result.value) else person }
                }
                is AppResult.Failure -> eventQueue.send(FriendsEvent.ActionFailed(result.error))
            }
        }
    }

    private fun startSearch(normalized: String) {
        cancelSearch()
        if (normalized.isEmpty()) {
            phase.value = SearchPhase.Idle
            return
        }
        searchJob = viewModelScope.launch {
            phase.value = SearchPhase.Searching
            when (val page = search.search(normalized)) {
                is AppResult.Success -> {
                    results.value = page.value.results
                    nextOffset.value = page.value.nextOffset
                    phase.value = SearchPhase.Done
                }
                is AppResult.Failure -> phase.value = SearchPhase.Failed(page.error)
            }
        }
    }

    private fun cancelSearch() {
        searchJob?.cancel()
        pageJob?.cancel()
    }

    private fun toLists(
        friends: LoadState<List<UserProfile>>,
        requests: LoadState<FriendRequests>,
        refreshing: Boolean,
        busy: Set<Int>,
    ): FriendLists {
        if (friends == LoadState.Disabled || requests == LoadState.Disabled) return FriendLists.Disabled
        val friendList = (friends as? LoadState.Content)?.value
        val requestList = (requests as? LoadState.Content)?.value
        if (friendList == null || requestList == null) {
            val error = (friends as? LoadState.Error)?.error ?: (requests as? LoadState.Error)?.error
            // Keep showing progress while a refresh may still replace an error.
            return if (error != null && !refreshing) FriendLists.Error(error) else FriendLists.Loading
        }
        return FriendLists.Content(
            incoming = requestList.incoming.map {
                it.toRow(busy, UserAction.ACCEPT, UserAction.REJECT, UiText.Res(SocialStrings.user_status_incoming))
            },
            outgoing = requestList.outgoing.map {
                it.toRow(busy, null, UserAction.CANCEL, UiText.Res(SocialStrings.user_status_outgoing))
            },
            friends = friendList.map { it.toRow(busy, null, null, null) },
            refreshing = refreshing,
        )
    }

    private fun toSearch(
        phase: SearchPhase,
        people: List<PersonSearchResult>,
        nextOffset: Int?,
        busy: Set<Int>,
        loadingMore: Boolean,
    ): PeopleSearchResults? = when (phase) {
        SearchPhase.Idle -> null
        SearchPhase.Searching -> PeopleSearchResults.Loading
        is SearchPhase.Failed -> PeopleSearchResults.Error(phase.error)
        SearchPhase.Done -> if (people.isEmpty()) PeopleSearchResults.Empty else PeopleSearchResults.Content(
            registered = people.filter { it.registered != null }.map { it.toRow(busy) },
            others = people.filter { it.registered == null }.map { it.toRow(busy) },
            offersMore = nextOffset != null && !loadingMore,
            loadingMore = loadingMore,
        )
    }

    private fun UserProfile.toRow(busy: Set<Int>, primary: UserAction?, secondary: UserAction?, status: UiText?) =
        UserRowUi(
            isu = isu,
            name = user.name,
            pictureUrl = user.pictureUrl,
            subtitle = user.subtitleText(),
            status = status,
            primary = primary,
            secondary = secondary,
            busy = isu in busy,
            familyName = user.familyName,
            givenName = user.givenName,
        )

    private fun PersonSearchResult.toRow(busy: Set<Int>): UserRowUi {
        val profile = registered
        return if (profile != null) {
            UserRowUi(
                isu = isu,
                name = profile.user.name,
                pictureUrl = profile.user.pictureUrl ?: pictureUrl,
                subtitle = profile.user.subtitleText(),
                status = profile.relationship.statusText(),
                primary = profile.relationship.primaryAction(),
                busy = isu in busy,
                familyName = profile.user.familyName,
                givenName = profile.user.givenName,
            )
        } else {
            UserRowUi(
                isu = isu,
                name = name,
                pictureUrl = pictureUrl,
                subtitle = UiText.Res(SocialStrings.user_subtitle_isu, listOf(isu)),
                status = UiText.Res(SocialStrings.user_status_not_registered),
                primary = UserAction.INVITE,
            )
        }
    }

    /** Where the first page of the current query is; rows and later pages live beside it. */
    private sealed interface SearchPhase {
        data object Idle : SearchPhase
        data object Searching : SearchPhase
        data object Done : SearchPhase
        data class Failed(val error: AppError) : SearchPhase
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
    }
}
