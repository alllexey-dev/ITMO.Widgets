package dev.alllexey.itmowidgets.feature.resources.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.presentation.BusyKeys
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.presentation.RefreshTracker
import dev.alllexey.itmowidgets.core.presentation.StableOrder
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.ResourceReportReason
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.resources.SubjectLinkRanking
import dev.alllexey.itmowidgets.core.resources.SubjectLinksRepository
import dev.alllexey.itmowidgets.core.resources.SubjectLinksSnapshot
import dev.alllexey.itmowidgets.core.resources.SubjectLinksState
import dev.alllexey.itmowidgets.core.resources.UserRestriction
import dev.alllexey.itmowidgets.core.result.AppResult
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** The «Все ссылки» sheet of one subject period; the actions sheet and the report dialog act through it too. */
@HiltViewModel
class SubjectLinksViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repository: SubjectLinksRepository,
) : ViewModel() {
    val scope = ResourceScope(checkNotNull(handle[SubjectLinksArgs.SUBJECT_ID]),
        checkNotNull(handle[SubjectLinksArgs.SUBJECT_NAME]), checkNotNull(handle[SubjectLinksArgs.PERIOD_KEY]))
    private val refreshes = RefreshTracker(viewModelScope)
    /** One action at a time, whatever the link: the only key is [Unit]. */
    private val actions = BusyKeys<Unit>(viewModelScope)
    /** An action other than a vote succeeded and its sheet is closing; its rows take no more taps. */
    private val closing = MutableStateFlow(false)
    /** The order of the shown links while the sheet is open: a vote must not move a row under the finger. */
    private val shownOrder = StableOrder()
    /** Counts the refreshes the user asked for, so the shown links are ranked afresh at once. */
    private val rankings = MutableStateFlow(0)
    private val eventQueue = EventQueue<LinkEvent>()

    val uiState: StateFlow<SubjectLinksUiState> = combine(
        repository.observe(scope), repository.observeRestrictions(), refreshes.refreshing,
        combine(actions.busy, closing) { busy, closed -> busy.isNotEmpty() || closed }, rankings,
    ) { state, restrictions, refreshing, busy, _ ->
        toUiState(state, restrictions, refreshing, busy)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SubjectLinksUiState())

    val events: Flow<LinkEvent> = eventQueue.events

    init { refresh(RefreshMode.Silent) }

    /**
     * The load on entry is silent behind the cache. A refresh the user asked for shows the indicator, reports a
     * failure while there are links to keep showing, and ranks the links afresh.
     */
    fun refresh(mode: RefreshMode) {
        if (mode.showsIndicator) {
            shownOrder.reset()
            rankings.update { it + 1 }
        }
        refreshes.launch(mode) {
            val result = repository.refresh(scope)
            repository.refreshRestrictions()
            if (result is AppResult.Failure && refreshes.refreshing.value && repository.peek(scope) != null) {
                eventQueue.send(LinkEvent.Failed(result.error))
            }
        }
    }

    /** Tapping the arrow of the current vote removes it. A vote keeps the actions sheet open, so it sends no [LinkEvent.Done]. */
    fun vote(id: String, up: Boolean) {
        val link = find(id) ?: return
        val value = if (up) 1 else -1
        runOnce { repository.vote(scope, id, if (link.myVote == value) 0 else value) }
    }

    /** Pinning the pinned link unpins it. */
    fun pin(id: String) {
        val pinned = repository.peek(scope)?.pinnedId
        act { repository.pin(scope, id.takeUnless { it == pinned }) }
    }

    fun delete(id: String) = act { repository.delete(scope, id) }

    fun report(id: String, reason: ResourceReportReason, comment: String?) = act { repository.report(scope, id, reason, comment) }

    private fun find(id: String): SubjectLink? =
        repository.peek(scope)?.let { snapshot -> (snapshot.mine + snapshot.shared + snapshot.previous).firstOrNull { it.id == id } }

    /** Any action but a vote closes the sheet that sent it once it succeeds. */
    private fun act(block: suspend () -> AppResult<*>) = runOnce {
        block().also {
            if (it is AppResult.Success) {
                closing.value = true
                eventQueue.send(LinkEvent.Done)
            }
        }
    }

    /** One action at a time: a second tap while the first is in flight is ignored. A failure is an event. */
    private fun runOnce(block: suspend () -> AppResult<*>) {
        actions.launch(Unit) {
            val result = block()
            if (result is AppResult.Failure) eventQueue.send(LinkEvent.Failed(result.error))
        }
    }

    private fun toUiState(state: SubjectLinksState, restrictions: List<UserRestriction>, refreshing: Boolean, busy: Boolean) =
        when (state) {
            SubjectLinksState.Loading -> SubjectLinksUiState(restrictions = restrictions, refreshing = refreshing, busy = busy)
            is SubjectLinksState.Error ->
                SubjectLinksUiState(restrictions = restrictions, refreshing = refreshing, error = state.error, busy = busy)
            is SubjectLinksState.Content -> SubjectLinksUiState(state.snapshot, linkSections(state.snapshot, shownOrder),
                restrictions, refreshing, busy = busy)
        }
}

/**
 * Categories in declaration order, chats after them and past years last; within a section by [SubjectLinkRanking],
 * or in the order [shown] keeps while the sheet is open, so a vote does not move a row.
 */
internal fun linkSections(snapshot: SubjectLinksSnapshot, shown: StableOrder? = null): List<LinkSection> {
    val ranked = (snapshot.mine + snapshot.shared).distinctBy { it.id }.sortedWith(SubjectLinkRanking)
    val currentIds = ranked.mapTo(HashSet()) { it.id }
    val arranged = shown?.arrange(ranked + snapshot.previous) { it.id } ?: (ranked + snapshot.previous)
    val byCategory = arranged.filter { it.id in currentIds }.groupBy { it.category }
    val previous = arranged.filter { it.id !in currentIds }
    val order = LinkCategory.entries.filter { it != LinkCategory.CHAT } + LinkCategory.CHAT
    val current = order.mapNotNull { category -> byCategory[category]?.let { LinkSection.Category(category, it) } }
    return current + listOfNotNull(previous.takeIf { it.isNotEmpty() }?.let(LinkSection::Previous))
}
