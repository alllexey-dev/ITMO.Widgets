package dev.alllexey.itmowidgets.feature.resources

import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.SubjectLinksPreviewActivity
import dev.alllexey.itmowidgets.core.debug.MemorySubjectLinksRepository
import dev.alllexey.itmowidgets.core.debug.PreviewAppearance
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs
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
import dev.alllexey.itmowidgets.feature.resources.ui.LinkActionsBottomSheet
import dev.alllexey.itmowidgets.testing.Appearances
import dev.alllexey.itmowidgets.testing.toSubjectLinks
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestUi
import dev.alllexey.itmowidgets.testing.ViewChecks.assertTextFits
import dev.alllexey.itmowidgets.testing.ViewChecks.assertTouchTargets
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubjectLinksVisualTest {

    @Test fun actionsSheetVotesForOthersLinkAndStaysOpen() {
        Appearances.default.forEachIndexed { index, spec ->
            withPreview(spec.toSubjectLinks(), SubjectLinksPreviewActivity.SCREEN_ACTIONS, linkId = "tasks-flow") { scenario, repository ->
                settle()
                fun assertScore(score: Int, vote: Int) = TestUi.eventually(idleBetween = true) {
                    scenario.onActivity { activity ->
                        val sheet = actions(activity)
                        assertEquals(score.toString().replace('-', '−'), sheet.findViewById<TextView>(R.id.score).text.toString())
                        assertEquals(vote > 0, sheet.findViewById<View>(R.id.vote_up).isSelected)
                        assertEquals(vote < 0, sheet.findViewById<View>(R.id.vote_down).isSelected)
                    }
                    assertEquals(vote, repository.peek(SCOPE).shared.first { it.id == "tasks-flow" }.myVote)
                }
                scenario.onActivity { activity ->
                    val sheet = actions(activity)
                    assertTrue(sheet.findViewById<View>(R.id.vote_up).isShown && sheet.findViewById<View>(R.id.vote_down).isShown)
                    // The vote sits at the top, before the first action.
                    assertTrue(sheet.findViewById<View>(R.id.vote_down).bottomOnScreen() <= sheet.findViewById<View>(R.id.action_open).topOnScreen())
                    // Another student's link names its author in a row of its own.
                    assertEquals("Автор: ${AUTHOR.name}", sheet.findViewById<TextView>(R.id.action_author).text.toString())
                    assertTrue(sheet.findViewById<View>(R.id.action_author).isShown)
                    assertTextFits(sheet)
                    assertTouchTargets(sheet)
                }
                assertScore(0, 0)
                scenario.onActivity { actions(it).findViewById<View>(R.id.vote_up).performClick() }
                assertScore(1, 1)
                screenshot("actions-vote-$index")
                scenario.onActivity { actions(it).findViewById<View>(R.id.vote_up).performClick() }
                assertScore(0, 0)
                scenario.onActivity { actions(it).findViewById<View>(R.id.vote_down).performClick() }
                assertScore(-1, -1)
                scenario.onActivity { assertNotNull(it.supportFragmentManager.findFragmentByTag(LinkActionsBottomSheet.TAG)) }
            }
        }
    }

    @Test fun actionsSheetOffersMyScoresOnlyForAGoogleSheet() {
        val sheetLink = link("sheet-scores", LinkCategory.SCORES, SHEET_URL, "Баллы по таблице преподавателя", score = 2)
        val configure: (MemorySubjectLinksRepository) -> Unit = {
            it.snapshots.value = mapOf(SCOPE.key to fixture().let { snapshot -> snapshot.copy(shared = snapshot.shared + sheetLink) })
        }
        Appearances.default.forEach { spec ->
            SubjectLinksPreviewActivity.sheetRequests.clear()
            withPreview(spec.toSubjectLinks(), SubjectLinksPreviewActivity.SCREEN_ACTIONS, linkId = "sheet-scores", configure = configure) { scenario, _ ->
                settle()
                scenario.onActivity { activity ->
                    val sheet = actions(activity)
                    val scores = sheet.findViewById<TextView>(R.id.action_scores)
                    assertTrue(scores.isShown)
                    assertEquals(activity.getString(R.string.sheet_scores_action), scores.text.toString())
                    assertTrue(scores.height >= 48 * activity.resources.displayMetrics.density - 1)
                    assertTrue(sheet.findViewById<View>(R.id.action_open).bottomOnScreen() <= scores.topOnScreen())
                    assertTextFits(sheet)
                    assertTouchTargets(sheet)
                }
                screenshot("links-actions-scores-${spec.name}")
                scenario.onActivity { actions(it).findViewById<View>(R.id.action_scores).performClick() }
                TestUi.eventually(idleBetween = true) {
                    scenario.onActivity { assertNull(it.supportFragmentManager.findFragmentByTag(LinkActionsBottomSheet.TAG)) }
                }
                assertEquals(
                    listOf(SheetScoresArgs(SCOPE.subjectId, SCOPE.subjectName, SCOPE.periodKey, SHEET_URL, SheetScoresArgs.Step.CONNECT)),
                    SubjectLinksPreviewActivity.sheetRequests.toList()
                )
            }
        }
        withPreview(Appearances.light.toSubjectLinks(), SubjectLinksPreviewActivity.SCREEN_ACTIONS, linkId = "tasks-flow", configure = configure) { scenario, _ ->
            settle()
            scenario.onActivity { activity -> assertFalse(actions(activity).findViewById<View>(R.id.action_scores).isShown) }
        }
    }

    @Test fun actionsSheetShowsTheOwnScoreWithoutArrowsAndNothingForAPrivateLink() {
        withPreview(Appearances.light.toSubjectLinks(), SubjectLinksPreviewActivity.SCREEN_ACTIONS, linkId = "own-scores") { scenario, _ ->
            settle()
            scenario.onActivity { activity ->
                val sheet = actions(activity)
                assertTrue(sheet.findViewById<View>(R.id.score).isShown)
                assertEquals("5", sheet.findViewById<TextView>(R.id.score).text.toString())
                assertFalse(sheet.findViewById<View>(R.id.vote_up).isShown || sheet.findViewById<View>(R.id.vote_down).isShown)
            }
            screenshot("actions-own-score")
        }
        withPreview(Appearances.light.toSubjectLinks(), SubjectLinksPreviewActivity.SCREEN_ACTIONS, linkId = "own-other") { scenario, _ ->
            settle()
            scenario.onActivity { activity ->
                assertEquals(View.GONE, actions(activity).findViewById<View>(R.id.vote_pill).visibility)
                assertEquals(View.GONE, actions(activity).findViewById<View>(R.id.action_author).visibility)
            }
        }
    }

    @Test fun actionsSheetHidesArrowsUnderAVoteRestriction() {
        withPreview(Appearances.light.toSubjectLinks(), SubjectLinksPreviewActivity.SCREEN_ACTIONS, linkId = "tasks-flow", configure = {
            it.snapshots.value = mapOf(SCOPE.key to fixture())
            it.restrictions.value = listOf(UserRestriction("r", RestrictionCapability.VOTE, "Правила", null))
        }) { scenario, _ ->
            settle()
            scenario.onActivity { activity ->
                val sheet = actions(activity)
                assertTrue(sheet.findViewById<View>(R.id.score).isShown)
                assertFalse(sheet.findViewById<View>(R.id.vote_up).isShown || sheet.findViewById<View>(R.id.vote_down).isShown)
            }
        }
    }

    @Test fun ownRejectedLinkShowsTheReasonAndOwnActions() {
        Appearances.default.forEachIndexed { index, spec ->
            withPreview(spec.toSubjectLinks(), SubjectLinksPreviewActivity.SCREEN_ACTIONS, linkId = "own-rejected") { scenario, _ ->
                settle()
                scenario.onActivity { activity ->
                    val sheet = actions(activity)
                    assertEquals("Причина: Ссылка ведёт на другой предмет", sheet.findViewById<TextView>(R.id.review_note).text.toString())
                    assertTrue(sheet.findViewById<TextView>(R.id.meta).text.contains("отклонена"))
                    assertEquals(listOf(R.id.action_open, R.id.action_pin, R.id.action_edit, R.id.action_delete), visibleActions(sheet))
                    assertTrue(sheet.findViewById<View>(R.id.score).isShown)
                    assertFalse(sheet.findViewById<View>(R.id.vote_up).isShown)
                    assertTextFits(sheet)
                    assertTouchTargets(sheet)
                }
                screenshot("actions-own-$index")
            }
        }
    }

    @Test fun othersLinkOffersPinningAndReporting() {
        withPreview(Appearances.light.toSubjectLinks(), SubjectLinksPreviewActivity.SCREEN_ACTIONS, linkId = "materials-all") { scenario, repository ->
            settle()
            scenario.onActivity { activity ->
                val sheet = actions(activity)
                assertEquals(View.GONE, sheet.findViewById<View>(R.id.review_note).visibility)
                assertEquals(listOf(R.id.action_open, R.id.action_pin, R.id.action_report), visibleActions(sheet))
                assertEquals(activity.getString(R.string.links_unpin), sheet.findViewById<TextView>(R.id.action_pin).text.toString())
                sheet.findViewById<View>(R.id.action_pin).performClick()
            }
            settle()
            assertEquals(null, repository.peek(SCOPE).pinnedId)
            scenario.onActivity { assertEquals(null, it.supportFragmentManager.findFragmentByTag(LinkActionsBottomSheet.TAG)) }
        }
    }

    private fun withPreview(
        appearance: PreviewAppearance,
        screen: String,
        linkId: String? = null,
        configure: (MemorySubjectLinksRepository) -> Unit = { it.snapshots.value = mapOf(SCOPE.key to fixture()) },
        block: (ActivityScenario<SubjectLinksPreviewActivity>, MemorySubjectLinksRepository) -> Unit,
    ) {
        val repository = MemorySubjectLinksRepository().apply { servicesEnabled = true }.also(configure)
        SubjectLinksPreviewActivity.appearance = appearance
        SubjectLinksPreviewActivity.repository = repository
        val intent = Intent(ApplicationProvider.getApplicationContext(), SubjectLinksPreviewActivity::class.java)
            .putExtra(SubjectLinksPreviewActivity.EXTRA_SCREEN, screen)
            .putExtra(SubjectLinksPreviewActivity.EXTRA_LINK_ID, linkId)
        try {
            ActivityScenario.launch<SubjectLinksPreviewActivity>(intent).use { block(it, repository) }
        } finally {
            SubjectLinksPreviewActivity.appearance = PreviewAppearance()
            SubjectLinksPreviewActivity.repository = MemorySubjectLinksRepository()
        }
    }

    private fun sheetView(activity: SubjectLinksPreviewActivity, tag: String): View =
        checkNotNull((activity.supportFragmentManager.findFragmentByTag(tag) as DialogFragment).dialog).window!!.decorView

    private fun actions(activity: SubjectLinksPreviewActivity) = sheetView(activity, LinkActionsBottomSheet.TAG)

    private fun visibleActions(sheet: View): List<Int> =
        listOf(R.id.action_open, R.id.action_pin, R.id.action_edit, R.id.action_delete, R.id.action_report)
            .filter { sheet.findViewById<View>(it).visibility == View.VISIBLE }

    private fun View.topOnScreen(): Int = IntArray(2).also(::getLocationOnScreen)[1]

    private fun View.bottomOnScreen(): Int = topOnScreen() + height

    private fun settle() = TestUi.settle(600)

    private fun screenshot(name: String) = Screenshots.capture("links-screenshots", name)

    private companion object {
        val SCOPE = ResourceScope(42L, "Математический анализ", "2026-1")
        const val SHEET_URL = "https://docs.google.com/spreadsheets/d/1SyntheticSheetForVisualTests_0123456/edit"
        val PAST = ResourceScope(42L, "Математический анализ", "2025-1")
        val LECTURE_FLOW = LinkAudience(7101, "ФИЗ ПИИКТ 3", typeId = 1, depth = 1)
        val PRACTICE_FLOW = LinkAudience(7102, "ФИЗ ПИИКТ 3.2", typeId = 3, depth = 2)
        val LAB_FLOW = LinkAudience(7103, "ФИЗ ПИИКТ 3.2.1", typeId = 2, depth = 3)
        val NOW: Instant = Instant.parse("2026-09-22T09:00:00Z")
        val AUTHOR = UserSummary(100001, "Синтетический Автор", null, listOf(UserGroup("P3118", 2, "ФПИиКТ")), UserSharing(false, false))

        fun link(
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

        fun fixture() = SubjectLinksSnapshot(
            mine = listOf(
                link("own-scores", LinkCategory.SCORES, "https://docs.google.com/spreadsheets/d/own", "Баллы нашей группы",
                    LinkVisibility.FLOW, mine = true, score = 5, flow = PRACTICE_FLOW),
                link("own-other", LinkCategory.OTHER, "https://example.org/cheatsheet", null, LinkVisibility.PRIVATE, mine = true,
                    status = SubjectLinkStatus.PRIVATE),
                link("own-rejected", LinkCategory.NOTES, "https://www.notion.so/synthetic",
                    "Полный конспект лекций по математическому анализу за весь семестр с разобранными примерами",
                    mine = true, status = SubjectLinkStatus.REJECTED, reviewNote = "Ссылка ведёт на другой предмет"),
            ),
            shared = listOf(
                link("scores-all", LinkCategory.SCORES, "https://docs.google.com/spreadsheets/d/all", "Баллы всего потока", score = 8),
                link("scores-old", LinkCategory.SCORES, "https://docs.google.com/spreadsheets/d/old", "Старая таблица", score = 1),
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
    }
}
