package dev.alllexey.itmowidgets.client.reviews

import dev.alllexey.itmowidgets.client.common.UserData
import dev.alllexey.itmowidgets.client.json.IsoLocalDateSerializer
import dev.alllexey.itmowidgets.client.json.UnknownTolerantEnumSerializer
import dev.alllexey.itmowidgets.client.json.UuidSerializer
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Reviews of one teacher for the authenticated viewer; every reviews route answers with it. [reviews] holds other
 * authors' published reviews and the active Reviews copies in Backend's ranked order; the viewer's own review comes
 * only in [mine].
 *
 * [canWrite], [canVote] and [canReport] are computed for the viewer (not the teacher, no matching restriction) and
 * are required on the wire without a default: a missing one fails decoding instead of offering an action.
 *
 * @property teacherIsu Positive; checked on decode and construction.
 * @property providerUrl The Reviews project page of the teacher.
 * @property knownTeacher The teacher appears in a loaded academic pair, the ISU flow cache, an active Reviews copy
 *   or a published review; apps use it to offer writing a review.
 * @property summary The shown AI summary; absent or `null` without one or while an admin hides it.
 */
@Serializable
data class TeacherReviewsResponse(
    val teacherIsu: Int,
    val providerUrl: String,
    val reviews: List<TeacherReview>,
    val mine: OwnTeacherReview?,
    val canWrite: Boolean,
    val canVote: Boolean,
    val canReport: Boolean,
    val knownTeacher: Boolean,
    val summary: TeacherSummary?,
) {
    init {
        require(teacherIsu > 0) { "teacherIsu is positive" }
    }
}

/**
 * A review as another viewer sees it.
 *
 * Invariants checked on decode and construction: [myVote] is -1, 0 or 1; at most one of [writtenOn] and
 * [writtenBeforeYear] is set; a [TeacherReviewKind.COMMUNITY] review has no [sourceTitle], [sourceLink] or
 * [writtenBeforeYear]; a [TeacherReviewKind.REVIEWS] copy has no [author], `verified = false` and
 * `reportedByMe = false`. A review of an [TeacherReviewKind.UNKNOWN] kind is checked only for the vote and the dates.
 *
 * @property writtenOn For a COMMUNITY review, the Moscow date the shown approved revision was sent.
 * @property writtenBeforeYear For a REVIEWS copy, the year the review was written before, when that is all it says.
 * @property score The sum of all votes.
 * @property myVote The viewer's vote: -1, 0 (none) or 1.
 * @property verified The ISU check proved the teacher taught the author; verification does not affect the order.
 * @property author Set only on a COMMUNITY review written under the author's name; always `null` on an anonymous one.
 * @property sourceTitle The original source of a REVIEWS copy, when Backend has one.
 * @property sourceLink The original source link of a REVIEWS copy, when Backend has one.
 */
@Serializable
data class TeacherReview(
    @Serializable(with = UuidSerializer::class) val id: Uuid,
    val kind: TeacherReviewKind,
    val subjectTitle: String?,
    @Serializable(with = IsoLocalDateSerializer::class) val writtenOn: LocalDate?,
    val writtenBeforeYear: Int?,
    val text: String,
    val score: Int,
    val myVote: Int,
    val verified: Boolean,
    val reportedByMe: Boolean,
    val author: UserData?,
    val sourceTitle: String?,
    val sourceLink: String?,
) {
    init {
        require(myVote in -1..1) { "A vote is -1, 0 or 1" }
        require(writtenOn == null || writtenBeforeYear == null) { "A review has at most one date" }
        when (kind) {
            TeacherReviewKind.COMMUNITY ->
                require(sourceTitle == null && sourceLink == null && writtenBeforeYear == null) {
                    "A community review has no source"
                }
            TeacherReviewKind.REVIEWS -> require(author == null && !verified && !reportedByMe) {
                "A copied review has no author, verification or reports"
            }
            TeacherReviewKind.UNKNOWN -> Unit
        }
    }
}

/**
 * The viewer's own review with its current content, which may still wait for review.
 *
 * @property anonymous Whether other viewers see the review without a name.
 * @property reviewNote The moderator's note; set only when [status] is [TeacherReviewStatus.REJECTED].
 * @property writtenOn The Moscow date the author's newest revision was sent.
 */
@Serializable
data class OwnTeacherReview(
    @Serializable(with = UuidSerializer::class) val id: Uuid,
    val subjectTitle: String?,
    val text: String,
    val anonymous: Boolean,
    val status: TeacherReviewStatus,
    val reviewNote: String?,
    val score: Int,
    val verified: Boolean,
    @Serializable(with = IsoLocalDateSerializer::class) val writtenOn: LocalDate,
)

/**
 * [COMMUNITY] is an own review of an ITMO.Widgets user; [REVIEWS] is a copy from the Reviews project. A display
 * enum: a kind added by a newer Backend decodes as [UNKNOWN], which is never sent back.
 */
@Serializable(with = TeacherReviewKindSerializer::class)
enum class TeacherReviewKind { COMMUNITY, REVIEWS, UNKNOWN }

internal object TeacherReviewKindSerializer : UnknownTolerantEnumSerializer<TeacherReviewKind>(
    "TeacherReviewKind",
    TeacherReviewKind.entries,
    TeacherReviewKind.UNKNOWN,
)

/**
 * The author's view of their own review, in Backend's order of precedence: [HIDDEN] (a moderator hid it),
 * [PENDING] (the newest revision waits for review), [REJECTED] (the newest revision was rejected), [PUBLISHED].
 * A display enum: a newer value decodes as [UNKNOWN].
 */
@Serializable(with = TeacherReviewStatusSerializer::class)
enum class TeacherReviewStatus { PENDING, PUBLISHED, REJECTED, HIDDEN, UNKNOWN }

internal object TeacherReviewStatusSerializer : UnknownTolerantEnumSerializer<TeacherReviewStatus>(
    "TeacherReviewStatus",
    TeacherReviewStatus.entries,
    TeacherReviewStatus.UNKNOWN,
)
