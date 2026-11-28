package dev.alllexey.itmowidgets.client.links

import dev.alllexey.itmowidgets.client.json.UuidSerializer
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Creates a link under a client-generated ID or replaces the viewer's own link ([SubjectLinksApi.saveSubjectLink]).
 * The subject and period of a link never change; [subjectName] (1 to 200 characters) is display text. [title] is at
 * most 120 characters, blank means none; [url] must be HTTPS without user info or a port other than 443, at most
 * 2000 characters. A `null` [title], [flowId] or [flowOptions] is omitted from the body.
 *
 * [flowId] is required with [LinkVisibility.FLOW] and must be one of [SubjectLinksResponse.audiences]; it is `null`
 * otherwise. Construction fails on any other combination, which Backend would answer with 400.
 *
 * [flowOptions] are the flows the author could choose from, [SubjectLinksResponse.audiences] as the editor showed
 * them, at most [MAX_FLOW_OPTIONS] with unique flow IDs; with FLOW they include [flowId]. Backend keeps them with the
 * moderation revision so that a moderator can move a FLOW link to another of them. They are sent with FLOW and ALL,
 * `null` with PRIVATE or when there are none; a Backend without the field skips it.
 */
@Serializable
data class SaveSubjectLinkRequest(
    val subjectId: Long,
    val subjectName: String,
    val periodKey: String,
    val category: LinkCategory,
    val url: String,
    val title: String?,
    val visibility: LinkVisibility,
    val flowId: Long? = null,
    val flowOptions: List<LinkAudience>? = null,
) {
    init {
        requireFlowScope(visibility, flowId)
        if (flowOptions != null) {
            require(visibility != LinkVisibility.PRIVATE) { "A PRIVATE link has no flow options" }
            require(flowOptions.isNotEmpty() && flowOptions.size <= MAX_FLOW_OPTIONS) { "1 to $MAX_FLOW_OPTIONS flow options" }
            require(flowOptions.distinctBy { it.flowId }.size == flowOptions.size) { "Flow options have unique flow IDs" }
            require(flowId == null || flowOptions.any { it.flowId == flowId }) { "The FLOW link's flow is one of its options" }
        }
    }

    companion object {
        /** Backend refuses a longer [flowOptions] list. */
        const val MAX_FLOW_OPTIONS = 32
    }
}

/** Pins one of the viewer's listed links for a period; a `null` [linkId] clears the pin and is omitted. */
@Serializable
data class PinSubjectLinkRequest(
    val periodKey: String,
    @Serializable(with = UuidSerializer::class) val linkId: Uuid? = null,
)
