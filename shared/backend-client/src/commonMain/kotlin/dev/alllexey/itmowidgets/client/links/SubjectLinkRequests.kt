package dev.alllexey.itmowidgets.client.links

import dev.alllexey.itmowidgets.client.json.UuidSerializer
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Creates a link under a client-generated ID or replaces the viewer's own link ([SubjectLinksApi.saveSubjectLink]).
 * The subject and period of a link never change; [subjectName] (1 to 200 characters) is display text. [title] is at
 * most 120 characters, blank means none; [url] must be HTTPS without user info or a port other than 443, at most
 * 2000 characters. A `null` [title] or [flowId] is omitted from the body.
 *
 * [flowId] is required with [LinkVisibility.FLOW] and must be one of [SubjectLinksResponse.audiences]; it is `null`
 * otherwise. Construction fails on any other combination, which Backend would answer with 400.
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
) {
    init {
        requireFlowScope(visibility, flowId)
    }
}

/** Pins one of the viewer's listed links for a period; a `null` [linkId] clears the pin and is omitted. */
@Serializable
data class PinSubjectLinkRequest(
    val periodKey: String,
    @Serializable(with = UuidSerializer::class) val linkId: Uuid? = null,
)
