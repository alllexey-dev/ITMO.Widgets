package dev.alllexey.itmowidgets.client.links

import dev.alllexey.itmowidgets.client.common.ModerationReportRequest
import dev.alllexey.itmowidgets.client.common.ResourceVoteRequest
import dev.alllexey.itmowidgets.client.error.BackendException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid

/**
 * Subject links (routes under `/api/subjects/{subjectId}/links` and `/api/links`): HTTPS links per subject and
 * period, shared with one schedule flow or everybody, with votes, reports and the viewer's restrictions. Semantics
 * are in Backend's `subject-links.md` contract.
 *
 * Restrictions block mutations, never reads: saving a non-private link needs `SUBMIT_RESOURCES`, voting `VOTE`,
 * reporting `REPORT`; a restricted action is 403 `restricted` ([BackendException.Forbidden.code]), distinct from 403
 * `permission_denied` on another owner's link. A daily submission or report limit is 409.
 *
 * Not mirrored: the moderation routes under `/api/moderation` (moderators only, CO-02) and the moderation-only types
 * `SubjectLinkRevision` and `LinkRevisionStatus`.
 */
interface SubjectLinksApi {

    /**
     * `GET /api/subjects/{subjectId}/links?period=`: the viewer's own, shared and previous links of the subject in
     * [period] (`YYYY-S`).
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun subjectLinks(subjectId: Long, period: String): SubjectLinksResponse

    /**
     * `PUT /api/links/{id}`: creates the link under the client-generated [id] or edits the viewer's own link and
     * returns the owner view. 403 `permission_denied` for another owner's link, 403 `restricted` without
     * `SUBMIT_RESOURCES` for a non-private link, 400 for another subject or period or a flow outside the viewer's
     * audiences, 409 over the daily submission limit. Saving identical content creates no revision.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun saveSubjectLink(id: Uuid, request: SaveSubjectLinkRequest): SubjectLink

    /**
     * `DELETE /api/links/{id}`: removes the viewer's own link with its votes and pins; a missing link is a no-op,
     * another owner's link 403. Backend answers an empty object.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun deleteSubjectLink(id: Uuid)

    /**
     * `PUT /api/subjects/{subjectId}/links/pin`: pins a link from the viewer's own lists of that subject and period
     * (404 otherwise) or clears the pin, and returns the lists again.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun pinSubjectLink(subjectId: Long, request: PinSubjectLinkRequest): SubjectLinksResponse

    /**
     * `PUT /api/links/{id}/vote`: -1 or 1 replaces the viewer's vote, 0 removes it; returns the link with the new
     * score. 409 on the viewer's own link, 404 on a link the viewer does not see, 403 `restricted` without `VOTE`.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun voteSubjectLink(id: Uuid, request: ResourceVoteRequest): SubjectLink

    /**
     * `POST /api/links/{id}/report`: reports the content the viewer currently sees, with a link reason (`BROKEN`,
     * `WRONG_SUBJECT`, `SPAM`, `OTHER`). 409 on the viewer's own link, 403 `restricted` without `REPORT`.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun reportSubjectLink(id: Uuid, request: ModerationReportRequest): SubjectLink

    /** `GET /api/users/me/restrictions`: the viewer's active restrictions. */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun myRestrictions(): List<UserRestriction>
}
