package dev.alllexey.itmowidgets.feature.social.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.social.PeopleSearchRepository
import dev.alllexey.itmowidgets.core.social.PersonSearchResult
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.core.text.UiText
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface UserSearchUiState {
    /** Nothing typed yet. */
    data object Idle : UserSearchUiState
    data object Loading : UserSearchUiState
    data object Empty : UserSearchUiState
    data class Error(val error: AppError) : UserSearchUiState
    data class Content(
        val items: List<UserListItem>,
        val loadingMore: Boolean
    ) : UserSearchUiState
}

sealed interface UserSearchEvent {
    data class ActionFailed(val error: AppError) : UserSearchEvent
    data class Invite(val name: String) : UserSearchEvent
}

@OptIn(FlowPreview::class)
@HiltViewModel
class UserSearchViewModel @Inject constructor(
    private val search: PeopleSearchRepository,
    private val social: SocialRepository
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val results = MutableStateFlow<List<PersonSearchResult>>(emptyList())
    private val nextOffset = MutableStateFlow<Int?>(null)
    private val busy = MutableStateFlow<Set<Int>>(emptySet())
    private val events = Channel<UserSearchEvent>(Channel.BUFFERED)

    private val _uiState = MutableStateFlow<UserSearchUiState>(UserSearchUiState.Idle)
    val uiState: StateFlow<UserSearchUiState> = _uiState.asStateFlow()

    val eventFlow: Flow<UserSearchEvent> = events.receiveAsFlow()

    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null

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
            searchJob?.cancel()
            loadMoreJob?.cancel()
            results.value = emptyList()
            nextOffset.value = null
            _uiState.value = UserSearchUiState.Idle
        }
    }

    fun loadMore() {
        val offset = nextOffset.value ?: return
        if (loadMoreJob?.isActive == true) return
        val current = query.value.trim()
        loadMoreJob = viewModelScope.launch {
            publish(loadingMore = true)
            when (val page = search.search(current, offset)) {
                is AppResult.Success -> {
                    results.value = results.value + page.value.results
                    nextOffset.value = page.value.nextOffset
                    publish(loadingMore = false)
                }
                is AppResult.Failure -> {
                    events.send(UserSearchEvent.ActionFailed(page.error))
                    publish(loadingMore = false)
                }
            }
        }
    }

    fun retry() {
        startSearch(query.value.trim())
    }

    fun onAction(row: UserRowUi, action: UserAction) {
        when (action) {
            UserAction.ADD -> act(row.isu) { social.sendRequest(row.isu) }
            UserAction.ACCEPT -> act(row.isu) { social.acceptRequest(row.isu) }
            UserAction.CANCEL -> act(row.isu) { social.cancelRequest(row.isu) }
            UserAction.INVITE -> viewModelScope.launch { events.send(UserSearchEvent.Invite(row.name)) }
            UserAction.REJECT, UserAction.REMOVE -> Unit
        }
    }

    private fun startSearch(normalized: String) {
        searchJob?.cancel()
        loadMoreJob?.cancel()
        if (normalized.isEmpty()) {
            _uiState.value = UserSearchUiState.Idle
            return
        }
        searchJob = viewModelScope.launch {
            _uiState.value = UserSearchUiState.Loading
            when (val page = search.search(normalized)) {
                is AppResult.Success -> {
                    results.value = page.value.results
                    nextOffset.value = page.value.nextOffset
                    publish(loadingMore = false)
                }
                is AppResult.Failure -> _uiState.value = UserSearchUiState.Error(page.error)
            }
        }
    }

    private fun act(isu: Int, action: suspend () -> AppResult<UserProfile>) {
        if (isu in busy.value) return
        busy.value = busy.value + isu
        publish(loadingMore = loadingMore())
        viewModelScope.launch {
            try {
                when (val result = action()) {
                    is AppResult.Success -> results.value = results.value.map { person ->
                        if (person.isu == isu) person.copy(registered = result.value) else person
                    }
                    is AppResult.Failure -> events.send(UserSearchEvent.ActionFailed(result.error))
                }
            } finally {
                busy.value = busy.value - isu
                if (_uiState.value is UserSearchUiState.Content) publish(loadingMore = loadingMore())
            }
        }
    }

    private fun loadingMore() = (_uiState.value as? UserSearchUiState.Content)?.loadingMore == true

    private fun publish(loadingMore: Boolean) {
        val people = results.value
        if (people.isEmpty()) {
            _uiState.value = UserSearchUiState.Empty
            return
        }
        val registered = people.filter { it.registered != null }
        val others = people.filter { it.registered == null }
        val items = buildList {
            if (registered.isNotEmpty()) {
                add(UserListItem.Header(UiText.Resource(R.string.user_search_section_registered)))
                registered.forEach { add(UserListItem.User(it.toRow())) }
            }
            if (others.isNotEmpty()) {
                add(UserListItem.Header(UiText.Resource(R.string.user_search_section_others)))
                others.forEach { add(UserListItem.User(it.toRow())) }
            }
            if (nextOffset.value != null && !loadingMore) add(UserListItem.LoadMore)
        }
        _uiState.value = UserSearchUiState.Content(items, loadingMore)
    }

    private fun PersonSearchResult.toRow(): UserRowUi {
        val profile = registered
        return if (profile != null) {
            UserRowUi(
                isu = isu,
                name = profile.user.name,
                pictureUrl = profile.user.pictureUrl ?: pictureUrl,
                subtitle = profile.user.subtitleText(),
                status = profile.relationship.statusText(),
                primary = profile.relationship.primaryAction(),
                busy = isu in busy.value,
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
                opensProfile = false
            )
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
    }
}
