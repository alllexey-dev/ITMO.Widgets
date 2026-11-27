package dev.alllexey.itmowidgets.client.moderation

import dev.alllexey.itmowidgets.client.json.UnknownTolerantEnumSerializer
import dev.alllexey.itmowidgets.client.json.UuidSerializer
import dev.alllexey.itmowidgets.client.json.WireInstantSerializer
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * What the author of a subject link or a teacher review is told about a moderation decision on it: an entry of
 * `GET /api/users/me/moderation-notices` and the payload of `MODERATION_NOTICE_PAYLOAD` (Backend's `notifications.md`,
 * Moderation notices). [id] is the decision's; exactly one of [link] and [review] is set. [note] is the moderator's
 * note of a rejection, null otherwise.
 */
@Serializable
data class ModerationNotice(
    @Serializable(with = UuidSerializer::class) val id: Uuid,
    val outcome: ModerationOutcome,
    val note: String? = null,
    @Serializable(with = WireInstantSerializer::class) val decidedAt: Instant,
    val link: ModerationNoticeLink? = null,
    val review: ModerationNoticeReview? = null,
) {
    companion object {
        const val TYPE: String = "MODERATION_NOTICE_PAYLOAD"
    }
}

/** The link as it is now; [title] and [host] are of the decided revision. */
@Serializable
data class ModerationNoticeLink(
    @Serializable(with = UuidSerializer::class) val id: Uuid,
    val subjectId: Long,
    val subjectName: String,
    val periodKey: String,
    val title: String? = null,
    val host: String,
)

/** The review; [teacherName] is null when Backend knows no name, [subjectTitle] is of the decided revision. */
@Serializable
data class ModerationNoticeReview(
    @Serializable(with = UuidSerializer::class) val id: Uuid,
    val teacherIsu: Int,
    val teacherName: String? = null,
    val subjectTitle: String? = null,
)

/** What the decision did. Display: a newer outcome is [UNKNOWN], which the app does not show. */
@Serializable(with = ModerationOutcomeSerializer::class)
enum class ModerationOutcome { APPROVED, REJECTED, HIDDEN, RESTORED, UNKNOWN }

internal object ModerationOutcomeSerializer :
    UnknownTolerantEnumSerializer<ModerationOutcome>("ModerationOutcome", ModerationOutcome.entries, ModerationOutcome.UNKNOWN)
