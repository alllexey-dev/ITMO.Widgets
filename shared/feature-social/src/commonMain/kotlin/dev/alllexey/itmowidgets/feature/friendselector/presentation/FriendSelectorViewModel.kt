package dev.alllexey.itmowidgets.feature.friendselector.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.navigation.FriendSelectionContract
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.presentation.RefreshTracker
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.result.valueOrNull
import dev.alllexey.itmowidgets.core.social.PeopleSearchRepository
import dev.alllexey.itmowidgets.feature.friendselector.domain.FriendSelectionHistory
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
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class FriendSelectorViewModel(
    private val repository: FriendRepository,
    private val history: FriendSelectionHistory,
    private val peopleSearch: PeopleSearchRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {

    private val refreshes = RefreshTracker(viewModelScope)
    private val eventQueue = EventQueue<FriendSelectorEvent>()
    private val scope = MutableStateFlow(FriendSelectorScope.FRIENDS)
    private val query = MutableStateFlow("")
    private val people = MutableStateFlow<PeopleResults>(PeopleResults.Idle)
    private val choice = MutableStateFlow<Choice>(Choice.Opening)

    /** A restored pending choice wins over the ISU the sheet was opened with. */
    private val openingIsu: Int? = (
        savedState.get<Int>(KEY_PENDING_ISU) ?: savedState.get<Int>(FriendSelectionContract.ARG_SELECTED_ISU)
        )?.takeIf { it != FriendSelectionContract.NO_USER_ISU }
    private val recentOrder = RecentFriendOrder(savedState.get<IntArray>(KEY_RECENT_ORDER)?.toList())
    private var lastRecent: List<UserSummary> = emptyList()
    private var applied = false
    private var searchJob: Job? = null

    /**
     * Derived from the repository flow and the refresh indicator, never written as a transient `Loading`: the
     * repository conflates equal states, so a refresh ending on the value it already held emits nothing.
     */
    val uiState: StateFlow<FriendSelectorUiState> = combine(
        repository.observeFriendList(),
        repository.observeCurrentUser(),
        refreshes.refreshing,
        inputs()
    ) { friends, currentUser, refreshing, inputs -> toUiState(friends, currentUser, refreshing, inputs) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FriendSelectorUiState(selectedIsu = openingIsu))

    val events: Flow<FriendSelectorEvent> = eventQueue.events

    init {
        observeQuery()
        refresh(RefreshMode.Silent)
    }

    fun refresh(mode: RefreshMode) {
        refreshes.launch(mode) { repository.refreshFriendList() }
    }

    /** The retry button repeats what failed: the people search in the wide scope, the friend list otherwise. */
    fun retry() {
        if (scope.value == FriendSelectorScope.ALL) search(query.value.trim()) else refresh(RefreshMode.Force)
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

    /** Only a person whose schedule is open can be chosen; a closed row leads to the profile instead. */
    fun select(person: UserSummary) {
        if (person.sharing.schedule) choose(person)
    }

    fun selectOwnSchedule() {
        choose(null)
    }

    /** Records a chosen friend in the history once, then asks the host to deliver the result. */
    fun apply() {
        val made = choice.value as? Choice.Made
        if (applied || made == null || !uiState.value.canApply) return
        applied = true
        val target = made.target
        viewModelScope.launch {
            target?.let { history.record(it.isu) }
            eventQueue.send(FriendSelectorEvent.Apply(target))
        }
    }

    private fun choose(target: UserSummary?) {
        choice.value = Choice.Made(target)
        savedState[KEY_PENDING_ISU] = target?.isu ?: FriendSelectionContract.NO_USER_ISU
    }

    private fun inputs(): Flow<Inputs> = combine(scope, query, people, choice, ::Inputs)

    /** People search only runs in the wide scope; the friends scope filters locally. */
    private fun observeQuery() {
        viewModelScope.launch {
            combine(scope, query.map { it.trim() }) { scope, query -> scope to query }
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { (scope, query) -> if (scope == FriendSelectorScope.ALL) search(query) }
        }
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
                is AppResult.Success -> PeopleResults.Content(page.value.results.mapNotNull { it.registered?.user })
                is AppResult.Failure -> PeopleResults.Error(page.error)
            }
        }
    }

    private suspend fun toUiState(
        friendList: LoadState<List<UserSummary>>,
        currentUser: UserSummary?,
        refreshing: Boolean,
        inputs: Inputs
    ): FriendSelectorUiState {
        val body = body(friendList, refreshing, inputs)
        val base = FriendSelectorUiState(body = body, scope = inputs.scope, currentUser = currentUser)
        if (!base.canApply) {
            // Nothing loaded to choose from: chips and selection stay as last shown.
            val made = inputs.choice as? Choice.Made
            return base.copy(
                recentFriends = lastRecent,
                selectedIsu = if (made == null) openingIsu else made.target?.isu,
                applyTarget = made?.target
            )
        }
        val friends = friendList.valueOrNull().orEmpty()
        val target = settleChoice(inputs.choice, friends, body)
        lastRecent = resolveRecent(friends)
        return base.copy(recentFriends = lastRecent, selectedIsu = target?.isu, applyTarget = target)
    }

    private fun body(
        friendList: LoadState<List<UserSummary>>,
        refreshing: Boolean,
        inputs: Inputs
    ): FriendSelectorBody {
        // Keep an existing list on screen while reloading; otherwise show progress.
        if (refreshing && friendList.valueOrNull().isNullOrEmpty()) return FriendSelectorBody.Loading
        return when (friendList) {
            LoadState.Loading -> FriendSelectorBody.Loading
            LoadState.Disabled -> FriendSelectorBody.Disabled
            is LoadState.Error -> FriendSelectorBody.Error(friendList.error)
            is LoadState.Content -> when {
                inputs.scope == FriendSelectorScope.ALL -> peopleBody(inputs.people)
                friendList.value.isEmpty() -> FriendSelectorBody.NoFriends
                else -> friendList.value.matching(inputs.query)
                    .takeIf { it.isNotEmpty() }
                    ?.let(FriendSelectorBody::Users)
                    ?: FriendSelectorBody.NoMatches
            }
        }
    }

    private fun peopleBody(people: PeopleResults): FriendSelectorBody = when (people) {
        PeopleResults.Idle -> FriendSelectorBody.PeopleIdle
        PeopleResults.Loading -> FriendSelectorBody.PeopleLoading
        is PeopleResults.Content ->
            if (people.people.isEmpty()) FriendSelectorBody.PeopleEmpty else FriendSelectorBody.Users(people.people)
        is PeopleResults.Error -> FriendSelectorBody.PeopleError(people.error)
    }

    /**
     * The opening ISU becomes the choice once, on the first loaded list, and only for a listed friend whose schedule
     * is open. A friend list that loads empty resets the choice to the own schedule.
     */
    private fun settleChoice(current: Choice, friends: List<UserSummary>, body: FriendSelectorBody): UserSummary? {
        val settled = when {
            body == FriendSelectorBody.NoFriends -> Choice.Made(null)
            current is Choice.Made -> current
            else -> Choice.Made(friends.firstOrNull { it.isu == openingIsu && it.sharing.schedule })
        }
        if (settled != current && choice.compareAndSet(current, settled)) {
            savedState[KEY_PENDING_ISU] = settled.target?.isu ?: FriendSelectionContract.NO_USER_ISU
        }
        return settled.target
    }

    /** Chip identities and order are fixed by the first loaded list; later history changes never move them. */
    private suspend fun resolveRecent(friends: List<UserSummary>): List<UserSummary> {
        val fromHistory = if (recentOrder.snapshot == null) {
            val byIsu = friends.associateBy(UserSummary::isu)
            history.getRecentIsu().mapNotNull(byIsu::get)
        } else {
            emptyList()
        }
        val recent = recentOrder.resolve(friends, fromHistory, openingIsu)
        if (!savedState.contains(KEY_RECENT_ORDER)) {
            recentOrder.snapshot?.let { savedState[KEY_RECENT_ORDER] = it.toIntArray() }
        }
        return recent
    }

    private fun List<UserSummary>.matching(query: String): List<UserSummary> {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return this
        return filter { friend ->
            friend.name.lowercase().contains(normalized) ||
                friend.isu.toString().contains(normalized) ||
                friend.groups.any { group ->
                    group.name.lowercase().contains(normalized) ||
                        group.facultyShortName.lowercase().contains(normalized) ||
                        group.course.toString().contains(normalized)
                }
        }
    }

    private sealed interface Choice {
        /** Nothing chosen yet: the opening ISU waits for the first loaded list. */
        data object Opening : Choice

        /** [target] is the chosen person, or `null` for the own schedule. */
        data class Made(val target: UserSummary?) : Choice
    }

    private data class Inputs(
        val scope: FriendSelectorScope,
        val query: String,
        val people: PeopleResults,
        val choice: Choice
    )

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val KEY_PENDING_ISU = "pending_friend_isu"
        const val KEY_RECENT_ORDER = "recent_friend_order"
    }
}
