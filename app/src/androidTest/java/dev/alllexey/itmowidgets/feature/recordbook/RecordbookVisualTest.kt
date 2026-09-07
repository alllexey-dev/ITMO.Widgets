package dev.alllexey.itmowidgets.feature.recordbook

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
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
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.color.MaterialColors
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
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
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookPreviewActivity
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookScoreView
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordbookVisualTest {
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

    @Test fun realFragmentsInLightDarkAndDynamicPalettesAtLargeFont() {
        val appearances = listOf(
            RecordbookPreviewActivity.Appearance(),
            RecordbookPreviewActivity.Appearance(dark = true),
            RecordbookPreviewActivity.Appearance(widthDp = 320, fontScale = 1.3f, colorSeed = 0xff826c24.toInt()),
            RecordbookPreviewActivity.Appearance(widthDp = 320, fontScale = 1.3f, dark = true, colorSeed = 0xff386a20.toInt())
        )
        appearances.forEachIndexed { index, appearance ->
            withPreview(appearance, configure = { repository ->
                repository.sportScore = AppResult.Success(when (index) {
                    1 -> SportScoreSummary(100, 20)
                    2 -> SportScoreSummary(66, 65)
                    3 -> SportScoreSummary(0, 0)
                    else -> SportScoreSummary(66, 28)
                })
                repository.sportRate = "зачет".takeIf { index == 1 }
            }) { scenario, repository ->
                settle()
                scenario.onActivity { activity ->
                    assertVisibleTextFits(activity.window.decorView)
                    val period = activity.findViewById<View>(R.id.period_button)
                    val info = activity.findViewById<View>(R.id.source_button)
                    val minimum = 48 * activity.resources.displayMetrics.density
                    assertTrue(period.height >= minimum - 1)
                    assertTrue(info.width >= minimum - 1 && info.height >= minimum - 1)
                    assertNotNull(info.contentDescription)
                    val ring = activity.findViewById<CircularProgressIndicator>(R.id.score_ring)
                    assertFalse(ring.isIndeterminate)
                    assertEquals(485, ring.progress)
                    assertTrue(ring.indicatorSize <= ring.width && ring.indicatorSize <= ring.height)
                    assertTrue(ColorUtils.calculateContrast(ring.indicatorColor.first(), MaterialColors.getColor(
                        ring, com.google.android.material.R.attr.colorSurfaceContainerLow)) >= 3.0)
                    assertEquals("2FX", activity.findViewById<TextView>(R.id.rate).text.toString())
                }
                screenshot("root-$index")
                scenario.onActivity { it.findViewById<View>(R.id.period_button).performClick() }
                settle()
                scenario.onActivity { activity ->
                    val sheet = activity.supportFragmentManager.findFragmentByTag("RecordbookPeriodBottomSheet") as DialogFragment
                    assertVisibleTextFits(checkNotNull(sheet.dialog).window!!.decorView)
                }
                screenshot("period-$index")
                scenario.onActivity { activity ->
                    (activity.supportFragmentManager.findFragmentByTag("RecordbookPeriodBottomSheet") as DialogFragment).dismiss()
                }
                settle()
                scenario.onActivity { activity ->
                    val list = activity.findViewById<RecyclerView>(R.id.main_recycler_view)
                    list.scrollToPosition(4)
                }
                settle()
                screenshot("root-sport-$index")
                scenario.onActivity { activity ->
                    val list = activity.findViewById<RecyclerView>(R.id.main_recycler_view)
                    val sport = list.children().first { row -> row.findViewById<TextView>(R.id.name)?.text?.contains("Физическая") == true }
                    val score = (repository.sportScore as AppResult.Success).value
                    assertEquals(score.total.toString(), sport.findViewById<TextView>(R.id.points).text.toString())
                    assertEquals(score.totalCapped * 10, sport.findViewById<CircularProgressIndicator>(R.id.score_ring).progress)
                    assertEquals(if (index == 1) View.VISIBLE else View.GONE, sport.findViewById<View>(R.id.rate_icon).visibility)
                    sport.performClick()
                }
                settle()
                scenario.onActivity {
                    assertVisibleTextFits(it.window.decorView)
                    val score = (repository.sportScore as AppResult.Success).value
                    assertEquals(score.total.toString(), it.findViewById<TextView>(R.id.total).text.toString())
                    assertEquals(score.creditedBonus.toString(), it.findViewById<TextView>(R.id.bonus).text.toString())
                    assertEquals(it.getString(if (index == 1) R.string.recordbook_rate_credit else R.string.recordbook_official_pending),
                        it.findViewById<TextView>(R.id.official_result).text.toString())
                    assertEquals(1, it.findViewById<RecyclerView>(R.id.recycler_view).adapter!!.itemCount)
                }
                screenshot("subject-sport-$index")
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                settle()
                scenario.onActivity { activity ->
                    val list = activity.findViewById<RecyclerView>(R.id.main_recycler_view)
                    list.scrollToPosition(2)
                }
                settle()
                scenario.onActivity { activity ->
                    val list = activity.findViewById<RecyclerView>(R.id.main_recycler_view)
                    list.children().first { it.findViewById<TextView>(R.id.name)?.text?.contains("Математический") == true }.performClick()
                }
                settle()
                scenario.onActivity { assertVisibleTextFits(it.window.decorView) }
                screenshot("subject-controls-$index")
                scenario.recreate()
                settle()
                scenario.onActivity { assertVisibleTextFits(it.window.decorView) }
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

    @Test fun resultRingRebindingPreservesOfficialGradesAndMissingScores() {
        withPreview(RecordbookPreviewActivity.Appearance(fontScale = 1.3f)) { scenario, _ ->
            scenario.onActivity { activity ->
                val view = RecordbookScoreView(activity)
                val subject = RecordbookSubject("Предмет", 1, 1, "Экзамен", 100.0, "5/A", null, null, false, null)
                val ring = view.findViewById<CircularProgressIndicator>(R.id.score_ring)
                view.bind(subject)
                assertEquals(1000, ring.progress)
                assertEquals("5A", view.findViewById<TextView>(R.id.rate).text.toString())
                view.bind(subject.copy(score = 116.5, rate = "зачет"))
                assertEquals(1000, ring.progress)
                assertEquals("116,5", view.findViewById<TextView>(R.id.points).text.toString())
                assertEquals(View.VISIBLE, view.findViewById<View>(R.id.rate_icon).visibility)
                view.bind(subject.copy(score = null, rate = "зачет"))
                assertEquals(0, ring.progress)
                assertEquals("—", view.findViewById<TextView>(R.id.points).text.toString())
                assertEquals(View.GONE, view.findViewById<View>(R.id.points).visibility)
                assertEquals(View.VISIBLE, view.findViewById<View>(R.id.rate_icon).visibility)
                assertTrue(view.contentDescription.contains(activity.getString(R.string.recordbook_points_missing)))
                view.bind(subject.copy(score = 0.0, rate = null))
                assertEquals(View.GONE, view.findViewById<View>(R.id.rate_icon).visibility)
                assertEquals("0", view.findViewById<TextView>(R.id.points).text.toString())
                assertEquals(View.VISIBLE, view.findViewById<View>(R.id.points).visibility)
                assertTrue(view.contentDescription.contains(activity.getString(R.string.recordbook_rate_in_progress)))
                view.bind(subject.copy(score = 62.0, rate = "Неявка по уважительной причине"))
                assertEquals(620, ring.progress)
                assertEquals("—", view.findViewById<TextView>(R.id.rate).text.toString())
                assertTrue(view.contentDescription.contains("Неявка по уважительной причине"))
                val pe = subject.copy(name = "Физическая культура и спорт (базовая)", score = null, rate = null)
                view.bind(pe, RecordbookSportState.Content("Весна 2025/2026", SportScoreSummary(100, 20)))
                assertEquals(1000, ring.progress)
                assertEquals("120", view.findViewById<TextView>(R.id.points).text.toString())
                assertEquals(View.GONE, view.findViewById<View>(R.id.rate_icon).visibility)
                view.bind(pe.copy(rate = "зачет"), RecordbookSportState.Error)
                assertEquals(0, ring.progress)
                assertEquals(View.GONE, view.findViewById<View>(R.id.points).visibility)
                assertEquals(View.VISIBLE, view.findViewById<View>(R.id.rate_icon).visibility)
                view.bind(subject)
                assertEquals(1000, ring.progress)
                assertEquals("100", view.findViewById<TextView>(R.id.points).text.toString())
                assertFalse(view.contentDescription.contains(activity.getString(R.string.recordbook_sport_source)))
            }
        }
    }

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
            RecordbookPreviewActivity.repository = null
            RecordbookPreviewActivity.sportRepository = null
        }
    }

    private fun settle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        SystemClock.sleep(600)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.externalCacheDir, "recordbook-screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun assertVisibleTextFits(root: View) {
        root.descendants().filterIsInstance<TextView>().filter { it.isShown && it.text.isNotEmpty() }.forEach { view ->
            val layout = view.layout ?: return@forEach
            assertTrue("Text height: ${view.text}", layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
            for (line in 0 until layout.lineCount) {
                assertEquals("Ellipsis: ${view.text}", 0, layout.getEllipsisCount(line))
                assertTrue("Text width (${layout.getLineMax(line)} / ${view.width - view.compoundPaddingLeft - view.compoundPaddingRight}): ${view.text}", layout.getLineMax(line) <= view.width - view.compoundPaddingLeft - view.compoundPaddingRight + 1)
            }
        }
    }

    private fun ViewGroup.children() = (0 until childCount).map(::getChildAt)
    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) children().forEach { yieldAll(it.descendants()) }
    }

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
