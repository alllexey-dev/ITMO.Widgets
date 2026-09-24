package dev.alllexey.itmowidgets.core.resources

import dev.alllexey.itmowidgets.feature.resources.presentation.linkTime
import dev.alllexey.itmowidgets.feature.resources.presentation.linksSnapshot
import dev.alllexey.itmowidgets.feature.resources.presentation.subjectLink
import org.junit.Assert.assertEquals
import org.junit.Test

class SubjectLinkChipsTest {
    private val lms = "https://lms.itmo.ru/course/1"

    @Test fun `chips follow the pin and the LMS page, then own and others' links by score`() {
        val snapshot = linksSnapshot(
            mine = listOf(subjectLink("own-notes", LinkCategory.NOTES, score = 1), subjectLink("own-scores", LinkCategory.SCORES)),
            shared = listOf(
                subjectLink("flow", LinkCategory.QUEUE, LinkVisibility.FLOW, isMine = false, score = 3),
                subjectLink("pinned", LinkCategory.EXAM, LinkVisibility.ALL, isMine = false),
                subjectLink("saved", LinkCategory.OTHER, LinkVisibility.ALL, isMine = false, isSaved = true, score = 2),
                subjectLink("public-tasks", LinkCategory.TASKS, LinkVisibility.ALL, isMine = false, score = 5),
            ),
            pinnedId = "pinned",
        )

        val chips = subjectLinkChips(snapshot, lms, limit = 10)

        assertEquals(listOf("pinned", "LMS", "public-tasks", "flow", "saved", "own-notes", "own-scores"), chips.visible.map { it.name() })
        assertEquals(0, chips.moreCount)
    }

    @Test fun `of equal scores the newer link comes first whoever owns it`() {
        val snapshot = linksSnapshot(
            mine = listOf(subjectLink("own-old", LinkCategory.SCORES, score = 2)),
            shared = listOf(
                subjectLink("shared-new", LinkCategory.TASKS, LinkVisibility.ALL, isMine = false, score = 2).copy(updatedAt = linkTime.plusDays(1)),
                subjectLink("shared-older", LinkCategory.NOTES, LinkVisibility.ALL, isMine = false, score = 2).copy(updatedAt = linkTime.minusDays(1)),
            ),
        )

        assertEquals(listOf("shared-new", "own-old", "shared-older"), subjectLinkChips(snapshot, lmsUrl = null).visible.map { it.name() })
    }

    @Test fun `without a pin the LMS page comes first`() {
        val snapshot = linksSnapshot(mine = listOf(subjectLink("own", LinkCategory.SCORES)))

        assertEquals(listOf("LMS", "own"), subjectLinkChips(snapshot, lms).visible.map { it.name() })
    }

    @Test fun `only four chips are shown and the rest of mine and shared is counted`() {
        val snapshot = linksSnapshot(
            mine = LinkCategory.entries.filter { it != LinkCategory.CHAT }.map { subjectLink("own-$it", it) },
            shared = listOf(subjectLink("group", LinkCategory.SCORES, LinkVisibility.FLOW, isMine = false, score = 1)),
        )

        val chips = subjectLinkChips(snapshot, lms)

        assertEquals(listOf("LMS", "group", "own-SCORES", "own-QUEUE"), chips.visible.map { it.name() })
        assertEquals(6, chips.moreCount)
    }

    @Test fun `a link is shown and counted once`() {
        val saved = subjectLink("saved", LinkCategory.TASKS, LinkVisibility.ALL, isMine = false, isSaved = true, score = 4)
        val snapshot = linksSnapshot(
            mine = listOf(saved, subjectLink("pinned", LinkCategory.SCORES, score = 9)),
            shared = listOf(saved, subjectLink("other", LinkCategory.NOTES, LinkVisibility.ALL, isMine = false)),
            pinnedId = "pinned",
        )

        val chips = subjectLinkChips(snapshot, lmsUrl = null, limit = 2)

        assertEquals(listOf("pinned", "saved"), chips.visible.map { it.name() })
        assertEquals(1, chips.moreCount)
    }

    @Test fun `chats never become chips and are not counted`() {
        val snapshot = linksSnapshot(
            mine = listOf(subjectLink("own-chat", LinkCategory.CHAT)),
            shared = listOf(subjectLink("group-chat", LinkCategory.CHAT, LinkVisibility.FLOW, isMine = false),
                subjectLink("public-chat", LinkCategory.CHAT, LinkVisibility.ALL, isMine = false, score = 10)),
            pinnedId = "own-chat",
        )

        val chips = subjectLinkChips(snapshot, lmsUrl = null)

        assertEquals(emptyList<String>(), chips.visible.map { it.name() })
        assertEquals(0, chips.moreCount)
    }

    private fun SubjectLinkChip.name() = when (this) {
        is SubjectLinkChip.Link -> link.id
        is SubjectLinkChip.Lms -> "LMS"
    }
}
