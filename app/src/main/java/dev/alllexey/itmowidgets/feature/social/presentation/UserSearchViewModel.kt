package dev.alllexey.itmowidgets.feature.social.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.presentation.BusyKeys
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.presentation.RefreshTracker
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.social.PeopleSearchRepository
import dev.alllexey.itmowidgets.core.social.PersonSearchResult
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.core.text.UiText
import javax.inject.Inject
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

@OptIn(FlowPreview::class)
@HiltViewModel
class UserSearchViewModel @Inject constructor(
    private val search: PeopleSearchRepository,
    private val social: SocialRepository
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val phase = MutableStateFlow<SearchPhase>(SearchPhase.Idle)
    private val results = MutableStateFlow<List<PersonSearchResult>>(emptyList())
    private val nextOffset = MutableStateFlow<Int?>(null)
    /** The next page; its `refreshing` is the list footer and never the first page's progress. */
    private val pages = RefreshTracker(viewModelScope)
    private val busyRows = BusyKeys<Int>(viewModelScope)
    private val eventQueue = EventQueue<UserSearchEvent>()

    val uiState: StateFlow<UserSearchUiState> = combine(
        phase,
        results,
        nextOffset,
        busyRows.busy,
        pages.refreshing,
        ::toUiState
    ).stateIn(viewModelScope, SharingStarted.Eagerly, UserSearchUiState.Idle)

    val events: Flow<UserSearchEvent> = eventQueue.events

    // A new query replaces the search and the page in flight, which a refresh would join instead.
    private var searchJob: Job? = null
    private var pageJob: Job? = null

    init {
        viewModelScope.launch {
            query.map { it.trim() }
                .debounce(DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { normalized -> startSearch(normalized) }
        }
    }

    fun onQueryChanged(text: String) {
        query.value = text
        if (text.isBlank()) {
            // Clearing the field resets immediately instead of after the debounce.
            cancelSearch()
            results.value = emptyList()
            nextOffset.value = null
            phase.value = SearchPhase.Idle
        }
    }

    /** Searches the current query again; every mode does the same, the screen only offers it as a retry. */
    fun refresh(mode: RefreshMode) {
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
                is AppResult.Failure -> eventQueue.send(UserSearchEvent.ActionFailed(page.error))
            }
        }
    }

    fun onAction(row: UserRowUi, action: UserAction) {
        when (action) {
            UserAction.ADD -> act(row.isu) { social.sendRequest(row.isu) }
            UserAction.ACCEPT -> act(row.isu) { social.acceptRequest(row.isu) }
            UserAction.CANCEL -> act(row.isu) { social.cancelRequest(row.isu) }
            UserAction.INVITE -> viewModelScope.launch { eventQueue.send(UserSearchEvent.Invite(row.name)) }
            UserAction.REJECT, UserAction.REMOVE -> Unit
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

    private fun act(isu: Int, action: suspend () -> AppResult<UserProfile>) {
        busyRows.launch(isu) {
            when (val result = action()) {
                is AppResult.Success -> results.update { people ->
                    people.map { person -> if (person.isu == isu) person.copy(registered = result.value) else person }
                }
                is AppResult.Failure -> eventQueue.send(UserSearchEvent.ActionFailed(result.error))
            }
        }
    }

    private fun toUiState(
        phase: SearchPhase,
        people: List<PersonSearchResult>,
        nextOffset: Int?,
        busy: Set<Int>,
        loadingMore: Boolean
    ): UserSearchUiState = when (phase) {
        SearchPhase.Idle -> UserSearchUiState.Idle
        SearchPhase.Searching -> UserSearchUiState.Loading
        is SearchPhase.Failed -> UserSearchUiState.Error(phase.error)
        SearchPhase.Done -> if (people.isEmpty()) UserSearchUiState.Empty else {
            UserSearchUiState.Content(items(people, nextOffset != null && !loadingMore, busy), loadingMore)
        }
    }

    private fun items(people: List<PersonSearchResult>, offersMore: Boolean, busy: Set<Int>): List<UserListItem> {
        val registered = people.filter { it.registered != null }
        val others = people.filter { it.registered == null }
        return buildList {
            if (registered.isNotEmpty()) {
                add(UserListItem.Header(UiText.Resource(R.string.user_search_section_registered)))
                registered.forEach { add(UserListItem.User(it.toRow(busy))) }
            }
            if (others.isNotEmpty()) {
                add(UserListItem.Header(UiText.Resource(R.string.user_search_section_others)))
                others.forEach { add(UserListItem.User(it.toRow(busy))) }
            }
            if (offersMore) add(UserListItem.LoadMore)
        }
    }

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
                opensProfile = true
            )
        } else {
            UserRowUi(
                isu = isu,
                name = name,
                pictureUrl = pictureUrl,
                subtitle = UiText.Resource(R.string.user_subtitle_isu, listOf(isu)),
                status = UiText.Resource(R.string.user_status_not_registered),
                primary = UserAction.INVITE,
                busy = false,
                opensProfile = true
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
