package dev.alllexey.itmowidgets.core.resources

import dev.alllexey.itmowidgets.core.result.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * With the ITMO.Widgets opt-in the server is the source of truth and every action goes to it at once.
 * Without it links are PRIVATE and stay on the device until the first refresh with the opt-in uploads them;
 * voting, reports, pinning or saving others' links and non-PRIVATE visibility fail with `CustomServicesDisabled`.
 * A FLOW link names one of the viewer's [LinkAudience.flowId]s in [save]; other visibilities pass null.
 * [save] also takes the flows the editor offered ([SubjectLinksSnapshot.audiences]): a FLOW or ALL link sends them to
 * the server so that a moderator can move a FLOW link to another of them; a PRIVATE link does not.
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
        flowOptions: List<LinkAudience> = emptyList(),
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
