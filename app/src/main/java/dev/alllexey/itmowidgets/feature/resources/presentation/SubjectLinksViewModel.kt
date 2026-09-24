package dev.alllexey.itmowidgets.feature.resources.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.ResourceReportReason
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.RestrictionCapability
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.resources.SubjectLinkRanking
import dev.alllexey.itmowidgets.core.resources.SubjectLinksRepository
import dev.alllexey.itmowidgets.core.resources.SubjectLinksSnapshot
import dev.alllexey.itmowidgets.core.resources.SubjectLinksState
import dev.alllexey.itmowidgets.core.resources.UserRestriction
import dev.alllexey.itmowidgets.core.resources.blocks
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.ui.toUiText
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LinkEvent {
    data class Failed(val text: UiText) : LinkEvent
    data object Saved : LinkEvent
    /** An action other than a vote succeeded; a sheet opened for one action may close. */
    data object Done : LinkEvent
}

sealed interface LinkSection {
    val links: List<SubjectLink>

    /** Own and shared links of one category by [SubjectLinkRanking]; chats are the CHAT section after all the others. */
    data class Category(val category: LinkCategory, override val links: List<SubjectLink>) : LinkSection

    /** Approved links of past periods, always last. */
    data class Previous(override val links: List<SubjectLink>) : LinkSection
}

data class SubjectLinksUiState(
    val content: SubjectLinksSnapshot? = null,
    val sections: List<LinkSection> = emptyList(),
    val restrictions: List<UserRestriction> = emptyList(),
    val refreshing: Boolean = false,
    val error: AppError? = null,
) {
    val canVote: Boolean get() = allows(RestrictionCapability.VOTE)
    val canReport: Boolean get() = allows(RestrictionCapability.REPORT)

    private fun allows(capability: RestrictionCapability) =
        content?.servicesEnabled == true && restrictions.blocks(capability) == null
}

/** The «Все ссылки» sheet of one subject period. */
@HiltViewModel
class SubjectLinksViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repository: SubjectLinksRepository,
) : ViewModel() {
    val scope = ResourceScope(checkNotNull(handle[SubjectLinksArgs.SUBJECT_ID]),
        checkNotNull(handle[SubjectLinksArgs.SUBJECT_NAME]), checkNotNull(handle[SubjectLinksArgs.PERIOD_KEY]))
    private val refreshing = MutableStateFlow(false)
    private var refreshInFlight = false
    private var busy = false
    private val channel = Channel<LinkEvent>(Channel.BUFFERED)
    val events = channel.receiveAsFlow()

    val uiState: StateFlow<SubjectLinksUiState> = combine(
        repository.observe(scope), repository.observeRestrictions(), refreshing,
    ) { state, restrictions, pulling ->
        when (state) {
            SubjectLinksState.Loading -> SubjectLinksUiState(restrictions = restrictions, refreshing = pulling)
            is SubjectLinksState.Error -> SubjectLinksUiState(restrictions = restrictions, refreshing = pulling, error = state.error)
            is SubjectLinksState.Content ->
                SubjectLinksUiState(state.snapshot, linkSections(state.snapshot), restrictions, pulling)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubjectLinksUiState())

    init { refresh(silent = true) }

    /** A pull shows the indicator and reports a failure; the load on entry is silent behind the cache. */
    fun refresh(silent: Boolean = false) {
        if (!silent) refreshing.value = true
        if (refreshInFlight) return
        refreshInFlight = true
        viewModelScope.launch {
            try {
                val result = repository.refresh(scope)
                repository.refreshRestrictions()
                if (result is AppResult.Failure && refreshing.value && repository.peek(scope) != null) {
                    channel.send(LinkEvent.Failed(result.error.toUiText()))
                }
            } finally {
                refreshing.value = false
                refreshInFlight = false
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

    private fun act(block: suspend () -> AppResult<*>) = runOnce {
        block().also { if (it is AppResult.Success) channel.send(LinkEvent.Done) }
    }

    /** One action at a time: a second tap while the first is in flight is ignored. A failure is an event. */
    private fun runOnce(block: suspend () -> AppResult<*>) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val result = block()
                if (result is AppResult.Failure) channel.send(LinkEvent.Failed(result.error.toUiText()))
            } finally {
                busy = false
            }
        }
    }
}

/** Categories in declaration order with chats after them, then the links of past periods. */
internal fun linkSections(snapshot: SubjectLinksSnapshot): List<LinkSection> {
    val byCategory = (snapshot.mine + snapshot.shared).distinctBy { it.id }.sortedWith(SubjectLinkRanking).groupBy { it.category }
    val order = LinkCategory.entries.filter { it != LinkCategory.CHAT } + LinkCategory.CHAT
    val current = order.mapNotNull { category -> byCategory[category]?.let { LinkSection.Category(category, it) } }
    return current + listOfNotNull(snapshot.previous.takeIf { it.isNotEmpty() }?.let(LinkSection::Previous))
}
