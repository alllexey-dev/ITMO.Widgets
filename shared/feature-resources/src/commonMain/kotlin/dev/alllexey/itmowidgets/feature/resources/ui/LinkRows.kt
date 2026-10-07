package dev.alllexey.itmowidgets.feature.resources.ui

import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.resources.title
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.designsystem.components.groups.GroupPosition
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkSection
import dev.alllexey.itmowidgets.feature.resources.presentation.SubjectLinksUiState
import dev.alllexey.itmowidgets.shared.core.links_chats
import dev.alllexey.itmowidgets.shared.feature.resources.Res
import dev.alllexey.itmowidgets.shared.feature.resources.links_previous
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes

/** One row of the `Все ссылки` list: a section heading or a link inside its section's connected group. */
sealed interface LinkListRow {
    /** Stable across votes and refreshes, so the list keeps a row in place while its pill changes. */
    val key: String

    /** [first] sits closer to the sheet's header, which already stands above the first group. */
    data class Header(val title: UiText, val first: Boolean, override val key: String) : LinkListRow

    data class Item(
        val link: SubjectLink,
        val pinned: Boolean,
        val canVote: Boolean,
        val previous: Boolean,
        val position: GroupPosition = GroupPosition.Single,
    ) : LinkListRow {
        override val key: String get() = "link:${link.id}"
    }
}

/** Sections become a heading followed by their links as one connected group; chats are titled `Чаты`. */
fun SubjectLinksUiState.linkRows(): List<LinkListRow> {
    val snapshot = content ?: return emptyList()
    return sections.flatMapIndexed { index, section ->
        val header = when (section) {
            is LinkSection.Category -> LinkListRow.Header(
                if (section.category == LinkCategory.CHAT) UiText.Res(CoreRes.string.links_chats) else section.category.title(),
                first = index == 0,
                key = "header:${section.category.name}",
            )
            is LinkSection.Previous -> LinkListRow.Header(UiText.Res(Res.string.links_previous), index == 0, "header:previous")
        }
        listOf(header) + section.links.mapIndexed { position, link ->
            LinkListRow.Item(
                link,
                pinned = link.id == snapshot.pinnedId,
                canVote = canVote,
                previous = section is LinkSection.Previous,
                position = GroupPosition.of(position, section.links.size),
            )
        }
    }
}
