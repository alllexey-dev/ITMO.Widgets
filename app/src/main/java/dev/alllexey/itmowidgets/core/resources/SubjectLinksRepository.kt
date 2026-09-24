package dev.alllexey.itmowidgets.core.resources

import dev.alllexey.itmowidgets.core.result.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * With the ITMO.Widgets opt-in the server is the source of truth and every action goes to it at once.
 * Without it links are PRIVATE and stay on the device until the first refresh with the opt-in uploads them;
 * voting, reports, pinning or saving others' links and non-PRIVATE visibility fail with `CustomServicesDisabled`.
 * A FLOW link names one of the viewer's [LinkAudience.flowId]s in [save]; other visibilities pass null.
 */
interface SubjectLinksRepository {
    fun observe(scope: ResourceScope): Flow<SubjectLinksState>
    fun peek(scope: ResourceScope): SubjectLinksSnapshot?
    suspend fun refresh(scope: ResourceScope): AppResult<Unit>
    suspend fun save(
        scope: ResourceScope,
        id: String,
        category: LinkCategory,
        url: String,
        title: String?,
        visibility: LinkVisibility,
        flowId: Long?,
    ): AppResult<SubjectLink>
    suspend fun delete(scope: ResourceScope, id: String): AppResult<Unit>
    /** A null [id] removes the pin of the period. */
    suspend fun pin(scope: ResourceScope, id: String?): AppResult<Unit>
    /** -1 or +1 replaces the previous vote, 0 removes it. */
    suspend fun vote(scope: ResourceScope, id: String, value: Int): AppResult<Unit>
    suspend fun report(scope: ResourceScope, id: String, reason: ResourceReportReason, comment: String?): AppResult<Unit>
    fun observeRestrictions(): Flow<List<UserRestriction>>
    suspend fun refreshRestrictions(): AppResult<Unit>
}
