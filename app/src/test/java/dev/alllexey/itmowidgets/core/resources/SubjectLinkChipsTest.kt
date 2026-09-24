package dev.alllexey.itmowidgets.core.resources

import dev.alllexey.itmowidgets.feature.resources.presentation.linksSnapshot
import dev.alllexey.itmowidgets.feature.resources.presentation.subjectLink
import org.junit.Assert.assertEquals
import org.junit.Test

class SubjectLinkChipsTest {
    private val lms = "https://lms.itmo.ru/course/1"

    @Test fun `chips follow pin, LMS, own, saved, flow and best public order`() {
        val snapshot = linksSnapshot(
            mine = listOf(subjectLink("own-notes", LinkCategory.NOTES), subjectLink("own-scores", LinkCategory.SCORES)),
            shared = listOf(
                subjectLink("flow", LinkCategory.QUEUE, LinkVisibility.FLOW, isMine = false),
                subjectLink("pinned", LinkCategory.EXAM, LinkVisibility.ALL, isMine = false),
                subjectLink("saved", LinkCategory.OTHER, LinkVisibility.ALL, isMine = false, isSaved = true),
                subjectLink("public-tasks", LinkCategory.TASKS, LinkVisibility.ALL, isMine = false, score = 5),
            ),
            pinnedId = "pinned",
        )

        val chips = subjectLinkChips(snapshot, lms, limit = 10)

        assertEquals(listOf("pinned", "LMS", "own-scores", "own-notes", "saved", "flow", "public-tasks"), chips.visible.map { it.name() })
        assertEquals(0, chips.moreCount)
    }

    @Test fun `without a pin the LMS page comes first`() {
        val snapshot = linksSnapshot(mine = listOf(subjectLink("own", LinkCategory.SCORES)))

        assertEquals(listOf("LMS", "own"), subjectLinkChips(snapshot, lms).visible.map { it.name() })
    }

    @Test fun `only four chips are shown and the rest of mine and shared is counted`() {
        val snapshot = linksSnapshot(
            mine = LinkCategory.entries.filter { it != LinkCategory.CHAT }.map { subjectLink("own-$it", it) },
            shared = listOf(subjectLink("group", LinkCategory.SCORES, LinkVisibility.FLOW, isMine = false)),
        )

        val chips = subjectLinkChips(snapshot, lms)

        assertEquals(listOf("LMS", "own-SCORES", "own-QUEUE", "own-MATERIALS"), chips.visible.map { it.name() })
        assertEquals(6, chips.moreCount)
    }

    @Test fun `chats never become chips and are not counted`() {
        val snapshot = linksSnapshot(
            mine = listOf(subjectLink("own-chat", LinkCategory.CHAT)),
            shared = listOf(subjectLink("group-chat", LinkCategory.CHAT, LinkVisibility.FLOW, isMine = false),
                subjectLink("public-chat", LinkCategory.CHAT, LinkVisibility.ALL, isMine = false)),
            pinnedId = "own-chat",
        )

        val chips = subjectLinkChips(snapshot, lmsUrl = null)

        assertEquals(emptyList<String>(), chips.visible.map { it.name() })
        assertEquals(0, chips.moreCount)
    }

    @Test fun `one best public link per category not shown yet`() {
        val snapshot = linksSnapshot(
            mine = listOf(subjectLink("own-tasks", LinkCategory.TASKS)),
            shared = listOf(
                subjectLink("tasks", LinkCategory.TASKS, LinkVisibility.ALL, isMine = false, score = 9),
                subjectLink("notes-low", LinkCategory.NOTES, LinkVisibility.ALL, isMine = false, score = 1),
                subjectLink("notes-high", LinkCategory.NOTES, LinkVisibility.ALL, isMine = false, score = 4),
                subjectLink("materials", LinkCategory.MATERIALS, LinkVisibility.ALL, isMine = false, score = 7),
            ),
        )

        val chips = subjectLinkChips(snapshot, lms, limit = 10)

        assertEquals(listOf("LMS", "own-tasks", "notes-high"), chips.visible.map { it.name() })
        assertEquals(3, chips.moreCount)
    }

    private fun SubjectLinkChip.name() = when (this) {
        is SubjectLinkChip.Link -> link.id
        is SubjectLinkChip.Lms -> "LMS"
    }
}
