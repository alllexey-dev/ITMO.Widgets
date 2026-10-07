package dev.alllexey.itmowidgets.feature.resources

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.core.debug.MemorySubjectLinksRepository
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.resources.SubjectLinkStatus
import dev.alllexey.itmowidgets.core.resources.SubjectLinksRepository
import dev.alllexey.itmowidgets.core.resources.SubjectLinksSnapshot
import dev.alllexey.itmowidgets.di.bridge.KoinStarter
import kotlin.time.Instant
import org.koin.dsl.module

/**
 * Synthetic links of one subject period for the links hosts' JVM tests in `:app`: an own link, an own private one and
 * another student's Google Sheet. What the sheets draw is tested in `:shared:feature-resources` over its own samples.
 */
internal object LinksHostFixtures {
    val ARGS = SubjectLinksArgs(42L, "Математический анализ", "2026-1")
    val SCOPE = ResourceScope(ARGS.subjectId, ARGS.subjectName, ARGS.periodKey)
    private val NOW = Instant.parse("2026-09-22T09:00:00Z")
    private val AUTHOR = UserSummary(100001, "Синтетический Автор", null, listOf(UserGroup("P3118", 2, "ФПИиКТ")),
        UserSharing(sport = false, schedule = false))

    fun fixture() = SubjectLinksSnapshot(
        mine = listOf(
            link("own-scores", LinkCategory.SCORES, "https://docs.google.com/spreadsheets/d/own", "Баллы нашей группы",
                LinkVisibility.ALL, mine = true, score = 5),
            link("own-other", LinkCategory.OTHER, "https://example.org/cheatsheet", null, LinkVisibility.PRIVATE,
                mine = true, status = SubjectLinkStatus.PRIVATE),
        ),
        shared = listOf(
            link("others-sheet", LinkCategory.SCORES,
                "https://docs.google.com/spreadsheets/d/1SyntheticSheetForVisualTests_0123456/edit",
                "Баллы по таблице преподавателя", LinkVisibility.ALL, score = 2, myVote = 1),
        ),
        previous = emptyList(),
        pinnedId = null,
        audiences = emptyList(),
        premoderation = true,
        servicesEnabled = true,
    )

    /** A repository with [fixture] and the connection, handed to Koin for every links view model of the test. */
    fun install(): MemorySubjectLinksRepository {
        val repository = MemorySubjectLinksRepository().apply {
            servicesEnabled = true
            snapshots.value = mapOf(SCOPE.key to fixture())
        }
        KoinStarter.ensureStarted(ApplicationProvider.getApplicationContext<Context>())
            .loadModules(listOf(module { factory<SubjectLinksRepository> { repository } }), allowOverride = true)
        return repository
    }

    private fun link(
        id: String,
        category: LinkCategory,
        url: String,
        title: String?,
        visibility: LinkVisibility,
        mine: Boolean = false,
        score: Int = 0,
        myVote: Int = 0,
        status: SubjectLinkStatus = SubjectLinkStatus.PUBLISHED,
    ) = SubjectLink(id, SCOPE, category, url, title, visibility, null, null, status, null, score, myVote,
        isMine = mine, reportedByMe = false, author = AUTHOR.takeUnless { mine }, updatedAt = NOW)
}
