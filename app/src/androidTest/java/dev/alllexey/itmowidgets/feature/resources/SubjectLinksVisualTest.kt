package dev.alllexey.itmowidgets.feature.resources

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.chip.ChipGroup
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.textfield.TextInputLayout
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.SubjectLinksPreviewActivity
import dev.alllexey.itmowidgets.core.debug.MemorySubjectLinksRepository
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
import dev.alllexey.itmowidgets.feature.resources.ui.LinkActionsBottomSheet
import dev.alllexey.itmowidgets.feature.resources.ui.LinkEditorBottomSheet
import dev.alllexey.itmowidgets.feature.resources.ui.SubjectLinksBottomSheet
import dev.alllexey.itmowidgets.testing.Appearances
import dev.alllexey.itmowidgets.testing.Appearances.toSubjectLinks
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestUi
import dev.alllexey.itmowidgets.testing.ViewChecks.assertTextFits
import dev.alllexey.itmowidgets.testing.ViewChecks.assertTouchTargets
import dev.alllexey.itmowidgets.testing.ViewChecks.descendants
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubjectLinksVisualTest {

    @Test fun sheetListsEveryCategoryThenChatsThenPastYears() {
        Appearances.default.forEachIndexed { index, spec ->
            withPreview(spec.toSubjectLinks(), SubjectLinksPreviewActivity.SCREEN_LINKS) { scenario, _ ->
                settle()
                val headers = mutableListOf<String>()
                visitRows(scenario) { row ->
                    assertTextFits(row)
                    if (row is TextView) headers += row.text.toString() else assertTouchTargets(row)
                }
                assertEquals(listOf("Таблица баллов", "Очередь на сдачу", "Материалы курса", "Задания", "Записи лекций",
                    "Конспекты", "К экзамену", "Другое", "Чаты", "С прошлых лет"), headers)
                screenshot("links-$index")
            }
        }
    }

    @Test fun arrowsChangeTheScoreAndTheSameArrowTakesTheVoteBack() {
        withPreview(Appearances.light.toSubjectLinks(), SubjectLinksPreviewActivity.SCREEN_LINKS) { scenario, repository ->
            settle()
            val position = positionOf(scenario, "Задания потока")
            fun row(activity: SubjectLinksPreviewActivity): View =
                checkNotNull(sheetList(activity).findViewHolderForAdapterPosition(position)).itemView
            fun assertScore(score: Int, vote: Int) = TestUi.eventually(idleBetween = true) {
                scenario.onActivity { activity ->
                    val view = row(activity)
                    assertEquals(score.toString(), view.findViewById<TextView>(R.id.score).text.toString())
                    assertEquals(vote > 0, view.findViewById<View>(R.id.vote_up).isSelected)
                    assertEquals(vote < 0, view.findViewById<View>(R.id.vote_down).isSelected)
                }
                assertEquals(vote, repository.peek(SCOPE).shared.first { it.id == "tasks-flow" }.myVote)
            }

            assertScore(0, 0)
            scenario.onActivity { row(it).findViewById<View>(R.id.vote_up).performClick() }
            assertScore(1, 1)
            screenshot("vote-up")
            scenario.onActivity { row(it).findViewById<View>(R.id.vote_up).performClick() }
            assertScore(0, 0)
            scenario.onActivity { row(it).findViewById<View>(R.id.vote_down).performClick() }
            assertScore(-1, -1)
        }
    }

    @Test fun editorListsEveryNestedFlowWithItsKindOfClasses() {
        Appearances.default.forEachIndexed { index, spec ->
            withPreview(spec.toSubjectLinks(), SubjectLinksPreviewActivity.SCREEN_EDITOR, linkId = "own-scores") { scenario, repository ->
                settle()
                scenario.onActivity { activity ->
                    val sheet = editor(activity)
                    assertEquals(listOf("Все\nПосле проверки", "ФИЗ ПИИКТ 3\nЛекция", "ФИЗ ПИИКТ 3.2\nПрактика",
                        "ФИЗ ПИИКТ 3.2.1\nЛабораторная", "Только я"), audienceRows(sheet))
                    assertEquals("ФИЗ ПИИКТ 3.2", checkedAudience(sheet))
                    assertEquals(View.GONE, sheet.findViewById<View>(R.id.connection_hint).visibility)
                    assertTextFits(sheet)
                    assertTouchTargets(sheet.findViewById(R.id.visibility))
                    radioRows(sheet).first { it.text.startsWith("ФИЗ ПИИКТ 3.2.1") }.performClick()
                }
                settle()
                scenario.onActivity { assertEquals("ФИЗ ПИИКТ 3.2.1", checkedAudience(editor(it))) }
                screenshot("editor-flows-$index")
                scenario.onActivity { editor(it).findViewById<View>(R.id.save_button).performClick() }
                settle()
                val saved = repository.peek(SCOPE).mine.first { it.id == "own-scores" }
                assertEquals(LinkVisibility.FLOW, saved.visibility)
                assertEquals(LAB_FLOW.flowId, saved.flowId)
                assertEquals("ФИЗ ПИИКТ 3.2.1", saved.audienceLabel)
            }
        }
    }

    @Test fun editorPastesAGuessedLinkAndOffersOnlyAvailableAudiences() {
        val pasted = "https://docs.google.com/spreadsheets/d/synthetic"
        withPreview(Appearances.light.toSubjectLinks(), SubjectLinksPreviewActivity.SCREEN_EDITOR, clipboard = pasted,
            configure = { it.snapshots.value = mapOf(SCOPE.key to fixture().copy(audiences = listOf(LECTURE_FLOW))) }) { scenario, repository ->
            settle()
            TestUi.eventually(idleBetween = true) {
                scenario.onActivity { activity ->
                    val sheet = editor(activity)
                    assertEquals(pasted, sheet.findViewById<TextView>(R.id.url).text.toString())
                    assertEquals(R.id.category_scores, sheet.findViewById<ChipGroup>(R.id.categories).checkedChipId)
                    assertEquals("Таблица баллов", sheet.findViewById<TextInputLayout>(R.id.name_layout).hint.toString())
                }
            }
            scenario.onActivity { activity ->
                val sheet = editor(activity)
                assertEquals(listOf("Все\nПосле проверки", "ФИЗ ПИИКТ 3\nЛекция", "Только я"), audienceRows(sheet))
                assertEquals("Только я", checkedAudience(sheet))
                radioRows(sheet)[1].performClick()
            }
            settle()
            scenario.onActivity { activity ->
                val sheet = editor(activity)
                assertEquals("ФИЗ ПИИКТ 3", checkedAudience(sheet))
                assertTextFits(sheet)
                assertTouchTargets(sheet.findViewById(R.id.visibility))
            }
            screenshot("editor-guessed")

            // The site keeps suggesting until a chip is picked by hand.
            scenario.onActivity { editor(it).findViewById<TextView>(R.id.url).text = "https://youtu.be/synthetic" }
            settle()
            scenario.onActivity { activity ->
                val sheet = editor(activity)
                assertEquals(R.id.category_recordings, sheet.findViewById<ChipGroup>(R.id.categories).checkedChipId)
                sheet.findViewById<View>(R.id.category_exam).performClick()
                sheet.findViewById<TextView>(R.id.url).text = "https://github.com/synthetic"
            }
            settle()
            scenario.onActivity { assertEquals(R.id.category_exam, editor(it).findViewById<ChipGroup>(R.id.categories).checkedChipId) }

            repository.snapshots.value = mapOf(SCOPE.key to fixture().copy(audiences = emptyList(), premoderation = false))
            settle()
            scenario.onActivity { activity ->
                val sheet = editor(activity)
                // The chosen flow is gone from the schedule, so the choice falls back to only me.
                assertEquals(listOf("Все", "Только я"), audienceRows(sheet))
                assertEquals("Только я", checkedAudience(sheet))
                radioRows(sheet).first().performClick()
            }
            settle()
            scenario.onActivity { activity ->
                val sheet = editor(activity)
                assertEquals("Все", checkedAudience(sheet))
                sheet.findViewById<View>(R.id.save_button).performClick()
            }
            settle()
            val saved = repository.peek(SCOPE).mine.first { it.url == "https://github.com/synthetic" }
            assertEquals(LinkCategory.EXAM, saved.category)
            assertEquals(LinkVisibility.ALL, saved.visibility)
            scenario.onActivity { assertEquals(null, it.supportFragmentManager.findFragmentByTag(LinkEditorBottomSheet.TAG)) }
        }
    }

    @Test fun editorWithoutTheConnectionKeepsTheLinkPrivate() {
        withPreview(Appearances.light.toSubjectLinks(), SubjectLinksPreviewActivity.SCREEN_EDITOR, configure = {
            it.servicesEnabled = false
            it.snapshots.value = mapOf(SCOPE.key to fixture().copy(shared = emptyList(), previous = emptyList(),
                audiences = emptyList(), servicesEnabled = false))
        }) { scenario, repository ->
            settle()
            scenario.onActivity { activity ->
                val sheet = editor(activity)
                assertEquals(listOf("Только я"), audienceRows(sheet))
                assertEquals("Только я", checkedAudience(sheet))
                val hint = sheet.findViewById<TextView>(R.id.connection_hint)
                assertEquals(View.VISIBLE, hint.visibility)
                assertEquals(activity.getString(R.string.links_connection_required), hint.text.toString())
                assertFalse(sheet.findViewById<View>(R.id.save_button).isEnabled)
                sheet.findViewById<TextView>(R.id.url).text = "https://t.me/synthetic_chat"
            }
            settle()
            scenario.onActivity { activity ->
                val sheet = editor(activity)
                assertEquals(R.id.category_chat, sheet.findViewById<ChipGroup>(R.id.categories).checkedChipId)
                assertTextFits(sheet)
            }
            screenshot("editor-offline")
            scenario.onActivity { editor(it).findViewById<View>(R.id.save_button).performClick() }
            settle()
            val saved = repository.peek(SCOPE).mine.first { it.url == "https://t.me/synthetic_chat" }
            assertEquals(LinkVisibility.PRIVATE, saved.visibility)
            assertTrue(saved.local)
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
                    assertTextFits(sheet)
                    assertTouchTargets(sheet)
                }
                screenshot("actions-own-$index")
            }
        }
    }

    @Test fun othersLinkOffersAddingPinningAndReporting() {
        withPreview(Appearances.light.toSubjectLinks(), SubjectLinksPreviewActivity.SCREEN_ACTIONS, linkId = "materials-all") { scenario, repository ->
            settle()
            scenario.onActivity { activity ->
                val sheet = actions(activity)
                assertEquals(View.GONE, sheet.findViewById<View>(R.id.review_note).visibility)
                assertEquals(listOf(R.id.action_open, R.id.action_save, R.id.action_pin, R.id.action_report), visibleActions(sheet))
                assertEquals("Добавить к себе", sheet.findViewById<TextView>(R.id.action_save).text.toString())
                sheet.findViewById<View>(R.id.action_save).performClick()
            }
            settle()
            assertTrue(repository.peek(SCOPE).shared.first { it.id == "materials-all" }.isSaved)
            scenario.onActivity { assertEquals(null, it.supportFragmentManager.findFragmentByTag(LinkActionsBottomSheet.TAG)) }
        }
    }

    @Test fun longTitlesFitAtLargeFontOnANarrowScreen() {
        val narrow = Appearances.all.first { it.widthDp == 320 && !it.dark }.toSubjectLinks()
        withPreview(narrow, SubjectLinksPreviewActivity.SCREEN_LINKS) { scenario, _ ->
            settle()
            var longTitle = false
            visitRows(scenario) { row ->
                assertTextFits(row, checkEdges = true)
                longTitle = longTitle || row.findViewById<TextView>(R.id.title)?.text?.startsWith("Полный конспект") == true
            }
            assertTrue(longTitle)
            screenshot("links-narrow")
        }
        withPreview(narrow, SubjectLinksPreviewActivity.SCREEN_EDITOR, linkId = "own-rejected") { scenario, _ ->
            settle()
            screenshot("editor-narrow")
            scenario.onActivity { activity ->
                val sheet = editor(activity)
                assertEquals(activity.getString(R.string.links_editor_edit), sheet.findViewById<TextView>(R.id.title).text.toString())
                assertEquals(5, audienceRows(sheet).size)
                assertTextFits(sheet)
            }
        }
    }

    private fun withPreview(
        appearance: SubjectLinksPreviewActivity.Appearance,
        screen: String,
        linkId: String? = null,
        clipboard: String? = null,
        configure: (MemorySubjectLinksRepository) -> Unit = { it.snapshots.value = mapOf(SCOPE.key to fixture()) },
        block: (ActivityScenario<SubjectLinksPreviewActivity>, MemorySubjectLinksRepository) -> Unit,
    ) {
        TestUi.instrumentation.runOnMainSync {
            val manager = ApplicationProvider.getApplicationContext<Context>().getSystemService(ClipboardManager::class.java)
            if (clipboard == null) manager.clearPrimaryClip() else manager.setPrimaryClip(ClipData.newPlainText("link", clipboard))
        }
        val repository = MemorySubjectLinksRepository().apply { servicesEnabled = true }.also(configure)
        SubjectLinksPreviewActivity.appearance = appearance
        SubjectLinksPreviewActivity.repository = repository
        val intent = Intent(ApplicationProvider.getApplicationContext(), SubjectLinksPreviewActivity::class.java)
            .putExtra(SubjectLinksPreviewActivity.EXTRA_SCREEN, screen)
            .putExtra(SubjectLinksPreviewActivity.EXTRA_LINK_ID, linkId)
        try {
            ActivityScenario.launch<SubjectLinksPreviewActivity>(intent).use { block(it, repository) }
        } finally {
            SubjectLinksPreviewActivity.appearance = SubjectLinksPreviewActivity.Appearance()
            SubjectLinksPreviewActivity.repository = MemorySubjectLinksRepository()
        }
    }

    /** Scrolls through the whole list, handing every bound row to [block] on the main thread. */
    private fun visitRows(scenario: ActivityScenario<SubjectLinksPreviewActivity>, block: (View) -> Unit) {
        var count = 0
        scenario.onActivity { count = checkNotNull(sheetList(it).adapter).itemCount }
        assertTrue(count > 0)
        for (position in 0 until count) {
            scenario.onActivity { sheetList(it).scrollToPosition(position) }
            TestUi.eventually(idleBetween = true) {
                scenario.onActivity { assertNotNull(sheetList(it).findViewHolderForAdapterPosition(position)) }
            }
            scenario.onActivity { block(checkNotNull(sheetList(it).findViewHolderForAdapterPosition(position)).itemView) }
        }
    }

    private fun positionOf(scenario: ActivityScenario<SubjectLinksPreviewActivity>, title: String): Int {
        var found = -1
        var position = 0
        visitRows(scenario) { row ->
            if (row !is TextView && row.findViewById<TextView>(R.id.title).text.toString() == title) found = position
            position++
        }
        assertTrue("No row $title", found >= 0)
        scenario.onActivity { sheetList(it).scrollToPosition(found) }
        TestUi.eventually(idleBetween = true) {
            scenario.onActivity { assertNotNull(sheetList(it).findViewHolderForAdapterPosition(found)) }
        }
        return found
    }

    private fun sheetView(activity: SubjectLinksPreviewActivity, tag: String): View =
        checkNotNull((activity.supportFragmentManager.findFragmentByTag(tag) as DialogFragment).dialog).window!!.decorView

    private fun sheetList(activity: SubjectLinksPreviewActivity): RecyclerView =
        sheetView(activity, SubjectLinksBottomSheet.TAG).findViewById(R.id.recycler_view)

    private fun editor(activity: SubjectLinksPreviewActivity) = sheetView(activity, LinkEditorBottomSheet.TAG)

    private fun actions(activity: SubjectLinksPreviewActivity) = sheetView(activity, LinkActionsBottomSheet.TAG)

    private fun radioRows(sheet: View): List<MaterialRadioButton> =
        sheet.findViewById<View>(R.id.visibility).descendants().filterIsInstance<MaterialRadioButton>()
            .filter { it.visibility == View.VISIBLE }.toList()

    private fun audienceRows(sheet: View): List<String> = radioRows(sheet).map { it.text.toString() }

    /** The first line of the checked row. */
    private fun checkedAudience(sheet: View): String? =
        radioRows(sheet).singleOrNull { it.isChecked }?.text?.toString()?.substringBefore('\n')

    private fun visibleActions(sheet: View): List<Int> =
        listOf(R.id.action_open, R.id.action_save, R.id.action_pin, R.id.action_edit, R.id.action_delete, R.id.action_report)
            .filter { sheet.findViewById<View>(it).visibility == View.VISIBLE }

    private fun settle() = TestUi.settle(600)

    private fun screenshot(name: String) = Screenshots.capture("links-screenshots", name)

    private companion object {
        val SCOPE = ResourceScope(42L, "Математический анализ", "2026-1")
        val PAST = ResourceScope(42L, "Математический анализ", "2025-1")
        val LECTURE_FLOW = LinkAudience(7101, "ФИЗ ПИИКТ 3", typeId = 1, depth = 1)
        val PRACTICE_FLOW = LinkAudience(7102, "ФИЗ ПИИКТ 3.2", typeId = 3, depth = 2)
        val LAB_FLOW = LinkAudience(7103, "ФИЗ ПИИКТ 3.2.1", typeId = 2, depth = 3)
        val NOW: OffsetDateTime = OffsetDateTime.parse("2026-09-22T09:00:00Z")
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
            saved: Boolean = false,
            scope: ResourceScope = SCOPE,
        ) = SubjectLink(id, scope, category, url, title, visibility, flow?.flowId, flow?.label, status, reviewNote, score, myVote,
            isMine = mine, isSaved = saved, reportedByMe = false, author = AUTHOR.takeUnless { mine }, updatedAt = NOW)

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
                link("queue-group", LinkCategory.QUEUE, "https://docs.google.com/forms/d/queue", "Очередь на защиту",
                    LinkVisibility.FLOW, score = 4, flow = PRACTICE_FLOW),
                link("materials-all", LinkCategory.MATERIALS, "https://drive.google.com/synthetic", "Материалы лектора", score = 12),
                link("tasks-flow", LinkCategory.TASKS, "https://github.com/synthetic/tasks", "Задания потока", LinkVisibility.FLOW,
                    flow = LECTURE_FLOW),
                link("recordings-all", LinkCategory.RECORDINGS, "https://youtube.com/synthetic", "Записи лекций 2026", score = -2, myVote = -1),
                link("exam-all", LinkCategory.EXAM, "https://example.org/exam", "Билеты к экзамену", score = 3, saved = true),
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
