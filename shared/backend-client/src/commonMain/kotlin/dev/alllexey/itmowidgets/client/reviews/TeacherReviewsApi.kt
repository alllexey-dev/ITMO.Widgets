package dev.alllexey.itmowidgets.client.reviews

import dev.alllexey.itmowidgets.client.common.ModerationReportRequest
import dev.alllexey.itmowidgets.client.common.ResourceVoteRequest
import dev.alllexey.itmowidgets.client.error.BackendException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid

/**
 * Teacher reviews (routes under `/api/teachers` and `/api/reviews`): own reviews of ITMO.Widgets users
 * (`COMMUNITY`, always premoderated) and copies from the Reviews project (`REVIEWS`) of one teacher, with votes,
 * reports and the AI summary. Semantics are in Backend's `teacher-reviews.md` contract.
 *
 * Every route except [teacherSummaryLevels] answers with the fresh [TeacherReviewsResponse] of the review's teacher
 * for the viewer. The viewer's capabilities come in `canWrite`, `canVote` and `canReport`; a restricted action is
 * 403 `restricted` ([BackendException.Forbidden.code]). Values outside Backend's rules (ISU outside
 * `100000..9999999` on a write, a review of oneself, text or `flowIds` limits, a link report reason) are 400
 * `invalid_request_data`; voting on or reporting one's own review, reporting a copy, a repeated report and the
 * daily limits are 409 `business_rule_violation`.
 *
 * Not mirrored: the moderation routes under `/api/moderation` (moderators only, CO-02) and the moderation-only types
 * `TeacherReviewRevision`, `ModeratedTeacherReview`, `ReviewRevisionStatus` and `ReviewVerification`.
 */
interface TeacherReviewsApi {

    /**
     * `GET /api/teachers/{isu}/reviews`. An unknown positive ISU is an answer with empty lists, not 404; `isu <= 0`
     * is 400.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun teacherReviews(isu: Int): TeacherReviewsResponse

    /**
     * `PUT /api/teachers/{isu}/reviews/mine`: creates or edits the viewer's review. A new subject or text creates a
     * revision that waits for review (needs `WRITE_REVIEWS`, counts against the daily limit); switching `anonymous`
     * or changing only `flowIds` applies at once.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun saveMyTeacherReview(isu: Int, request: SaveTeacherReviewRequest): TeacherReviewsResponse

    /** `DELETE /api/teachers/{isu}/reviews/mine`: always allowed; without a review it is a no-op. */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun deleteMyTeacherReview(isu: Int): TeacherReviewsResponse

    /**
     * `PUT /api/reviews/{id}/vote`: -1 or 1 replaces the viewer's vote, 0 removes it, on another author's published
     * review or an active copy. 404 `not_found` for an unknown, hidden or removed review; 403 `restricted` without
     * `VOTE`.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun voteTeacherReview(id: Uuid, request: ResourceVoteRequest): TeacherReviewsResponse

    /**
     * `POST /api/reviews/{id}/report`: reports the revision the viewer currently sees, with a review reason
     * (`OFFENSIVE`, `WRONG_TEACHER`, `SPAM`, `OTHER`). 403 `restricted` without `REPORT`.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun reportTeacherReview(id: Uuid, request: ModerationReportRequest): TeacherReviewsResponse

    /**
     * `GET /api/teachers/summary-levels?isu=..&isu=..`: the tone of each asked teacher whose shown summary has
     * `MEDIUM` or `HIGH` confidence, in the order asked; others are left out. Each ISU is a repeated `isu`
     * parameter. Backend accepts 1 to 50 distinct ISUs in `100000..9999999` (repeats are ignored) and answers
     * anything else with 400; an empty list is 400 `invalid_request`.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun teacherSummaryLevels(isus: List<Int>): List<TeacherSummaryLevel>
}
