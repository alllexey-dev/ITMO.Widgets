package dev.alllexey.itmowidgets.feature.resources.reference

import dev.alllexey.itmowidgets.app.SubjectLinksPreviewActivity
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.resources.LinkAudience
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.resources.SubjectLinkStatus
import dev.alllexey.itmowidgets.core.resources.SubjectLinksSnapshot
import kotlin.time.Instant

/**
 * Synthetic links of one subject period, the cases of `SubjectLinksVisualTest`: every category, chats, past years,
 * own private, shared, pending and rejected links, three nested flows and another student's Google Sheet.
 */
internal object SubjectLinksReferenceFixtures {
    val SCOPE = SubjectLinksPreviewActivity.ARGS.let { ResourceScope(it.subjectId, it.subjectName, it.periodKey) }
    const val SHEET_URL = "https://docs.google.com/spreadsheets/d/1SyntheticSheetForVisualTests_0123456/edit"
    private val PAST = SCOPE.copy(periodKey = "2025-1")
    private val LECTURE_FLOW = LinkAudience(7101, "ФИЗ ПИИКТ 3", typeId = 1, depth = 1)
    private val PRACTICE_FLOW = LinkAudience(7102, "ФИЗ ПИИКТ 3.2", typeId = 3, depth = 2)
    private val LAB_FLOW = LinkAudience(7103, "ФИЗ ПИИКТ 3.2.1", typeId = 2, depth = 3)
    private val NOW = Instant.parse("2026-09-22T09:00:00Z")
    private val AUTHOR = UserSummary(100001, "Синтетический Автор", null, listOf(UserGroup("P3118", 2, "ФПИиКТ")),
        UserSharing(sport = false, schedule = false))

    fun fixture() = SubjectLinksSnapshot(
        mine = listOf(
            link("own-scores", LinkCategory.SCORES, "https://docs.google.com/spreadsheets/d/own", "Баллы нашей группы",
                LinkVisibility.FLOW, mine = true, score = 5, flow = PRACTICE_FLOW),
            link("own-other", LinkCategory.OTHER, "https://example.org/cheatsheet", null, LinkVisibility.PRIVATE, mine = true,
                status = SubjectLinkStatus.PRIVATE),
            link("own-pending", LinkCategory.TASKS, "https://github.com/synthetic/solutions", "Разборы домашних заданий",
                mine = true, status = SubjectLinkStatus.PENDING),
            link("own-rejected", LinkCategory.NOTES, "https://www.notion.so/synthetic",
                "Полный конспект лекций по математическому анализу за весь семестр с разобранными примерами",
                mine = true, status = SubjectLinkStatus.REJECTED, reviewNote = "Ссылка ведёт на другой предмет"),
        ),
        shared = listOf(
            link("scores-all", LinkCategory.SCORES, "https://docs.google.com/spreadsheets/d/all", "Баллы всего потока", score = 8),
            link("others-sheet", LinkCategory.SCORES, SHEET_URL, "Баллы по таблице преподавателя", score = 2, myVote = 1),
            link("queue-group", LinkCategory.QUEUE, "https://docs.google.com/forms/d/queue", "Очередь на защиту",
                LinkVisibility.FLOW, score = 4, flow = PRACTICE_FLOW),
            link("materials-all", LinkCategory.MATERIALS, "https://drive.google.com/synthetic", "Материалы лектора", score = 12),
            link("tasks-flow", LinkCategory.TASKS, "https://github.com/synthetic/tasks", "Задания потока", LinkVisibility.FLOW,
                flow = LECTURE_FLOW),
            link("recordings-all", LinkCategory.RECORDINGS, "https://youtube.com/synthetic", "Записи лекций 2026", score = -2, myVote = -1),
            link("exam-all", LinkCategory.EXAM, "https://example.org/exam", "Билеты к экзамену", score = 3),
            link("chat-group", LinkCategory.CHAT, "https://t.me/synthetic_group", "Чат группы", LinkVisibility.FLOW,
                flow = PRACTICE_FLOW),
            link("chat-flow", LinkCategory.CHAT, "https://t.me/synthetic_flow", "Чат потока", LinkVisibility.FLOW, flow = LECTURE_FLOW),
        ),
        previous = listOf(
            link("past-materials", LinkCategory.MATERIALS, "https://drive.google.com/past", "Материалы прошлого года", score = 20, scope = PAST),
            link("past-notes", LinkCategory.NOTES, "https://synthetic.notion.site/notes", null, score = 7, scope = PAST),
        ),
        pinnedId = "materials-all",
        audiences = listOf(LECTURE_FLOW, PRACTICE_FLOW, LAB_FLOW),
        premoderation = true,
        servicesEnabled = true,
    )

    private fun link(
        id: String,
        category: LinkCategory,
        url: String,
        title: String?,
        visibility: LinkVisibility = LinkVisibility.ALL,
        mine: Boolean = false,
        score: Int = 0,
        myVote: Int = 0,
        status: SubjectLinkStatus = SubjectLinkStatus.PUBLISHED,
        reviewNote: String? = null,
        flow: LinkAudience? = null,
        scope: ResourceScope = SCOPE,
    ) = SubjectLink(id, scope, category, url, title, visibility, flow?.flowId, flow?.label, status, reviewNote, score, myVote,
        isMine = mine, reportedByMe = false, author = AUTHOR.takeUnless { mine }, updatedAt = NOW)
}
