package dev.alllexey.itmowidgets.client.common

import dev.alllexey.itmowidgets.client.json.StrictEnumSerializer
import kotlinx.serialization.Serializable

/** A vote on a subject link or a teacher review: -1 or 1 replaces the viewer's vote, 0 removes it. */
@Serializable
data class ResourceVoteRequest(val value: Int) {
    init {
        require(value in -1..1) { "A vote is -1, 0 or 1" }
    }
}

/** A report on a subject link or a teacher review; a `null` [comment] is omitted from the body. */
@Serializable
data class ModerationReportRequest(
    val reason: ReportReason,
    val comment: String? = null,
)

/**
 * Why a resource is reported. Links accept [BROKEN], [WRONG_SUBJECT], [SPAM] and [OTHER]; teacher reviews accept
 * [OFFENSIVE], [WRONG_TEACHER], [SPAM] and [OTHER]. Backend refuses a reason of the other kind. Request-only, so
 * strict.
 */
@Serializable(with = ReportReasonSerializer::class)
enum class ReportReason { BROKEN, WRONG_SUBJECT, SPAM, OTHER, OFFENSIVE, WRONG_TEACHER }

internal object ReportReasonSerializer : StrictEnumSerializer<ReportReason>("ReportReason", ReportReason.entries)
