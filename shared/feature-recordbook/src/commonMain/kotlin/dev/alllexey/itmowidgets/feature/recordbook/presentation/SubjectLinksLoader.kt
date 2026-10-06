package dev.alllexey.itmowidgets.feature.recordbook.presentation

import dev.alllexey.itmowidgets.core.presentation.StableOrder
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.RestrictionCapability
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.resources.SubjectLinksRepository
import dev.alllexey.itmowidgets.core.resources.SubjectLinksState
import dev.alllexey.itmowidgets.core.resources.blocks
import dev.alllexey.itmowidgets.core.result.AppResult
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** The links of a subject page; [canVote] is false without the connection or under a `VOTE` restriction. */
data class SubjectLinksUpdate(val links: SubjectLinksState, val canVote: Boolean)

/** Links and votes of the subject page. One instance serves one page and keeps the order its rows were shown in. */
class SubjectLinksLoader @Inject constructor(private val repository: SubjectLinksRepository) {
    /** The order of the ranked links while the page is open, so a vote does not change which three are shown. */
    private val order = StableOrder()

    /** The stored links of [scope], so the page opens with them. */
    fun cached(scope: ResourceScope): SubjectLinksState =
        repository.peek(scope)?.let { SubjectLinksState.Content(it) } ?: SubjectLinksState.Loading

    /** Refreshes the links of [scope] without an indicator and follows them with the viewer's restrictions. */
    fun observe(scope: ResourceScope): Flow<SubjectLinksUpdate> = channelFlow {
        launch { repository.refresh(scope) }
        combine(repository.observe(scope), repository.observeRestrictions()) { state, restrictions ->
            SubjectLinksUpdate(
                links = state,
                canVote = (state as? SubjectLinksState.Content)?.snapshot?.servicesEnabled == true &&
                    restrictions.blocks(RestrictionCapability.VOTE) == null
            )
        }.collect { send(it) }
    }

    /** Ranked links in the order the page first showed them; new ones follow by their rank. */
    fun arrange(ranked: List<SubjectLink>): List<SubjectLink> = order.arrange(ranked) { it.id }

    /** The next list takes its own ranking: a pull ranks the links afresh. */
    fun rankAfresh() = order.reset()

    /** Tapping the arrow of the current vote takes it back. */
    suspend fun vote(scope: ResourceScope, link: SubjectLink, up: Boolean): AppResult<Unit> {
        val value = if (up) 1 else -1
        return repository.vote(scope, link.id, if (link.myVote == value) 0 else value)
    }
}
