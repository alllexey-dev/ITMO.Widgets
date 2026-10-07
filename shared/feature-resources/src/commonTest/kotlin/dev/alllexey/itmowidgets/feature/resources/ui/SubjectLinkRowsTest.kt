package dev.alllexey.itmowidgets.feature.resources.ui

import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.SubjectLinkStatus
import dev.alllexey.itmowidgets.core.resources.label
import dev.alllexey.itmowidgets.core.resources.title
import dev.alllexey.itmowidgets.core.testing.linksSnapshot
import dev.alllexey.itmowidgets.core.testing.subjectLink
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.designsystem.components.groups.GroupPosition
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkSection
import dev.alllexey.itmowidgets.feature.resources.presentation.SubjectLinksUiState
import dev.alllexey.itmowidgets.shared.core.links_chats
import dev.alllexey.itmowidgets.shared.feature.resources.Res
import dev.alllexey.itmowidgets.shared.feature.resources.links_pinned
import dev.alllexey.itmowidgets.shared.feature.resources.links_previous
import dev.alllexey.itmowidgets.shared.feature.resources.links_status_hidden
import dev.alllexey.itmowidgets.shared.feature.resources.links_status_pending
import dev.alllexey.itmowidgets.shared.feature.resources.links_status_rejected
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes

class SubjectLinkRowsTest {

    @Test
    fun everySectionIsAHeadingOverOneConnectedGroupOfItsLinks() {
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

        assertEquals(LinkListRow.Header(LinkCategory.SCORES.title(), first = true, key = "header:SCORES"), rows[0])
        assertEquals(
            listOf("shared" to GroupPosition.First, "own" to GroupPosition.Middle, "other" to GroupPosition.Last),
            rows.subList(1, 4).map { (it as LinkListRow.Item).link.id to it.position }
        )
        assertEquals(LinkListRow.Header(UiText.Res(CoreRes.string.links_chats), first = false, key = "header:CHAT"), rows[4])
        assertEquals(GroupPosition.Single, (rows[5] as LinkListRow.Item).position)
        assertEquals(LinkListRow.Header(UiText.Res(Res.string.links_previous), first = false, key = "header:previous"), rows[6])
        val previous = rows[7] as LinkListRow.Item
        assertEquals(GroupPosition.Single, previous.position)
        assertEquals(true, previous.previous)
        assertEquals(true, (rows[1] as LinkListRow.Item).pinned)
        // Votes come from the services connection; a key never depends on a score.
        assertEquals(listOf("link:shared", "link:own", "link:other"), rows.subList(1, 4).map { it.key })
    }

    @Test
    fun noContentHasNoRows() {
        assertEquals(emptyList(), SubjectLinksUiState().linkRows())
    }

    @Test
    fun theCaptionNamesTheSiteWhenTitledTheAudienceThePastYearThePinAndTheOwnReviewState() {
        val titled = subjectLink("a", visibility = LinkVisibility.ALL, isMine = true, status = SubjectLinkStatus.PENDING)
        val untitled = subjectLink("b", visibility = LinkVisibility.ALL, isMine = false, title = null)
            .copy(scope = titled.scope.copy(periodKey = "2025-1"))

        assertEquals(
            UiText.Joined(
                listOf(
                    UiText.Dynamic("example.org"),
                    LinkVisibility.ALL.label(),
                    UiText.Res(Res.string.links_pinned),
                    UiText.Res(Res.string.links_status_pending),
                ),
                ", ",
            ),
            linkMeta(titled, pinned = true, previous = false),
        )
        assertEquals(
            UiText.Joined(listOf(LinkVisibility.ALL.label(), UiText.Dynamic("2025/26")), ", "),
            linkMeta(untitled, pinned = false, previous = true),
        )
        // Others never see a link's review state.
        assertEquals(
            UiText.Joined(listOf(UiText.Dynamic("example.org"), LinkVisibility.ALL.label()), ", "),
            linkMeta(untitled.copy(title = "x", status = SubjectLinkStatus.HIDDEN), pinned = false, previous = false),
        )
    }

    @Test
    fun onlyStatesTheOwnerHasToNoticeGetABadge() {
        assertEquals(UiText.Res(Res.string.links_status_pending), SubjectLinkStatus.PENDING.badge())
        assertEquals(UiText.Res(Res.string.links_status_rejected), SubjectLinkStatus.REJECTED.badge())
        assertEquals(UiText.Res(Res.string.links_status_hidden), SubjectLinkStatus.HIDDEN.badge())
        assertNull(SubjectLinkStatus.PRIVATE.badge())
        assertNull(SubjectLinkStatus.PUBLISHED.badge())
    }
}
