package dev.alllexey.itmowidgets.feature.resources.presentation

import dev.alllexey.itmowidgets.core.resources.LinkAudience
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.ResourceReportReason
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.resources.SubjectLinkStatus
import dev.alllexey.itmowidgets.core.resources.SubjectLinksRepository
import dev.alllexey.itmowidgets.core.resources.SubjectLinksSnapshot
import dev.alllexey.itmowidgets.core.resources.SubjectLinksState
import dev.alllexey.itmowidgets.core.resources.UserRestriction
import dev.alllexey.itmowidgets.core.result.AppResult
import java.time.OffsetDateTime
import kotlinx.coroutines.flow.MutableStateFlow

internal val linkScope = ResourceScope(42, "Предмет", "2026-1")
internal val linkTime: OffsetDateTime = OffsetDateTime.parse("2026-09-22T09:00:00Z")

internal fun subjectLink(
    id: String,
    category: LinkCategory = LinkCategory.MATERIALS,
    visibility: LinkVisibility = LinkVisibility.PRIVATE,
    isMine: Boolean = true,
    score: Int = 0,
    myVote: Int = 0,
    isSaved: Boolean = false,
    status: SubjectLinkStatus = if (isMine && visibility == LinkVisibility.PRIVATE) SubjectLinkStatus.PRIVATE else SubjectLinkStatus.PUBLISHED,
    title: String? = "Ссылка $id",
    flowId: Long? = if (visibility == LinkVisibility.FLOW) 7101L else null,
) = SubjectLink(id, linkScope, category, "https://example.org/$id", title, visibility, flowId, null, status, null,
    score, myVote, isMine, isSaved, reportedByMe = false, author = null, updatedAt = linkTime)

internal fun linksSnapshot(
    mine: List<SubjectLink> = emptyList(),
    shared: List<SubjectLink> = emptyList(),
    previous: List<SubjectLink> = emptyList(),
    pinnedId: String? = null,
    audiences: List<LinkAudience> = emptyList(),
    servicesEnabled: Boolean = true,
) = SubjectLinksSnapshot(mine, shared, previous, pinnedId, audiences, premoderation = true, servicesEnabled)

internal class FakeSubjectLinksRepository : SubjectLinksRepository {
    val state = MutableStateFlow<SubjectLinksState>(SubjectLinksState.Content(linksSnapshot(mine = listOf(subjectLink("own")))))
    val restrictions = MutableStateFlow<List<UserRestriction>>(emptyList())
    val actions = mutableListOf<String>()
    var result: AppResult<Unit> = AppResult.Success(Unit)
    var refreshResult: AppResult<Unit> = AppResult.Success(Unit)
    var gate: suspend () -> Unit = {}
    var refreshes = 0
    var restrictionRefreshes = 0
    var lastSave: SubjectLink? = null

    override fun observe(scope: ResourceScope) = state
    override fun peek(scope: ResourceScope) = (state.value as? SubjectLinksState.Content)?.snapshot
    override suspend fun refresh(scope: ResourceScope): AppResult<Unit> { refreshes++; return refreshResult }
    override suspend fun save(
        scope: ResourceScope,
        id: String,
        category: LinkCategory,
        url: String,
        title: String?,
        visibility: LinkVisibility,
        flowId: Long?,
    ): AppResult<SubjectLink> {
        actions += "save"
        gate()
        val link = subjectLink(id, category, visibility, flowId = flowId).copy(scope = scope, url = url, title = title)
        lastSave = link
        return (result as? AppResult.Failure) ?: AppResult.Success(link)
    }
    override suspend fun delete(scope: ResourceScope, id: String) = act("delete:$id")
    override suspend fun setSaved(scope: ResourceScope, id: String, saved: Boolean) = act("saved:$id:$saved")
    override suspend fun pin(scope: ResourceScope, id: String?) = act("pin:$id")
    override suspend fun vote(scope: ResourceScope, id: String, value: Int) = act("vote:$id:$value")
    override suspend fun report(scope: ResourceScope, id: String, reason: ResourceReportReason, comment: String?) = act("report:$id:$reason")
    override fun observeRestrictions() = restrictions
    override suspend fun refreshRestrictions(): AppResult<Unit> { restrictionRefreshes++; return AppResult.Success(Unit) }

    private suspend fun act(name: String): AppResult<Unit> {
        actions += name
        gate()
        return result
    }
}
