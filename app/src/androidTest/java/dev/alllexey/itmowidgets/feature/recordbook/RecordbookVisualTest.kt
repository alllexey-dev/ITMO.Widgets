package dev.alllexey.itmowidgets.feature.recordbook

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.color.MaterialColors
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.SubjectLesson
import dev.alllexey.itmowidgets.core.sport.SportScorePeriod
import dev.alllexey.itmowidgets.core.sport.SportScoreRepository
import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookViewModel
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectViewModel
import dev.alllexey.itmowidgets.feature.recordbook.ui.DetailItem
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookPreviewFixtures
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookPreviewFixtures.Phase
import dev.alllexey.itmowidgets.feature.recordbook.ui.SubjectHubAdapter
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookPreviewActivity
import dev.alllexey.itmowidgets.testing.Appearances
import dev.alllexey.itmowidgets.testing.Appearances.toRecordbook
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestUi
import dev.alllexey.itmowidgets.testing.ViewChecks.assertTextFits
import dev.alllexey.itmowidgets.testing.ViewChecks.descendants
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordbookVisualTest {
    @Test fun firstLoadWithoutAnyAnswerShowsTheSkeleton() {
        withPreview(RecordbookPreviewActivity.Appearance(), configure = { it.pending = CompletableDeferred() }) { scenario, _ ->
            settle()
            scenario.onActivity { activity ->
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.loading).visibility)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.state_container).visibility)
            }
            screenshot("root-loading")
        }
    }

    @Test fun semesterNumbersStayContinuousInPickerAndHeading() {
        withPreview(RecordbookPreviewActivity.Appearance(widthDp = 320, fontScale = 1.3f)) { scenario, repository ->
            settle()
            listOf(4, 3).forEach { semester ->
                scenario.onActivity { it.findViewById<View>(R.id.period_button).performClick() }
                settle()
                scenario.onActivity { activity ->
                    val sheet = activity.supportFragmentManager.findFragmentByTag("RecordbookPeriodBottomSheet") as DialogFragment
                    val decor = checkNotNull(sheet.dialog).window!!.decorView
                    assertVisibleTextFits(decor)
                    val labels = decor.descendants().filterIsInstance<TextView>().map { it.text.toString() }.toList()
                    (1..4).forEach { number ->
                        assertTrue(labels.contains(activity.getString(R.string.recordbook_period_value, (number + 1) / 2, number)))
                    }
                }
                screenshot("period-numbering")
                scenario.onActivity { activity ->
                    val sheet = activity.supportFragmentManager.findFragmentByTag("RecordbookPeriodBottomSheet") as DialogFragment
                    val title = activity.getString(R.string.recordbook_period_value, 2, semester)
                    sheet.requireView().findViewById<RecyclerView>(R.id.recycler_view).children().first {
                        it.findViewById<TextView>(R.id.title).text.toString() == title
                    }.performClick()
                }
                settle()
                scenario.onActivity {
                    assertEquals(it.getString(R.string.recordbook_period_value, 2, semester), it.findViewById<TextView>(R.id.period_button).text.toString())
                    assertEquals(semester, repository.lastRequestedSemester)
                    assertVisibleTextFits(it.window.decorView)
                }
                screenshot("semester-$semester")
            }
        }
    }

    @Test fun sessionRowsShowBadgesTheSummaryAndAttentionWithoutRings() {
        Appearances.default.forEach { spec ->
            withFixture(Phase.SESSION, spec.toRecordbook()) { scenario ->
                scenario.onActivity { activity ->
                    val list = activity.findViewById<RecyclerView>(R.id.main_recycler_view)
                    assertEquals(0, list.descendants().count { it is CircularProgressIndicator })
                    val texts = list.texts()
                    assertTrue(activity.getString(R.string.recordbook_summary_closed, 4, 6) in texts)
                    assertEquals(activity.getString(R.string.recordbook_attention_section), texts[1])
                    val math = activity.row(RecordbookPreviewFixtures.MATH)
                    assertEquals(activity.getString(R.string.recordbook_reason_failed), math.findViewById<TextView>(R.id.meta).text.toString())
                    assertEquals("2FX", math.findViewById<TextView>(R.id.grade).text.toString())
                    assertEquals(View.GONE, math.findViewById<View>(R.id.score_group).visibility)
                    assertEquals(activity.getString(R.string.recordbook_absent), activity.row("История").findViewById<TextView>(R.id.meta).text.toString())
                    assertEquals("5A", activity.row("Проектирование").findViewById<TextView>(R.id.grade).text.toString())
                    assertTouchTargets(activity)
                    assertVisibleTextFits(activity.window.decorView)
                }
                screenshot("root-session-${spec.name}")
            }
        }
    }

    @Test fun middleOfTheSemesterShowsNumbersAndPutsShortSportUnderAttention() {
        Appearances.default.forEach { spec ->
            withFixture(Phase.MIDDLE, spec.toRecordbook()) { scenario ->
                scenario.onActivity { activity ->
                    val list = activity.findViewById<RecyclerView>(R.id.main_recycler_view)
                    val texts = list.texts()
                    val summaryPrefix = activity.getString(R.string.recordbook_summary_closed, 0, 0).substringBefore(" 0")
                    assertTrue(texts.none { it.startsWith(summaryPrefix) })
                    assertEquals(activity.getString(R.string.recordbook_attention_section), texts.first())
                    val pe = activity.row("Физическая")
                    assertEquals(activity.resources.getQuantityString(R.plurals.recordbook_reason_sport, 36, 36),
                        pe.findViewById<TextView>(R.id.meta).text.toString())
                    assertEquals("64", pe.findViewById<TextView>(R.id.score).text.toString())
                    val math = activity.row(RecordbookPreviewFixtures.MATH)
                    assertEquals(activity.getString(R.string.recordbook_reason_below_minimum, "Контрольная работа 1"),
                        math.findViewById<TextView>(R.id.meta).text.toString())
                    assertEquals("72", math.findViewById<TextView>(R.id.score).text.toString())
                    assertEquals(720, math.findViewById<LinearProgressIndicator>(R.id.progress).progress)
                    val bar = math.findViewById<View>(R.id.progress)
                    assertEquals(64 * activity.resources.displayMetrics.density, bar.width.toFloat(), 1f)
                    assertEquals(View.GONE, math.findViewById<View>(R.id.grade).visibility)
                    val history = activity.row("История")
                    assertEquals(activity.getString(R.string.recordbook_score_pending), history.findViewById<TextView>(R.id.grade).text.toString())
                    assertVisibleTextFits(activity.window.decorView)
                }
                screenshot("root-middle-${spec.name}")
            }
        }
    }

    @Test fun startOfTheSemesterKeepsShortSportInTheRegularList() {
        withFixture(Phase.START, Appearances.light.toRecordbook()) { scenario ->
            scenario.onActivity { activity ->
                val texts = activity.findViewById<RecyclerView>(R.id.main_recycler_view).texts()
                assertFalse(activity.getString(R.string.recordbook_attention_section) in texts)
                val pe = activity.row("Физическая")
                assertEquals("Зачёт", pe.findViewById<TextView>(R.id.meta).text.toString())
                assertEquals("12", pe.findViewById<TextView>(R.id.score).text.toString())
                assertVisibleTextFits(activity.window.decorView)
            }
            screenshot("root-start")
        }
    }

    @Test fun subjectIsOnePageWithHeroChipsChatsGroupsAndLessons() {
        Appearances.default.forEach { spec ->
            withFixture(Phase.MIDDLE, spec.toRecordbook()) { scenario ->
                openSubject(scenario, "Математический")
                scenario.onActivity { activity ->
                    assertEquals(RecordbookPreviewFixtures.MATH, activity.findViewById<TextView>(R.id.title).text.toString())
                    assertEquals(activity.getString(R.string.subject_subtitle, "Экзамен", 2), activity.findViewById<TextView>(R.id.subtitle).text.toString())
                    assertEquals(0, activity.window.decorView.descendants().count { it.javaClass.simpleName == "TabLayout" })
                    val items = activity.hubItems()
                    assertTrue(items.first() is DetailItem.Hero)
                    assertTrue(items[1] is DetailItem.LinkChips)
                    assertEquals("72", activity.findViewById<TextView>(R.id.points).text.toString())
                    assertEquals(activity.getString(R.string.subject_grade_next, "4C", "3"), activity.findViewById<TextView>(R.id.hint).text.toString())
                    val chips = activity.findViewById<ChipGroup>(R.id.chips).descendants().filterIsInstance<Chip>().toList()
                    assertEquals(listOf(activity.getString(R.string.subject_link_lms), "Таблица баллов потока", "Задания на семестр",
                        "Записи лекций весны 2026 года с разбором задач", activity.getString(R.string.links_more, 2), ""),
                        chips.map { it.text.toString() })
                    assertEquals(activity.getString(R.string.links_add), chips.last().contentDescription)
                    chips.forEach { assertTrue(it.height >= 48 * activity.resources.displayMetrics.density - 1) }
                    chips[4].performClick()
                    chips.last().performClick()
                    chips[1].performLongClick()
                    assertEquals(listOf("links", "editor", "actions:own-table"), RecordbookPreviewActivity.linkNavigation.toList())
                    assertEquals(2, items.count { it is DetailItem.Chat })
                    val groups = items.filterIsInstance<DetailItem.Group>()
                    assertEquals(3, groups.size)
                    assertEquals(listOf(false, true, false), groups.map { it.group.belowMinimum.isNotEmpty() })
                    assertEquals(2, items.count { it is DetailItem.Lesson })
                    assertEquals(DetailItem.AllLessons(4), items.last())
                    assertVisibleTextFits(activity.window.decorView)
                }
                screenshot("subject-top-${spec.name}")
                scrollTo(scenario) { items -> items.indexOfFirst { it is DetailItem.Group } }
                scenario.onActivity { activity ->
                    assertTrue(activity.hubList().texts().contains(activity.getString(R.string.recordbook_group_labs)))
                    assertVisibleTextFits(activity.window.decorView)
                }
                screenshot("subject-groups-${spec.name}")
                scrollTo(scenario) { items -> items.indexOfFirst { it is DetailItem.Group && it.group.belowMinimum.isNotEmpty() } }
                scenario.onActivity { activity ->
                    val badge = activity.hubList().descendants().first { it.id == R.id.below_minimum && it.isShown } as TextView
                    assertEquals(activity.getString(R.string.recordbook_group_below_minimum), badge.text.toString())
                    assertTrue(activity.hubList().texts().contains(activity.getString(R.string.recordbook_group_tests)))
                    assertVisibleTextFits(activity.window.decorView)
                }
                screenshot("subject-below-minimum-${spec.name}")
                scrollToEnd(scenario)
                scenario.onActivity { activity ->
                    activity.hubList().descendants().first { it.id == R.id.more && it.isShown }.performClick()
                }
                settle()
                scrollToEnd(scenario)
                scenario.onActivity { activity ->
                    assertEquals(4, activity.hubItems().count { it is DetailItem.Lesson })
                    assertTrue(activity.hubItems().none { it is DetailItem.AllLessons })
                    assertTouchTargets(activity)
                    assertVisibleTextFits(activity.window.decorView)
                }
                screenshot("subject-lessons-${spec.name}")
                scenario.recreate()
                settle()
                scenario.onActivity { assertVisibleTextFits(it.window.decorView) }
            }
        }
    }

    @Test fun creditSubjectCountsDownToTheCredit() {
        withFixture(Phase.MIDDLE, RecordbookPreviewActivity.Appearance(widthDp = 320, fontScale = 1.3f)) { scenario ->
            openSubject(scenario, "Иностранный")
            scenario.onActivity { activity ->
                assertEquals(activity.getString(R.string.subject_credit_next, "8"), activity.findViewById<TextView>(R.id.hint).text.toString())
                // Without links yet the add chip explains itself.
                val chips = activity.findViewById<ChipGroup>(R.id.chips).descendants().filterIsInstance<Chip>().toList()
                assertEquals(listOf(activity.getString(R.string.links_add)), chips.map { it.text.toString() })
                assertVisibleTextFits(activity.window.decorView)
            }
            screenshot("subject-credit-narrow")
        }
    }

    @Test fun sessionSubjectShowsItsGradeAndNoHint() {
        withFixture(Phase.SESSION, Appearances.light.toRecordbook()) { scenario ->
            openSubject(scenario, "Алгоритмы")
            scenario.onActivity { activity ->
                assertEquals("4C", activity.findViewById<TextView>(R.id.grade).text.toString())
                assertEquals(View.GONE, activity.findViewById<View>(R.id.hint).visibility)
                assertVisibleTextFits(activity.window.decorView)
            }
            screenshot("subject-session")
        }
    }

    @Test fun pastPeriodKeepsLinksWithoutLessons() {
        withFixture(Phase.MIDDLE, Appearances.light.toRecordbook()) { scenario ->
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag(RecordbookPreviewActivity.ROOT_TAG)!!
                ViewModelProvider(fragment)[RecordbookViewModel::class.java].selectPeriod(1, 1)
            }
            settle()
            openSubject(scenario, "Математический")
            scenario.onActivity { activity ->
                val items = activity.hubItems()
                assertTrue(items.any { it is DetailItem.LinkChips })
                assertTrue(items.none { it is DetailItem.Lesson || it is DetailItem.LessonsMessage })
                val chips = activity.findViewById<ChipGroup>(R.id.chips).descendants().filterIsInstance<Chip>().map { it.text.toString() }.toList()
                assertTrue("Таблица баллов осени" in chips)
            }
            screenshot("subject-past-period")
        }
    }

    @Test fun physicalEducationPageHasTheSportOverviewAndNoLinks() {
        Appearances.default.forEach { spec ->
            withFixture(Phase.MIDDLE, spec.toRecordbook()) { scenario ->
                openSubject(scenario, "Физическая")
                scenario.onActivity { activity ->
                    val items = activity.hubItems()
                    assertTrue(items.first() is DetailItem.SportOverview)
                    assertTrue(items.none { it is DetailItem.LinkChips || it is DetailItem.Chat || it is DetailItem.Hero })
                    assertEquals("64", activity.findViewById<TextView>(R.id.total).text.toString())
                    assertEquals(RecordbookPreviewFixtures.PE, activity.findViewById<TextView>(R.id.title).text.toString())
                    assertVisibleTextFits(activity.window.decorView)
                }
                screenshot("subject-sport-${spec.name}")
            }
        }
    }

    @Test fun sportFailureAndMissingPeriodKeepOfficialCreditAndRetryRecoversPoints() {
        withPreview(RecordbookPreviewActivity.Appearance(widthDp = 320, fontScale = 1.3f, dark = true), configure = {
            it.sportRate = "зачет"
            it.sportScore = AppResult.Failure(AppError.Network)
        }) { scenario, repository ->
            settle()
            scenario.onActivity { it.findViewById<RecyclerView>(R.id.main_recycler_view).scrollToPosition(5) }
            settle()
            scenario.onActivity { activity ->
                activity.findViewById<RecyclerView>(R.id.main_recycler_view).children().first {
                    it.findViewById<TextView>(R.id.name)?.text?.contains("Физическая") == true
                }.performClick()
            }
            settle()
            var originalResultTop = 0
            scenario.onActivity {
                assertVisibleTextFits(it.window.decorView)
                assertEquals(View.VISIBLE, it.findViewById<View>(R.id.error_content).visibility)
                assertEquals(View.GONE, it.findViewById<View>(R.id.score_content).visibility)
                assertEquals(it.getString(R.string.recordbook_rate_credit), it.findViewById<TextView>(R.id.official_result).text.toString())
                assertTrue(it.findViewById<View>(R.id.retry).height >= 48 * it.resources.displayMetrics.density - 1)
                val location = IntArray(2)
                it.findViewById<View>(R.id.official_result).getLocationOnScreen(location)
                originalResultTop = location[1]
            }
            screenshot("sport-error")
            repository.sportScore = AppResult.Success(SportScoreSummary(100, 20))
            scenario.onActivity { it.findViewById<View>(R.id.retry).performClick() }
            settle()
            scenario.onActivity {
                assertVisibleTextFits(it.window.decorView)
                assertEquals(View.VISIBLE, it.findViewById<View>(R.id.score_content).visibility)
                assertEquals("120", it.findViewById<TextView>(R.id.total).text.toString())
                val location = IntArray(2)
                it.findViewById<View>(R.id.official_result).getLocationOnScreen(location)
                assertEquals(originalResultTop, location[1])
            }
            screenshot("sport-recovered")
            repository.hasSportPeriod = false
            scenario.onActivity {
                ViewModelProvider(it.supportFragmentManager.findFragmentByTag("detail")!!)[RecordbookSubjectViewModel::class.java].refresh()
            }
            settle()
            scenario.onActivity {
                assertVisibleTextFits(it.window.decorView)
                assertEquals(View.VISIBLE, it.findViewById<View>(R.id.error_content).visibility)
                assertEquals(View.GONE, it.findViewById<View>(R.id.retry).visibility)
                assertEquals(it.getString(R.string.recordbook_rate_credit), it.findViewById<TextView>(R.id.official_result).text.toString())
            }
            screenshot("sport-no-period")
        }
    }

    @Test fun subjectHubProposesANameMatchAndConfirmingItShowsTheLessons() {
        RecordbookPreviewActivity.lessonsGateway = RecordbookPreviewActivity.MemoryLessons(listOf(
            hubLesson(7, "2026-06-03", subjectId = 555, typeId = 2, teacher = "Лаборант Л. Л.", isu = 9, name = "Алгоритмы и структуры данных")
        ))
        withPreview(Appearances.light.toRecordbook()) { scenario, _ ->
            settle()
            openSubject(scenario, "Алгоритмы")
            scrollToEnd(scenario)
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertTrue(activity.hubItems().any { it is DetailItem.BindingProposal })
                assertEquals(View.VISIBLE, root.findViewById<View>(R.id.confirm).visibility)
                assertEquals(activity.getString(R.string.subject_binding_proposal, "Алгоритмы и структуры данных"),
                    root.findViewById<TextView>(R.id.message).text.toString())
                assertEquals(0, root.descendants().count { it.id == R.id.type_indicator })
                assertVisibleTextFits(root)
            }
            screenshot("subject-hub-proposal")
            scenario.onActivity { it.window.decorView.findViewById<View>(R.id.confirm).performClick() }
            settle()
            scrollToEnd(scenario)
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertTrue(activity.hubItems().none { it is DetailItem.BindingProposal })
                assertEquals(1, activity.hubItems().count { it is DetailItem.Lesson })
                assertEquals(1, root.descendants().count { it.id == R.id.type_indicator })
                assertEquals(mapOf(2L to 555L), (RecordbookPreviewActivity.bindingStore as RecordbookPreviewActivity.MemoryBindings).bindings)
            }
            screenshot("subject-hub-bound")
        }
    }

    private fun withFixture(phase: Phase, appearance: RecordbookPreviewActivity.Appearance, block: (ActivityScenario<RecordbookPreviewActivity>) -> Unit) {
        RecordbookPreviewActivity.appearance = appearance
        RecordbookPreviewFixtures.install(phase)
        try {
            ActivityScenario.launch<RecordbookPreviewActivity>(Intent(ApplicationProvider.getApplicationContext(), RecordbookPreviewActivity::class.java)).use {
                settle()
                block(it)
            }
        } finally {
            RecordbookPreviewFixtures.reset()
        }
    }

    private fun RecordbookPreviewActivity.hubList(): RecyclerView = findViewById(R.id.recycler_view)

    private fun RecordbookPreviewActivity.hubItems(): List<DetailItem> = (hubList().adapter as SubjectHubAdapter).currentList

    private fun RecordbookPreviewActivity.row(namePart: String): View =
        findViewById<RecyclerView>(R.id.main_recycler_view).children().first { it.findViewById<TextView>(R.id.name)?.text?.contains(namePart) == true }

    private fun View.texts(): List<String> = descendants().filterIsInstance<TextView>().filter { it.isShown }.map { it.text.toString() }.toList()

    private fun assertTouchTargets(activity: RecordbookPreviewActivity) {
        val minimum = 48 * activity.resources.displayMetrics.density - 1
        activity.window.decorView.descendants().filter { it.isShown && it.isClickable }.forEach {
            assertTrue("Touch target ${it.javaClass.simpleName}", it.height >= minimum)
        }
    }

    private fun scrollTo(scenario: ActivityScenario<RecordbookPreviewActivity>, position: (List<DetailItem>) -> Int) {
        scenario.onActivity { activity ->
            (activity.hubList().layoutManager as androidx.recyclerview.widget.LinearLayoutManager)
                .scrollToPositionWithOffset(position(activity.hubItems()), 0)
        }
        settle()
    }

    private fun scrollToEnd(scenario: ActivityScenario<RecordbookPreviewActivity>) {
        scenario.onActivity { activity ->
            val list = activity.hubList()
            list.scrollToPosition(list.adapter!!.itemCount - 1)
        }
        settle()
    }

    private fun openSubject(scenario: ActivityScenario<RecordbookPreviewActivity>, namePart: String) {
        scenario.onActivity { activity ->
            activity.findViewById<RecyclerView>(R.id.main_recycler_view).children().first {
                it.findViewById<TextView>(R.id.name)?.text?.contains(namePart) == true
            }.performClick()
        }
        settle()
    }

    private fun hubLesson(pairId: Long, date: String, subjectId: Long, typeId: Int, teacher: String, isu: Long, name: String = "Предмет $subjectId") = SubjectLesson(
        pairId = pairId, date = LocalDate.parse(date), start = LocalTime.of(9, 30), end = LocalTime.of(11, 0), typeId = typeId, type = "",
        subjectId = subjectId, subjectName = name, flowId = subjectId * 10, teacherIsu = isu, teacherFio = teacher,
        room = "1506", building = "Кронверкский проспект, 49", formatId = 1
    )

    @Test fun emptyErrorLoadingAndContentTransitionsStayInTheContentArea() {
        withPreview(RecordbookPreviewActivity.Appearance(widthDp = 320, fontScale = 1.3f)) { scenario, repository ->
            settle()
            val pending = CompletableDeferred<AppResult<List<RecordbookSubject>>>()
            repository.pending = pending
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag(RecordbookPreviewActivity.ROOT_TAG)!!
                ViewModelProvider(fragment)[RecordbookViewModel::class.java].selectPeriod(1, 1)
            }
            settle()
            screenshot("state-loading")
            scenario.onActivity {
                assertEquals(View.VISIBLE, it.findViewById<View>(R.id.loading).visibility)
                assertEquals(View.GONE, it.findViewById<View>(R.id.swipe_refresh_layout).visibility)
            }
            pending.complete(AppResult.Success(emptyList()))
            repository.pending = null
            settle()
            screenshot("state-empty")
            scenario.onActivity {
                assertEquals(View.VISIBLE, it.findViewById<View>(R.id.state_container).visibility)
                assertEquals(View.GONE, it.findViewById<View>(R.id.swipe_refresh_layout).visibility)
                assertVisibleTextFits(it.window.decorView)
            }
            repository.failure = true
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag(RecordbookPreviewActivity.ROOT_TAG)!!
                ViewModelProvider(fragment)[RecordbookViewModel::class.java].selectPeriod(1, 2)
            }
            settle()
            screenshot("state-error")
            scenario.onActivity { assertVisibleTextFits(it.window.decorView) }
            repository.failure = false
            scenario.onActivity { it.findViewById<View>(R.id.state_action).performClick() }
            settle()
            scenario.onActivity { assertEquals(View.GONE, it.findViewById<View>(R.id.state_container).visibility) }
            screenshot("state-recovered")
        }
    }

    private fun withPreview(appearance: RecordbookPreviewActivity.Appearance, configure: (PreviewRepository) -> Unit = {}, block: (ActivityScenario<RecordbookPreviewActivity>, PreviewRepository) -> Unit) {
        val repository = PreviewRepository().apply(configure)
        RecordbookPreviewActivity.appearance = appearance
        RecordbookPreviewActivity.repository = repository
        RecordbookPreviewActivity.sportRepository = object : SportScoreRepository {
            override suspend fun getScorePeriods() = AppResult.Success(if (repository.hasSportPeriod) listOf(SportScorePeriod(10, "Весна 2025/2026")) else emptyList())
            override suspend fun getScoreSummary(semesterId: Long) = repository.sportScore
        }
        try {
            ActivityScenario.launch<RecordbookPreviewActivity>(Intent(ApplicationProvider.getApplicationContext(), RecordbookPreviewActivity::class.java)).use { block(it, repository) }
        } finally {
            RecordbookPreviewActivity.bars = null
            RecordbookPreviewActivity.repository = null
            RecordbookPreviewActivity.sportRepository = null
            RecordbookPreviewActivity.lessonsGateway = RecordbookPreviewActivity.MemoryLessons()
            RecordbookPreviewActivity.bindingStore = RecordbookPreviewActivity.MemoryBindings()
        }
    }

    private fun settle() = TestUi.settle(600)

    private fun screenshot(name: String) = Screenshots.capture("recordbook-screenshots", name)

    /** Only subject names (two lines) and link chips (bounded width) may end in an ellipsis. */
    private fun assertVisibleTextFits(root: View) {
        assertTextFits(root, allowEllipsis = true)
        root.descendants().filterIsInstance<TextView>()
            .filter { it.isShown && it !is Chip && it.id != R.id.name && it.id != R.id.title }
            .forEach { view ->
                val layout = view.layout ?: return@forEach
                (0 until layout.lineCount).forEach { line -> assertEquals("Ellipsis: ${view.text}", 0, layout.getEllipsisCount(line)) }
            }
    }

    private fun ViewGroup.children() = (0 until childCount).map(::getChildAt)

    private class PreviewRepository : RecordbookRepository {
        @Volatile var sportScore: AppResult<SportScoreSummary> = AppResult.Success(SportScoreSummary(66, 28))
        @Volatile var sportRate: String? = null
        @Volatile var hasSportPeriod = true
        @Volatile var lastRequestedSemester = 0
        @Volatile var failure = false
        @Volatile var pending: CompletableDeferred<AppResult<List<RecordbookSubject>>>? = null
        override suspend fun getPrograms() = AppResult.Success(listOf(RecordbookProgram(1, "Тестовая образовательная программа", listOf(
            RecordbookPeriod("2026/2027", 4, 2, false), RecordbookPeriod("2026/2027", 3, 2, false),
            RecordbookPeriod("2025/2026", 2, 1, true), RecordbookPeriod("2025/2026", 1, 1, false)
        ))))
        override suspend fun getSubjects(programId: Long, semester: Int): AppResult<List<RecordbookSubject>> {
            lastRequestedSemester = semester
            pending?.let { return it.await() }
            if (failure) return AppResult.Failure(AppError.Network)
            return AppResult.Success(listOf(
                subject(1, "Математический анализ (продвинутый уровень)", "2/FX", 48.5),
                subject(2, "Алгоритмы и структуры данных", "4/C", 76.5),
                subject(3, "Физическая культура и спорт (элективная)", sportRate, null, false),
                subject(4, "Проектирование и разработка распределённых информационных систем", null, null),
                subject(5, "Иностранный язык", "зачет", 62.0),
                subject(6, "Проектная работа", "5/A", 100.0),
                subject(7, "Основы программирования", null, 0.0)
            ))
        }
        override suspend fun getControls(entryId: Long) = AppResult.Success(listOf(
            RecordbookControl(1, "Практические работы", 32.0, 20.0, 40.0, true, null, null),
            RecordbookControl(2, "Исследование сходимости последовательностей и функциональных рядов", 5.0, 4.0, 8.0, true, null, "Тестовый преподаватель с длинным именем и отчеством", 1),
            RecordbookControl(3, "Экзамен", null, null, 40.0, true, null, null),
            RecordbookControl(4, "Homework 1", 5.0, 5.0, 7.0, false, null, null),
            RecordbookControl(5, "Homework 2", 7.0, 5.0, 7.0, true, null, null),
            RecordbookControl(6, "Дополнительные баллы", null, null, null, false, null, null)
        ))
        private fun subject(id: Long, name: String, rate: String?, score: Double?, details: Boolean = true) = RecordbookSubject(
            name, id, id, if (id == 3L || id == 5L) "Зачёт" else "Экзамен", score, rate, null, null, details,
            "Тестовый преподаватель с длинным именем и отчеством"
        )
    }
}
