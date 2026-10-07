package dev.alllexey.itmowidgets.feature.resources.ui

import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.resources.SubjectLinkStatus
import dev.alllexey.itmowidgets.core.resources.host
import dev.alllexey.itmowidgets.core.resources.label
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.feature.resources.Res
import dev.alllexey.itmowidgets.shared.feature.resources.links_pinned
import dev.alllexey.itmowidgets.shared.feature.resources.links_status_hidden
import dev.alllexey.itmowidgets.shared.feature.resources.links_status_pending
import dev.alllexey.itmowidgets.shared.feature.resources.links_status_rejected

/** Only states the owner has to notice get a badge. */
fun SubjectLinkStatus.badge(): UiText? = when (this) {
    SubjectLinkStatus.PENDING -> UiText.Res(Res.string.links_status_pending)
    SubjectLinkStatus.REJECTED -> UiText.Res(Res.string.links_status_rejected)
    SubjectLinkStatus.HIDDEN -> UiText.Res(Res.string.links_status_hidden)
    SubjectLinkStatus.PRIVATE, SubjectLinkStatus.PUBLISHED -> null
}

/**
 * The second line of a link, comma-joined: the site when the title hides it, who sees the link (own and others'
 * alike; the author is named in the link's actions), the study year of a past link, the pin, and the review state
 * only the owner sees.
 */
fun linkMeta(link: SubjectLink, pinned: Boolean, previous: Boolean): UiText = UiText.Joined(
    listOfNotNull(
        UiText.Dynamic(link.host()).takeIf { link.title != null },
        link.visibility.label(link.audienceLabel),
        link.scope.periodKey.studyYear()?.takeIf { previous }?.let(UiText::Dynamic),
        UiText.Res(Res.string.links_pinned).takeIf { pinned },
        link.status.badge()?.takeIf { link.isMine },
    ),
    separator = ", ",
)

/** `2025-1` is the 2025/26 study year. */
private fun String.studyYear(): String? {
    val year = substringBefore('-').toIntOrNull() ?: return null
    return "$year/${(year + 1) % 100}"
}
