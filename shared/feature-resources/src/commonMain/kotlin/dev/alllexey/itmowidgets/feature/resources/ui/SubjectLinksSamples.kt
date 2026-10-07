package dev.alllexey.itmowidgets.feature.resources.ui

import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.resources.LinkAudience
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.RestrictionCapability
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.resources.SubjectLinkStatus
import dev.alllexey.itmowidgets.core.resources.SubjectLinksSnapshot
import dev.alllexey.itmowidgets.core.resources.UserRestriction
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.feature.resources.presentation.SubjectLinksUiState
import dev.alllexey.itmowidgets.feature.resources.presentation.linkSections
import kotlin.time.Instant

/**
 * Synthetic links of one subject period for the previews and host tests, the data of the LX-1c references: every
 * category, chats, past years, own private, shared, pending and rejected links, three nested flows and another
 * student's Google Sheet.
 */
internal object SubjectLinksSamples {
    val scope = ResourceScope(42L, "Математический анализ", "2026-1")
    private val past = scope.copy(periodKey = "2025-1")
    private val lectureFlow = LinkAudience(7101, "ФИЗ ПИИКТ 3", typeId = 1, depth = 1)
    private val practiceFlow = LinkAudience(7102, "ФИЗ ПИИКТ 3.2", typeId = 3, depth = 2)
    private val labFlow = LinkAudience(7103, "ФИЗ ПИИКТ 3.2.1", typeId = 2, depth = 3)
    private val now = Instant.parse("2026-09-22T09:00:00Z")
    private val author = UserSummary(
        100001,
        "Синтетический Автор",
        null,
        listOf(UserGroup("P3118", 2, "ФПИиКТ")),
        UserSharing(sport = false, schedule = false),
    )

    const val LONG_TITLE = "Полный конспект лекций по математическому анализу за весь семестр с разобранными примерами"

    val snapshot = SubjectLinksSnapshot(
        mine = listOf(
            link("own-scores", LinkCategory.SCORES, "https://docs.google.com/spreadsheets/d/own", "Баллы нашей группы",
                LinkVisibility.FLOW, mine = true, score = 5, flow = practiceFlow),
            link("own-other", LinkCategory.OTHER, "https://example.org/cheatsheet", null, LinkVisibility.PRIVATE, mine = true,
                status = SubjectLinkStatus.PRIVATE),
            link("own-pending", LinkCategory.TASKS, "https://github.com/synthetic/solutions", "Разборы домашних заданий",
                mine = true, status = SubjectLinkStatus.PENDING),
            link("own-rejected", LinkCategory.NOTES, "https://www.notion.so/synthetic", LONG_TITLE,
                mine = true, status = SubjectLinkStatus.REJECTED, reviewNote = "Ссылка ведёт на другой предмет"),
        ),
        shared = listOf(
            link("scores-all", LinkCategory.SCORES, "https://docs.google.com/spreadsheets/d/all", "Баллы всего потока", score = 8),
            link("others-sheet", LinkCategory.SCORES,
                "https://docs.google.com/spreadsheets/d/1SyntheticSheetForVisualTests_0123456/edit",
                "Баллы по таблице преподавателя", score = 2, myVote = 1),
            link("queue-group", LinkCategory.QUEUE, "https://docs.google.com/forms/d/queue", "Очередь на защиту",
                LinkVisibility.FLOW, score = 4, flow = practiceFlow),
            link("materials-all", LinkCategory.MATERIALS, "https://drive.google.com/synthetic", "Материалы лектора", score = 12),
            link("tasks-flow", LinkCategory.TASKS, "https://github.com/synthetic/tasks", "Задания потока", LinkVisibility.FLOW,
                flow = lectureFlow),
            link("recordings-all", LinkCategory.RECORDINGS, "https://youtube.com/synthetic", "Записи лекций 2026",
                score = -2, myVote = -1),
            link("exam-all", LinkCategory.EXAM, "https://example.org/exam", "Билеты к экзамену", score = 3),
            link("chat-group", LinkCategory.CHAT, "https://t.me/synthetic_group", "Чат группы", LinkVisibility.FLOW,
                flow = practiceFlow),
            link("chat-flow", LinkCategory.CHAT, "https://t.me/synthetic_flow", "Чат потока", LinkVisibility.FLOW,
                flow = lectureFlow),
        ),
        previous = listOf(
            link("past-materials", LinkCategory.MATERIALS, "https://drive.google.com/past", "Материалы прошлого года",
                score = 20, scope = past),
            link("past-notes", LinkCategory.NOTES, "https://synthetic.notion.site/notes", null, score = 7, scope = past),
        ),
        pinnedId = "materials-all",
        audiences = listOf(lectureFlow, practiceFlow, labFlow),
        premoderation = true,
        servicesEnabled = true,
    )

    /** Every review state an owner sees: on review, rejected and hidden by moderation, next to a published one. */
    val ownStates = SubjectLinksSnapshot(
        mine = listOf(
            link("own-published", LinkCategory.MATERIALS, "https://drive.google.com/own", "Мои материалы", mine = true,
                score = 3),
            link("own-pending", LinkCategory.MATERIALS, "https://github.com/synthetic/solutions", "Разборы домашних заданий",
                mine = true, status = SubjectLinkStatus.PENDING),
            link("own-rejected", LinkCategory.MATERIALS, "https://www.notion.so/synthetic", "Конспект семинаров",
                mine = true, status = SubjectLinkStatus.REJECTED, reviewNote = "Ссылка ведёт на другой предмет"),
            link("own-hidden", LinkCategory.MATERIALS, "https://example.org/hidden", "Скрытая подборка", mine = true,
                status = SubjectLinkStatus.HIDDEN, reviewNote = "Жалобы студентов"),
        ),
        shared = emptyList(),
        previous = emptyList(),
        pinnedId = null,
        audiences = listOf(lectureFlow),
        premoderation = true,
        servicesEnabled = true,
    )

    val voteRestriction = UserRestriction("vote", RestrictionCapability.VOTE, "Правила", null)

    val content = state(snapshot)
    val restricted = state(snapshot, listOf(voteRestriction))

    /** Without the custom-services opt-in: the same links, scores without arrows. */
    val offline = state(snapshot.copy(servicesEnabled = false))
    val own = state(ownStates)
    val loading = SubjectLinksUiState()
    val empty = state(SubjectLinksSnapshot(emptyList(), emptyList(), emptyList(), null, emptyList(), true, true))
    val error = SubjectLinksUiState(error = AppError.Network)

    fun state(snapshot: SubjectLinksSnapshot, restrictions: List<UserRestriction> = emptyList()) =
        SubjectLinksUiState(snapshot, linkSections(snapshot), restrictions)

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
        scope: ResourceScope = this.scope,
    ): SubjectLink = SubjectLink(id, scope, category, url, title, visibility, flow?.flowId, flow?.label, status, reviewNote,
        score, myVote, isMine = mine, reportedByMe = false, author = author.takeUnless { mine }, updatedAt = now)
}
