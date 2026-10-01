package dev.alllexey.itmowidgets.feature.recordbook

import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs
import dev.alllexey.itmowidgets.core.resources.GoogleSheetUrl
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.KeyKind
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.RowSearch
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetColumnRef
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetGrid
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetInspection
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRowMatch
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScore
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetStatus
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTab
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTabGrid
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetWorkbook
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookPreviewActivity
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookPreviewActivity.MemorySheetScores
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookPreviewFixtures
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookPreviewFixtures.Phase
import dev.alllexey.itmowidgets.feature.recordbook.ui.sheets.SheetScoresBottomSheet
import dev.alllexey.itmowidgets.testing.Appearances
import dev.alllexey.itmowidgets.testing.Appearances.toRecordbook
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestUi
import dev.alllexey.itmowidgets.testing.ViewChecks.assertTextFits
import dev.alllexey.itmowidgets.testing.ViewChecks.assertTouchTargets
import dev.alllexey.itmowidgets.testing.ViewChecks.descendants
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The «Мои баллы» sheet over the recordbook host with synthetic workbooks; never the network. */
@RunWith(AndroidJUnit4::class)
class SheetScoresVisualTest {

    @Test fun loadingAndFailuresKeepOneArea() {
        Appearances.default.forEach { spec ->
            withHost(spec.toRecordbook()) { scenario ->
                val gate = CompletableDeferred<Unit>()
                MemorySheetScores.inspectGate = gate
                MemorySheetScores.inspections += SheetInspection.Failed(SheetStatus.NETWORK)
                open(scenario, Step.CONNECT)
                var height = 0
                scenario.onActivity { activity ->
                    val sheet = sheet(activity)
                    assertTrue(sheet.findViewById<View>(R.id.loading).isShown)
                    assertFalse(sheet.findViewById<View>(R.id.state).isShown)
                    height = sheet.findViewById<View>(R.id.content).height
                }
                screenshot("sheet-loading-${spec.name}")

                gate.complete(Unit)
                MemorySheetScores.inspectGate = null
                TestUi.eventually(idleBetween = true) {
                    scenario.onActivity { activity ->
                        val sheet = sheet(activity)
                        assertEquals(activity.getString(R.string.sheet_scores_offline), stateTitle(sheet))
                        val retry = sheet.findViewById<View>(R.id.state_action)
                        assertTrue(retry.isShown)
                        assertTrue(retry.height >= 48 * activity.resources.displayMetrics.density - 1)
                        assertEquals(height, sheet.findViewById<View>(R.id.content).height)
                        assertTextFits(sheet)
                    }
                }

                MemorySheetScores.inspections += SheetInspection.Failed(SheetStatus.CLOSED)
                scenario.onActivity { sheet(it).findViewById<View>(R.id.state_action).performClick() }
                assertFailure(scenario, R.string.sheet_scores_closed, height)
                screenshot("sheet-closed-${spec.name}")
                scenario.onActivity { (it.supportFragmentManager.findFragmentByTag(SheetScoresBottomSheet.TAG) as DialogFragment).dismiss() }
                settle()

                MemorySheetScores.inspections += SheetInspection.Failed(SheetStatus.TOO_LARGE)
                open(scenario, Step.CONNECT)
                assertFailure(scenario, R.string.sheet_scores_too_large, height)
            }
        }
    }

