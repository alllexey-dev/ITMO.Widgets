package dev.alllexey.itmowidgets.feature.resources.presentation

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.resources.SubjectLinkStatus
import dev.alllexey.itmowidgets.core.text.UiText

/** Only states the owner has to notice get a badge. */
fun SubjectLinkStatus.badge(): UiText? = when (this) {
    SubjectLinkStatus.PENDING -> UiText.Resource(R.string.links_status_pending)
    SubjectLinkStatus.REJECTED -> UiText.Resource(R.string.links_status_rejected)
    SubjectLinkStatus.HIDDEN -> UiText.Resource(R.string.links_status_hidden)
    SubjectLinkStatus.PRIVATE, SubjectLinkStatus.PUBLISHED -> null
}
