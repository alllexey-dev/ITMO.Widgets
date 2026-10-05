package dev.alllexey.itmowidgets.client.links

import dev.alllexey.itmowidgets.client.common.UserData
import dev.alllexey.itmowidgets.client.json.StrictEnumSerializer
import dev.alllexey.itmowidgets.client.json.UnknownTolerantEnumSerializer
import dev.alllexey.itmowidgets.client.json.UuidSerializer
import dev.alllexey.itmowidgets.client.json.WireInstantSerializer
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A period-scoped link as the authenticated viewer sees it. For the owner it carries the link's current content and
 * [updatedAt]; for everybody else the latest approved revision's content and its decision time.
 *
 * Invariants checked on decode and construction: [flowId] is present exactly with [LinkVisibility.FLOW], and
 * [myVote] is -1, 0 or 1.
 *
 * @property periodKey `YYYY-S`: the start year of the academic year, `S` 1 for September to January, 2 for February
 *   to August.
 * @property title At most 120 characters; absent or `null` when there is none.
 * @property flowId The schedule flow of a FLOW link; `null` otherwise.
 * @property audienceLabel The schedule name of a FLOW link's flow (`ФИЗ ПИИКТ 3.2.1`); `null` otherwise.
 * @property status The owner's review state; other viewers always receive [SubjectLinkStatus.PUBLISHED].
 * @property reviewNote The moderator's note of a [SubjectLinkStatus.REJECTED] link.
 * @property score The sum of all votes.
 * @property myVote The viewer's vote: -1, 0 (none) or 1.
 * @property author The author with capabilities for the viewer; `null` on the viewer's own links.
 */
@Serializable
data class SubjectLink(
    @Serializable(with = UuidSerializer::class) val id: Uuid,
    val subjectId: Long,
    val subjectName: String,
    val periodKey: String,
    val category: LinkCategory,
    val url: String,
    val title: String?,
    val visibility: LinkVisibility,
    val flowId: Long?,
    val audienceLabel: String?,
    val status: SubjectLinkStatus,
    val reviewNote: String?,
    val score: Int,
    val myVote: Int,
    val isMine: Boolean,
    val reportedByMe: Boolean,
    val author: UserData?,
    @Serializable(with = WireInstantSerializer::class) val updatedAt: Instant,
) {
    init {
        requireFlowScope(visibility, flowId)
        require(myVote in -1..1) { "A vote is -1, 0 or 1" }
    }
}

/**
 * What a link is for. Approved MATERIALS, TASKS, RECORDINGS, NOTES and EXAM links are also shown to later periods;
 * a CHAT link is an ordinary link that apps show separately. A display enum: a value added by a newer Backend
 * decodes as [UNKNOWN], which is never sent back.
 */
@Serializable(with = LinkCategorySerializer::class)
enum class LinkCategory { SCORES, QUEUE, MATERIALS, TASKS, RECORDINGS, NOTES, EXAM, CHAT, OTHER, UNKNOWN }

internal object LinkCategorySerializer :
    UnknownTolerantEnumSerializer<LinkCategory>("LinkCategory", LinkCategory.entries, LinkCategory.UNKNOWN)

/** Who sees a link. It decides the audience, so it is strict: an unknown value fails decoding. */
@Serializable(with = LinkVisibilitySerializer::class)
enum class LinkVisibility {
    /** The owner only. */
    PRIVATE,

    /** One schedule flow of the author (`flowId`); published at once. */
    FLOW,

    /** Every student of the subject; waits for review when premoderation is on. */
    ALL,
}

internal object LinkVisibilitySerializer :
    StrictEnumSerializer<LinkVisibility>("LinkVisibility", LinkVisibility.entries)

/**
 * The review state of a link, in Backend's order of precedence: [HIDDEN], [PRIVATE], [PENDING], [REJECTED],
 * [PUBLISHED]. Owners see their own link's state; others always receive [PUBLISHED]. A display enum: a newer value
 * decodes as [UNKNOWN].
 */
@Serializable(with = SubjectLinkStatusSerializer::class)
enum class SubjectLinkStatus { PRIVATE, PENDING, PUBLISHED, REJECTED, HIDDEN, UNKNOWN }

internal object SubjectLinkStatusSerializer : UnknownTolerantEnumSerializer<SubjectLinkStatus>(
    "SubjectLinkStatus",
    SubjectLinkStatus.entries,
    SubjectLinkStatus.UNKNOWN,
)

/** A FLOW link names exactly one flow; the other visibilities carry none. Backend rejects anything else with 400. */
internal fun requireFlowScope(visibility: LinkVisibility, flowId: Long?) {
    require((visibility == LinkVisibility.FLOW) == (flowId != null)) {
        "flowId must be present exactly for FLOW visibility"
    }
}