    @Test fun rowChoiceListsCandidatesAndTabs() {
        Appearances.default.forEach { spec ->
            withHost(spec.toRecordbook()) { scenario ->
                val tab = SheetTab(22, "P3110")
                val grid = SheetGrid(listOf(
                    listOf("ФИО", "Группа", "Баллы"),
                    listOf(OWN_NAME, "P3110", "50"),
                    listOf(OWN_NAME, "P3112", "70"),
                ))
                val candidates = listOf(1, 2).map { SheetRowMatch(tab, it, 0, "тестов тест тестович", KeyKind.NAME, OWN_NAME) }
                MemorySheetScores.inspections += SheetInspection.Ready(book(SheetTabGrid(tab, grid)), RowSearch.Ambiguous(candidates))
                open(scenario, Step.CONNECT)
                scenario.onActivity { activity ->
                    val sheet = sheet(activity)
                    assertEquals(activity.getString(R.string.sheet_scores_pick_row), sheet.findViewById<TextView>(R.id.prompt).text.toString())
                    assertEquals(listOf(OWN_NAME, OWN_NAME), titles(sheet))
                    assertEquals(listOf("P3110", "P3110"), captions(sheet))
                    assertTouchTargets(list(sheet), requireWidth = false)
                    list(sheet).getChildAt(1).performClick()
                }
                TestUi.eventually(idleBetween = true) {
                    scenario.onActivity { activity ->
                        val sheet = sheet(activity)
                        assertEquals(activity.getString(R.string.sheet_scores_pick_total), sheet.findViewById<TextView>(R.id.prompt).text.toString())
                        assertEquals(listOf("P3112", "70"), values(sheet))
                    }
                }
                scenario.onActivity { (it.supportFragmentManager.findFragmentByTag(SheetScoresBottomSheet.TAG) as DialogFragment).dismiss() }
                settle()

                val longTab = SheetTab(33, "Баллы за весь семестр по всем видам работ (поток лекций 2026, группы P3110–P3115)")
                val people = SheetGrid(listOf(
                    listOf("ФИО", "Итог"),
                    listOf("Константинопольская-Тестова Александра Владиславовна", "40"),
                    listOf("Тестова Анна Сергеевна", "52"),
                ))
                val template = SheetTabGrid(SheetTab(0, "Шаблон"), SheetGrid(listOf(listOf("ФИО", "Итог"))))
                MemorySheetScores.inspections += SheetInspection.Ready(book(template, SheetTabGrid(longTab, people)), RowSearch.NotFound)
                open(scenario, Step.CONNECT)
                scenario.onActivity { activity ->
                    val sheet = sheet(activity)
                    assertEquals(activity.getString(R.string.sheet_scores_pick_tab), sheet.findViewById<TextView>(R.id.prompt).text.toString())
                    assertEquals(listOf(longTab.name), titles(sheet))
                    assertTextFits(sheet)
                    list(sheet).getChildAt(0).performClick()
                }
                TestUi.eventually(idleBetween = true) {
                    scenario.onActivity { activity ->
                        val sheet = sheet(activity)
                        assertEquals(activity.getString(R.string.sheet_scores_pick_row), sheet.findViewById<TextView>(R.id.prompt).text.toString())
                        assertEquals(2, titles(sheet).size)
                        assertEquals("Константинопольская-Тестова Александра Владиславовна", titles(sheet).first())
                        assertEquals(listOf(longTab.name, longTab.name), captions(sheet))
                        assertTextFits(sheet)
                        assertTouchTargets(list(sheet), requireWidth = false)
                    }
                }
                screenshot("sheet-rows-${spec.name}")
            }
        }
    }

    @Test fun totalPickerListsManyCellsByTab() {
        Appearances.default.forEach { spec ->
            withHost(spec.toRecordbook()) { scenario ->
                val first = SheetTab(22, "P3110")
                val second = SheetTab(33, "BARS (Fall semester 2026)")
                val longPath = "Тесты к видеолекциям · " + "Дополнительное задание по разделу ".repeat(3).trim()
                fun grid(values: List<String>) = SheetGrid(listOf(
                    listOf("ИСУ") + HEADERS.mapIndexed { index, header -> if (index == 1) longPath.take(120) else header },
                    listOf("100001") + List(HEADERS.size) { "1" },
                    listOf("123456") + values,
                ))
                val values = listOf("66,3", "100%", "5A") + (4..15).map { "$it,0" }
                MemorySheetScores.scores.value = listOf(score(first, SheetColumnRef("ИТОГО баллов", 1 + HEADERS.indexOf("ИТОГО баллов"))))
                MemorySheetScores.inspections += SheetInspection.Ready(
                    book(SheetTabGrid(first, grid(values)), SheetTabGrid(second, grid(values.reversed()))), RowSearch.NotFound
                )
                open(scenario, Step.TOTAL)
                scenario.onActivity { activity ->
                    val sheet = sheet(activity)
                    assertEquals(activity.getString(R.string.sheet_scores_pick_total), sheet.findViewById<TextView>(R.id.prompt).text.toString())
                    assertEquals(32, list(sheet).adapter!!.itemCount)
                    assertTextFits(sheet)
                    val checked = list(sheet).descendants().filter { it.id == R.id.check && it.isShown }.toList()
                    assertEquals(1, checked.size)
                    assertTrue((checked.single().parent as View).isSelected)
                    assertTrue(titles(sheet).any { it.length >= 100 })
                    assertTrue(values(sheet).containsAll(listOf("66,3", "100%", "5A")))
                }
                screenshot("sheet-totals-${spec.name}")
                scenario.onActivity { list(sheet(it)).scrollToPosition(31) }
                settle()
                scenario.onActivity { activity ->
                    val sheet = sheet(activity)
                    assertTextFits(sheet)
                    assertTouchTargets(list(sheet), requireWidth = false)
                    val last = checkNotNull(list(sheet).findViewHolderForAdapterPosition(31)).itemView
                    last.performClick()
                }
                TestUi.eventually(idleBetween = true) {
                    scenario.onActivity { assertNull(it.supportFragmentManager.findFragmentByTag(SheetScoresBottomSheet.TAG)) }
                }
                assertEquals(second, MemorySheetScores.connected.single().tab)
            }
        }
    }

