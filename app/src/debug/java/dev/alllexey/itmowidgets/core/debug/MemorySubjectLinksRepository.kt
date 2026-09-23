package dev.alllexey.itmowidgets.core.debug

import dev.alllexey.itmowidgets.BuildConfig
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
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import java.time.OffsetDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/** Debug-only fixture. It has no API, token or session dependency and cannot transmit synthetic data. */
class MemorySubjectLinksRepository : SubjectLinksRepository {
    init { check(BuildConfig.DEBUG) }

    /** Keyed by [ResourceScope.key]; a missing scope is an empty one. */
    val snapshots = MutableStateFlow<Map<String, SubjectLinksSnapshot>>(emptyMap())
    val restrictions = MutableStateFlow<List<UserRestriction>>(emptyList())
    val failure = MutableStateFlow<AppError?>(null)
    val loading = MutableStateFlow(false)
    var servicesEnabled = false
    private val now = OffsetDateTime.parse("2026-09-22T09:00:00Z")

    override fun observe(scope: ResourceScope): Flow<SubjectLinksState> = combine(snapshots, failure, loading) { _, error, busy ->
        when {
            busy -> SubjectLinksState.Loading
            error != null && snapshots.value[scope.key] == null -> SubjectLinksState.Error(error)
            else -> SubjectLinksState.Content(peek(scope))
        }
    }

    override fun peek(scope: ResourceScope): SubjectLinksSnapshot = snapshots.value[scope.key]
        ?: SubjectLinksSnapshot(emptyList(), emptyList(), emptyList(), null, emptyList(), premoderation = true, servicesEnabled)

    override suspend fun refresh(scope: ResourceScope): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun save(
        scope: ResourceScope,
        id: String,
        category: LinkCategory,
        url: String,
        title: String?,
        visibility: LinkVisibility,
    ): AppResult<SubjectLink> {
        if (!servicesEnabled && visibility != LinkVisibility.PRIVATE) return AppResult.Failure(AppError.CustomServicesDisabled)
        val link = SubjectLink(id, scope, category, url, title, visibility, null,
            if (visibility == LinkVisibility.PRIVATE) SubjectLinkStatus.PRIVATE else SubjectLinkStatus.PUBLISHED, null,
            0, 0, isMine = true, isSaved = false, reportedByMe = false, author = null, updatedAt = now, local = !servicesEnabled)
        update(scope) { it.copy(mine = it.mine.filterNot { row -> row.id == id } + link) }
        return AppResult.Success(link)
    }

    override suspend fun delete(scope: ResourceScope, id: String) = update(scope) { it.copy(mine = it.mine.filterNot { row -> row.id == id }) }

    override suspend fun setSaved(scope: ResourceScope, id: String, saved: Boolean) = online(scope) { snapshot ->
        snapshot.copy(shared = snapshot.shared.map { if (it.id == id) it.copy(isSaved = saved) else it })
    }

    override suspend fun pin(scope: ResourceScope, id: String?) = update(scope) { it.copy(pinnedId = id) }

    override suspend fun vote(scope: ResourceScope, id: String, value: Int) = online(scope) { snapshot ->
        snapshot.copy(shared = snapshot.shared.map { if (it.id == id) it.copy(myVote = value, score = it.score - it.myVote + value) else it })
    }

    override suspend fun report(scope: ResourceScope, id: String, reason: ResourceReportReason, comment: String?) = online(scope) { snapshot ->
        snapshot.copy(shared = snapshot.shared.map { if (it.id == id) it.copy(reportedByMe = true) else it })
    }

    override fun observeRestrictions(): Flow<List<UserRestriction>> = restrictions

    override suspend fun refreshRestrictions(): AppResult<Unit> = AppResult.Success(Unit)

    private fun online(scope: ResourceScope, transform: (SubjectLinksSnapshot) -> SubjectLinksSnapshot): AppResult<Unit> =
        if (servicesEnabled) update(scope, transform) else AppResult.Failure(AppError.CustomServicesDisabled)

    private fun update(scope: ResourceScope, transform: (SubjectLinksSnapshot) -> SubjectLinksSnapshot): AppResult<Unit> {
        snapshots.value = snapshots.value + (scope.key to transform(peek(scope)))
        return AppResult.Success(Unit)
    }
}
