package dev.alllexey.itmowidgets.feature.resources.presentation

import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.RestrictionCapability
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.resources.SubjectLinksSnapshot
import dev.alllexey.itmowidgets.core.resources.UserRestriction
import dev.alllexey.itmowidgets.core.resources.blocks
import dev.alllexey.itmowidgets.core.result.AppError

sealed interface LinkSection {
    val links: List<SubjectLink>

    /** Own and shared links of one category by `SubjectLinkRanking`; chats are the CHAT section after all the others. */
    data class Category(val category: LinkCategory, override val links: List<SubjectLink>) : LinkSection

    /** Approved links of past periods, always last. */
    data class Previous(override val links: List<SubjectLink>) : LinkSection
}

data class SubjectLinksUiState(
    val content: SubjectLinksSnapshot? = null,
    val sections: List<LinkSection> = emptyList(),
    val restrictions: List<UserRestriction> = emptyList(),
    val refreshing: Boolean = false,
    val error: AppError? = null,
    /**
     * An action was sent: the sheet ignores taps until it fails, or for good once it succeeded, since every action
     * but a vote closes the sheet that sent it.
     */
    val busy: Boolean = false,
) {
    val canVote: Boolean get() = allows(RestrictionCapability.VOTE)
    val canReport: Boolean get() = allows(RestrictionCapability.REPORT)

    private fun allows(capability: RestrictionCapability) =
        content?.servicesEnabled == true && restrictions.blocks(capability) == null
}