    private fun withHost(appearance: RecordbookPreviewActivity.Appearance, block: (ActivityScenario<RecordbookPreviewActivity>) -> Unit) {
        RecordbookPreviewActivity.appearance = appearance
        RecordbookPreviewFixtures.install(Phase.MIDDLE)
        try {
            ActivityScenario.launch<RecordbookPreviewActivity>(Intent(ApplicationProvider.getApplicationContext(), RecordbookPreviewActivity::class.java)).use {
                settle()
                block(it)
            }
        } finally {
            RecordbookPreviewFixtures.reset()
            RecordbookPreviewActivity.appearance = RecordbookPreviewActivity.Appearance()
        }
    }

    private fun open(scenario: ActivityScenario<RecordbookPreviewActivity>, step: Step) {
        scenario.onActivity { it.openSheetScores(SheetScoresArgs(SCOPE.subjectId, SCOPE.subjectName, SCOPE.periodKey, URL, step)) }
        settle()
    }

    private fun assertFailure(scenario: ActivityScenario<RecordbookPreviewActivity>, text: Int, height: Int) {
        TestUi.eventually(idleBetween = true) {
            scenario.onActivity { activity ->
                val sheet = sheet(activity)
                assertEquals(activity.getString(text), stateTitle(sheet))
                assertFalse(sheet.findViewById<View>(R.id.state_action).isShown)
                assertEquals(height, sheet.findViewById<View>(R.id.content).height)
                assertTextFits(sheet)
            }
        }
    }

    private fun sheet(activity: RecordbookPreviewActivity): View =
        checkNotNull((activity.supportFragmentManager.findFragmentByTag(SheetScoresBottomSheet.TAG) as DialogFragment).dialog)
            .window!!.decorView

    private fun list(sheet: View): RecyclerView = sheet.findViewById(R.id.options)

    private fun stateTitle(sheet: View): String = sheet.findViewById<TextView>(R.id.state_title).text.toString()

    private fun titles(sheet: View): List<String> = list(sheet).descendants()
        .filter { it.id == R.id.title && it is TextView && it.isShown && it.parent !is RecyclerView }
        .map { (it as TextView).text.toString() }.toList()

    private fun captions(sheet: View): List<String> = list(sheet).descendants()
        .filter { it.id == R.id.caption && it.isShown }.map { (it as TextView).text.toString() }.toList()

    private fun values(sheet: View): List<String> = list(sheet).descendants()
        .filter { it.id == R.id.value && it.isShown }.map { (it as TextView).text.toString() }.toList()

    private fun settle() = TestUi.settle(600)

    private fun screenshot(name: String) = Screenshots.capture("sheet-scores-screenshots", name) { settle() }

    private companion object {
        val SCOPE = ResourceScope(9001, "Математический анализ (продвинутый курс)", "2025-2")
        const val URL = "https://docs.google.com/spreadsheets/d/1SyntheticSheetForVisualTests_0123456/edit#gid=22"
        const val OWN_NAME = "Тестов Тест Тестович"
        val HEADERS = listOf("ИТОГО баллов", "Тест 2", "Оценка") + (4..15).map { "ЛР$it" }

        fun book(vararg tabs: SheetTabGrid) = SheetWorkbook(GoogleSheetUrl.parse(URL)!!, tabs.toList())

        fun score(tab: SheetTab, column: SheetColumnRef) = SheetScore(
            scope = SCOPE, url = URL, tabGid = tab.gid, tabName = tab.name, rowKey = "123456", keyColumn = 0,
            keyKind = KeyKind.ISU, column = column, value = "66,3", baseline = "66,3", tracked = true,
            status = SheetStatus.OK, updatedAt = Instant.parse("2026-06-01T09:00:00Z"),
            connectedAt = Instant.parse("2026-05-01T09:00:00Z"),
        )
    }
}

private typealias Step = SheetScoresArgs.Step
