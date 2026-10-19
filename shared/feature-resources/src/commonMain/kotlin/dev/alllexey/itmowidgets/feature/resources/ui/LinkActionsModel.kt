package dev.alllexey.itmowidgets.feature.resources.ui

import dev.alllexey.itmowidgets.core.resources.GoogleSheetUrl
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.resources.SubjectLinkStatus
import dev.alllexey.itmowidgets.feature.resources.presentation.SubjectLinksUiState

/** The rows of the link actions sheet, in display order. */
enum class LinkAction { OPEN, AUTHOR, COPY, SCORES, PIN, EDIT, DELETE, REPORT }

/**
 * What the link actions sheet shows for one link of [SubjectLinksUiState]: the vote pill ([showsScore], with thumbs
 * while [canVote]), the moderator's [reviewNote] of an own rejected or hidden link, the profile [author] of another
 * student's link and the [rows] in display order.
 */
data class LinkActionsModel(
    val link: SubjectLink,
    val pinned: Boolean,
    val previous: Boolean,
    val showsScore: Boolean,
    val canVote: Boolean,
    val reviewNote: String?,
    val author: String?,
    val rows: List<LinkAction>,
)

/**
 * The sheet's rules for [linkId], or null while the links load or once the link is gone. Others' links vote; an own
 * shared link shows its score without thumbs, an own private one none. Pinning needs the connection unless the link is
 * one's own; reporting needs it, no `REPORT` restriction and no earlier report. A deleted account's shared links move
 * to a placeholder author with a negative ISU and no profile, so only a positive ISU is named.
 */
fun SubjectLinksUiState.linkActions(linkId: String): LinkActionsModel? {
    val snapshot = content ?: return null
    val link = (snapshot.mine + snapshot.shared + snapshot.previous).firstOrNull { it.id == linkId } ?: return null
    val reviewed = link.status == SubjectLinkStatus.REJECTED || link.status == SubjectLinkStatus.HIDDEN
    val author = link.author?.takeIf { !link.isMine && it.isu > 0 }
    return LinkActionsModel(
        link = link,
        pinned = snapshot.pinnedId == link.id,
        previous = snapshot.previous.any { it.id == link.id },
        showsScore = !link.isMine || link.visibility != LinkVisibility.PRIVATE,
        canVote = !link.isMine && canVote,
        reviewNote = link.reviewNote.takeIf { link.isMine && reviewed },
        author = author?.name,
        rows = listOfNotNull(
            LinkAction.OPEN,
            LinkAction.AUTHOR.takeIf { author != null },
            LinkAction.COPY,
            LinkAction.SCORES.takeIf { GoogleSheetUrl.parse(link.url) != null },
            LinkAction.PIN.takeIf { link.isMine || snapshot.servicesEnabled },
            LinkAction.EDIT.takeIf { link.isMine },
            LinkAction.DELETE.takeIf { link.isMine },
            LinkAction.REPORT.takeIf { !link.isMine && canReport && !link.reportedByMe },
        ),
    )
}
