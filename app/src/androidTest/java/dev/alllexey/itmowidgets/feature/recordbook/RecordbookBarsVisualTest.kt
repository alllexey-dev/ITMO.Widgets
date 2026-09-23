package dev.alllexey.itmowidgets.feature.recordbook

import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.allOf
import com.google.android.material.chip.Chip
import com.google.android.material.progressindicator.CircularProgressIndicator
import dev.alllexey.itmowidgets.testing.ViewChecks.descendants
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.sport.SportScorePeriod
import dev.alllexey.itmowidgets.core.sport.SportScoreRepository
import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsSubjectDetails
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.BarsJournalReference
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookPreviewActivity
import dev.alllexey.itmowidgets.testing.Appearances
import dev.alllexey.itmowidgets.testing.Appearances.toRecordbook
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestUi
import dev.alllexey.itmowidgets.testing.ViewChecks.assertTextFits
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordbookBarsVisualTest {
    @Test fun chipOverlaysBarsInLightDarkNarrowAndDynamicThemes() {
        Appearances.default.forEachIndexed { index, spec ->
            preview(spec.toRecordbook()) { scenario, _ ->
                settle()
                scenario.onActivity { activity ->
                    val chip = activity.findViewById<Chip>(R.id.bars_chip)
                    assertFalse(chip.isChecked)
                    // Compact chip: the view may grow to the 48 dp touch target but never to a button row.
                    assertTrue(chip.height <= 48 * activity.resources.displayMetrics.density + 1)
                    assertVisibleTextFits(activity.window.decorView)
                }
                screenshot("bars-off-$index")
                scenario.onActivity { it.findViewById<View>(R.id.bars_chip).performClick() }
                settle()
                scenario.onActivity { activity ->
                    assertTrue(activity.findViewById<Chip>(R.id.bars_chip).isChecked)
                    val list = activity.findViewById<RecyclerView>(R.id.main_recycler_view)
                    val cards = (0 until list.childCount).map(list::getChildAt).filter { it.findViewById<TextView>(R.id.name) != null }
                    val matched = cards.first { it.findViewById<TextView>(R.id.name).text.startsWith("Проектирование") }
                    val other = cards.first { it.findViewById<TextView>(R.id.name).text.startsWith("Философия") }
                    assertFalse(matched.findViewById<TextView>(R.id.meta).text.contains(activity.getString(R.string.recordbook_bars_missing)))
                    assertTrue(other.findViewById<TextView>(R.id.meta).text.contains(activity.getString(R.string.recordbook_bars_missing)))
                    // Rows carry a badge or a number with a thin bar, never a ring.
                    assertEquals("5A", matched.findViewById<TextView>(R.id.grade).text.toString())
                    assertEquals(View.GONE, matched.findViewById<View>(R.id.score_group).visibility)
                    assertTrue(cards.none { card -> card.descendants().any { it is CircularProgressIndicator } })
                    assertVisibleTextFits(activity.window.decorView)
                }
                screenshot("bars-on-$index")
                scenario.onActivity { activity ->
                    val list = activity.findViewById<RecyclerView>(R.id.main_recycler_view)
                    (0 until list.childCount).map(list::getChildAt).first { it.findViewById<TextView>(R.id.name)?.text?.startsWith("Проектирование") == true }.performClick()
                }
                settle()
                onView(withText("Практическая работа с длинным названием и подробным описанием")).check(matches(isDisplayed()))
                scenario.onActivity { assertVisibleTextFits(it.window.decorView) }
                screenshot("bars-detail-$index")
            }
        }
    }

    @Test fun barsFailureKeepsMyItmoAndOffersLoginOnlyWhenSessionEnded() {
        preview(RecordbookPreviewActivity.Appearance(widthDp = 320, fontScale = 1.3f)) { scenario, bars ->
            bars.failure = AppError.Unauthorized
            scenario.onActivity { it.findViewById<View>(R.id.bars_chip).performClick() }
            settle()
            onView(allOf(withText(R.string.recordbook_bars_login), isDisplayed())).check(matches(isDisplayed()))
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<Chip>(R.id.bars_chip).isChecked)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.main_recycler_view).visibility)
                assertVisibleTextFits(activity.window.decorView)
            }
            screenshot("bars-login-required")
            bars.failure = AppError.Network
            scenario.onActivity { it.findViewById<View>(R.id.bars_chip).performClick(); it.findViewById<View>(R.id.bars_chip).performClick() }
            settle()
            onView(allOf(withText(R.string.common_retry), isDisplayed())).check(matches(isDisplayed()))
            screenshot("bars-network-error")
        }
    }

    private fun preview(appearance: RecordbookPreviewActivity.Appearance, block: (ActivityScenario<RecordbookPreviewActivity>, Bars) -> Unit) {
        val bars = Bars()
        RecordbookPreviewActivity.appearance = appearance
        RecordbookPreviewActivity.repository = MyItmo()
        RecordbookPreviewActivity.bars = bars
        RecordbookPreviewActivity.sportRepository = object : SportScoreRepository {
            override suspend fun getScorePeriods() = AppResult.Success(emptyList<SportScorePeriod>())
            override suspend fun getScoreSummary(semesterId: Long) = AppResult.Success(SportScoreSummary(0, 0))
        }
        try {
            ActivityScenario.launch<RecordbookPreviewActivity>(Intent(ApplicationProvider.getApplicationContext(), RecordbookPreviewActivity::class.java)).use { block(it, bars) }
        } finally {
            RecordbookPreviewActivity.bars = null
            RecordbookPreviewActivity.repository = null
            RecordbookPreviewActivity.sportRepository = null
        }
    }

    private class MyItmo : RecordbookRepository {
        override suspend fun getPrograms(): AppResult<List<RecordbookProgram>> =
            AppResult.Success(listOf(RecordbookProgram(1, "Программа", listOf(RecordbookPeriod("2025/2026", 2, 1, true)))))
        override suspend fun getSubjects(programId: Long, semester: Int): AppResult<List<RecordbookSubject>> = AppResult.Success(listOf(
            RecordbookSubject("Проектирование и разработка распределённых информационных систем", 10, 42, "Экзамен", 43.5, null, null, null, true, "Иванов И. И."),
            RecordbookSubject("Философия", 11, 43, "Зачет", null, null, null, null, false, null)
        ))
        override suspend fun getControls(entryId: Long) = AppResult.Success(listOf(
            RecordbookControl(1, "Работа MyITMO", 3.0, 0.0, 10.0, true, null, null)
        ))
    }
    private class Bars : BarsRecordbookRepository {
        var failure: AppError? = null
        private val journal = BarsJournalReference(8, "flow", "7", 2025, 2)
        private val subject = RecordbookSubject("Проектирование и разработка распределённых информационных систем", 900, 8, "Экзамен", 91.5, "5/A", 1, null, true, null, journal)
        override suspend fun getSubjects(period: RecordbookPeriod): AppResult<List<RecordbookSubject>> =
            failure?.let { AppResult.Failure(it) } ?: AppResult.Success(listOf(subject))
        override suspend fun getSubject(journal: BarsJournalReference): AppResult<BarsSubjectDetails> =
            failure?.let { AppResult.Failure(it) } ?: AppResult.Success(BarsSubjectDetails(subject, listOf(
                RecordbookControl(1, "Практическая работа с длинным названием и подробным описанием", 7.5, 0.0, 10.0, true, null, null),
                RecordbookControl(2, "Экзамен", null, 0.0, 30.0, true, null, null),
                RecordbookControl(-8, "", 2.0, null, null, false, null, null, additional = true)
            )))
    }
    private fun settle() = TestUi.settle(550)
    private fun screenshot(name: String) = Screenshots.capture("recordbook-screenshots", name)
    /** Subject names are two lines at most and may end in an ellipsis; nothing else may. */
    private fun assertVisibleTextFits(root: View) {
        assertTextFits(root, allowEllipsis = true)
        root.descendants().filterIsInstance<TextView>().filter { it.isShown && it.id != R.id.name && it.id != R.id.title }.forEach { view ->
            val layout = view.layout ?: return@forEach
            (0 until layout.lineCount).forEach { assertEquals("Ellipsis: ${view.text}", 0, layout.getEllipsisCount(it)) }
        }
    }
}
