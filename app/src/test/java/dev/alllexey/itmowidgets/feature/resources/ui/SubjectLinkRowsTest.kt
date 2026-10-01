package dev.alllexey.itmowidgets.feature.resources.ui

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.ui.GroupPosition
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkSection
import dev.alllexey.itmowidgets.feature.resources.presentation.SubjectLinksUiState
import dev.alllexey.itmowidgets.feature.resources.presentation.linksSnapshot
import dev.alllexey.itmowidgets.feature.resources.presentation.subjectLink
import org.junit.Assert.assertEquals
import org.junit.Test

class SubjectLinkRowsTest {

    @Test
    fun `every section is a heading over one connected group of its links`() {
        val own = subjectLink("own", LinkCategory.SCORES)
        val shared = subjectLink("shared", LinkCategory.SCORES, LinkVisibility.ALL, isMine = false)
        val other = subjectLink("other", LinkCategory.SCORES, LinkVisibility.ALL, isMine = false)
        val chat = subjectLink("chat", LinkCategory.CHAT, LinkVisibility.ALL, isMine = false)
        val past = subjectLink("past", LinkCategory.NOTES, LinkVisibility.ALL, isMine = false)
        val state = SubjectLinksUiState(
            content = linksSnapshot(mine = listOf(own), shared = listOf(shared, other, chat)).copy(pinnedId = "shared"),
            sections = listOf(
                LinkSection.Category(LinkCategory.SCORES, listOf(shared, own, other)),
                LinkSection.Category(LinkCategory.CHAT, listOf(chat)),
                LinkSection.Previous(listOf(past)),
            ),
        )

        val rows = state.linkRows()

        assertEquals(LinkRow.Header(UiText.Resource(R.string.links_category_scores)), rows[0])
        assertEquals(
            listOf("shared" to GroupPosition.FIRST, "own" to GroupPosition.MIDDLE, "other" to GroupPosition.LAST),
            rows.subList(1, 4).map { (it as LinkRow.Item).link.id to it.position }
        )
        assertEquals(LinkRow.Header(UiText.Resource(R.string.links_chats)), rows[4])
        assertEquals(GroupPosition.SINGLE, (rows[5] as LinkRow.Item).position)
        assertEquals(LinkRow.Header(UiText.Resource(R.string.links_previous)), rows[6])
        val previous = rows[7] as LinkRow.Item
        assertEquals(GroupPosition.SINGLE, previous.position)
        assertEquals(true, previous.previous)
        assertEquals(true, (rows[1] as LinkRow.Item).pinned)
    }
}
