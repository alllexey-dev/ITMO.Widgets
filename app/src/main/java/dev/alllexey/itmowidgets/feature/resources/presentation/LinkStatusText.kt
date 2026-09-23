package dev.alllexey.itmowidgets.feature.resources.presentation

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.resources.LinkAudience
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.SubjectLinkStatus
import dev.alllexey.itmowidgets.core.text.UiText

/** Only states the owner has to notice get a badge. */
fun SubjectLinkStatus.badge(): UiText? = when (this) {
    SubjectLinkStatus.PENDING -> UiText.Resource(R.string.links_status_pending)
    SubjectLinkStatus.REJECTED -> UiText.Resource(R.string.links_status_rejected)
    SubjectLinkStatus.HIDDEN -> UiText.Resource(R.string.links_status_hidden)
    SubjectLinkStatus.PRIVATE, SubjectLinkStatus.PUBLISHED -> null
}

fun LinkCategory.title(): UiText = UiText.Resource(when (this) {
    LinkCategory.SCORES -> R.string.links_category_scores
    LinkCategory.QUEUE -> R.string.links_category_queue
    LinkCategory.MATERIALS -> R.string.links_category_materials
    LinkCategory.TASKS -> R.string.links_category_tasks
    LinkCategory.RECORDINGS -> R.string.links_category_recordings
    LinkCategory.NOTES -> R.string.links_category_notes
    LinkCategory.EXAM -> R.string.links_category_exam
    LinkCategory.CHAT -> R.string.links_category_chat
    LinkCategory.OTHER -> R.string.links_category_other
})

/** With an [audience] a group or flow is named by its schedule groups, e.g. «Группа P3119». */
fun LinkVisibility.label(audience: LinkAudience? = null): UiText = when {
    this == LinkVisibility.GROUP && audience != null -> UiText.Resource(R.string.links_visibility_group_audience, listOf(audience.label))
    this == LinkVisibility.FLOW && audience != null -> UiText.Resource(R.string.links_visibility_flow_audience, listOf(audience.label))
    else -> UiText.Resource(when (this) {
        LinkVisibility.PRIVATE -> R.string.links_visibility_private
        LinkVisibility.GROUP -> R.string.links_visibility_group
        LinkVisibility.FLOW -> R.string.links_visibility_flow
        LinkVisibility.ALL -> R.string.links_visibility_all
    })
}
