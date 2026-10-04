package dev.alllexey.itmowidgets.feature.schedule

import android.content.Intent
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.debug.PreviewAppearance
import dev.alllexey.itmowidgets.core.schedule.LessonSlot
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import dev.alllexey.itmowidgets.feature.schedule.ui.changes.ScheduleChangesPreviewActivity
import dev.alllexey.itmowidgets.testing.Appearances
import dev.alllexey.itmowidgets.testing.toScheduleChanges
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestUi
import dev.alllexey.itmowidgets.testing.ViewChecks.assertTextFits
import dev.alllexey.itmowidgets.testing.ViewChecks.assertTouchTargets
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The real history screen over synthetic changes in memory (pair ids 1–99, September 2026); nothing is stored. */
@RunWith(AndroidJUnit4::class)
class ScheduleChangesVisualTest {

    @Test
    fun historyGroupsByDayAndMarksNewRows() {
        Appearances.default.forEach { spec ->
            preview(spec.toScheduleChanges(), history()) { scenario ->
                assertEquals(1, ScheduleChangesPreviewActivity.readCalls)
                assertHistory(scenario)
                screenshot("history-top-${spec.name}")
                scenario.onActivity { it.list().scrollToPosition(it.list().adapter!!.itemCount - 1) }
                settle()
                screenshot("history-bottom-${spec.name}")

                scenario.recreate()
                settle()
                // The rows that were unread keep their dot although everything is read now.
                assertTrue(ScheduleChangesPreviewActivity.changes.value.all(ScheduleChange::read))
                assertHistory(scenario)
                assertEquals(1, ScheduleChangesPreviewActivity.readCalls)
            }
        }
    }

    @Test
    fun emptyHistorySaysSo() {
        Appearances.default.forEach { spec ->
            preview(spec.toScheduleChanges(), emptyList()) { scenario ->
                scenario.onActivity { activity ->
                    val root = activity.fragment.requireView()
                    assertEquals(View.GONE, root.findViewById<View>(R.id.recycler_view).visibility)
                    assertEquals(View.VISIBLE, root.findViewById<View>(R.id.state_container).visibility)
                    assertEquals("Изменений нет", root.text(R.id.state_title))
                    assertEquals("За последние 30 дней", root.text(R.id.state_description))
                    val icon = root.findViewById<View>(R.id.state_icon)
                    assertEquals((64 * activity.resources.displayMetrics.density).toInt(), icon.height)
                    assertTextFits(root)
                    assertTouchTargets(root)
                }
                screenshot("empty-${spec.name}")
            }
        }
    }

    @Test
    fun longNamesWrapAtNarrowWidthAndLargeFont() {
        val long = change(
            "long", ScheduleChangeKind.UPDATED, setOf(ScheduleChangeField.TIME, ScheduleChangeField.PLACE, ScheduleChangeField.TEACHER),
            detectedAt = "2026-09-07T08:00:00Z",
            subject = "Очень длинное название дисциплины ".repeat(4).trim().take(120),
            flowName = "Поток с длинным названием 3.2 ".repeat(2).trim().take(40),
            before = slot(40, LocalDate.of(2026, 9, 8), LocalTime.of(8, 20)),
            after = slot(
                40, LocalDate.of(2026, 9, 11), LocalTime.of(17, 0), room = "Аудитория без номера",
                building = "Санкт-Петербург, Большой Сампсониевский проспект, дом 68, литера Н",
                teacherName = "Константинопольская-Преображенская Александра Вячеславовна".take(60)
            )
        )
        Appearances.default.forEach { spec ->
            preview(spec.toScheduleChanges(), listOf(long)) { scenario ->
                scenario.onActivity { activity ->
                    val row = activity.list().findViewHolderForAdapterPosition(1)!!.itemView
                    assertEquals(long.subjectName, row.text(R.id.subject))
                    assertTrue(row.lines().contains("Санкт-Петербург, Большой Сампсониевский проспект"))
                    assertTrue(row.lines().contains(long.after!!.teacherName!!))
                    assertEquals(3, row.findViewById<ViewGroup>(R.id.lines).childCount)
                    assertTrue(row.text(R.id.meta).endsWith(long.flowName!!))
                    assertTrue(row.findViewById<TextView>(R.id.subject).lineCount > 1)
                    assertTextFits(activity.fragment.requireView())
                }
                screenshot("long-${spec.name}")
            }
        }
    }

    @Test
    fun rowsAreNotClickableAndReadAsOneNode() {
        Appearances.default.forEach { spec ->
            preview(spec.toScheduleChanges(), history()) { scenario ->
                scenario.onActivity { activity ->
                    val row = activity.list().findViewHolderForAdapterPosition(1)!!.itemView
                    assertFalse(row.isClickable)
                    assertFalse(row.isLongClickable)
                    val description = row.contentDescription.toString()
                    assertTrue(description, description.startsWith("Новое. Математический анализ. Добавлена: ср, 9 сентября, 10:00"))
                    assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
                        row.findViewById<View>(R.id.content).importantForAccessibility)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) assertTrue(row.isScreenReaderFocusable)
                    val read = activity.list().findViewHolderForAdapterPosition(2)!!.itemView
                    assertFalse(read.contentDescription.toString().contains("Новое"))
                    activity.list().allRows().forEach { assertFalse(it.isClickable) }
                }
            }
        }
    }

    private fun assertHistory(scenario: ActivityScenario<ScheduleChangesPreviewActivity>) {
        val seen = mutableMapOf<Int, List<String>>()
        val count = scenario.withActivity { it.list().adapter!!.itemCount }
        assertEquals(EXPECTED.size, count)
        for (position in 0 until count) {
            scenario.onActivity { it.list().scrollToPosition(position) }
            TestUi.idle()
            scenario.onActivity { activity ->
                val item = activity.list().findViewHolderForAdapterPosition(position)!!.itemView
                seen[position] = if (item is TextView) listOf(item.text.toString()) else listOf(
                    item.text(R.id.subject), item.text(R.id.summary),
                    item.lines().takeIf { item.findViewById<View>(R.id.lines).visibility == View.VISIBLE }.orEmpty(),
                    item.text(R.id.meta),
                    if (item.findViewById<View>(R.id.new_mark).visibility == View.VISIBLE) "new" else "read"
                )
                assertTextFits(item)
            }
        }
        assertEquals(EXPECTED, (0 until count).map { seen.getValue(it) })
        scenario.onActivity { activity ->
            activity.list().scrollToPosition(0)
            assertTouchTargets(activity.fragment.requireView())
        }
        TestUi.idle()
    }

    private fun history(): List<ScheduleChange> = listOf(
        change("added", ScheduleChangeKind.ADDED, emptySet(), "2026-09-07T08:00:00Z",
            before = null, after = slot(1, LocalDate.of(2026, 9, 9), LocalTime.of(10, 0))),
        change("cancelled", ScheduleChangeKind.CANCELLED, emptySet(), "2026-09-07T06:00:00Z", subject = "Физика",
            typeId = 3, flowName = "ФИЗ ПИИКТ 3.2", before = slot(2, LocalDate.of(2026, 9, 8)), after = null, read = true),
        change("same-day", ScheduleChangeKind.UPDATED, setOf(ScheduleChangeField.TIME), "2026-09-06T12:00:00Z",
            subject = "Программирование", typeId = 2,
            before = slot(3, LocalDate.of(2026, 9, 10)), after = slot(3, LocalDate.of(2026, 9, 10), LocalTime.of(10, 0))),
        change("moved", ScheduleChangeKind.UPDATED, setOf(ScheduleChangeField.TIME, ScheduleChangeField.PLACE),
            "2026-09-06T09:00:00Z", subject = "Физика", typeId = 1,
            before = slot(4, LocalDate.of(2026, 9, 8), LocalTime.of(13, 30)),
            after = slot(4, LocalDate.of(2026, 9, 11), LocalTime.of(15, 20), room = "2202", building = "ул. Ломоносова, 9")),
        change("format", ScheduleChangeKind.UPDATED, setOf(ScheduleChangeField.FORMAT, ScheduleChangeField.PLACE),
            "2026-09-03T10:00:00Z", subject = "Английский язык", typeId = 3, flowName = null,
            before = slot(5, LocalDate.of(2026, 9, 9), LocalTime.of(11, 40)),
            after = slot(5, LocalDate.of(2026, 9, 9), LocalTime.of(11, 40), room = null, building = null, formatId = 3,
                format = "Дистанционный"), read = true),
        change("teacher", ScheduleChangeKind.UPDATED, setOf(ScheduleChangeField.TEACHER), "2026-09-03T09:00:00Z",
            subject = "Математический анализ", typeId = 3,
            before = slot(6, LocalDate.of(2026, 9, 14)),
            after = slot(6, LocalDate.of(2026, 9, 14), teacherIsu = 300002, teacherName = "Новый преподаватель"))
    )

    private fun change(
        id: String,
        kind: ScheduleChangeKind,
        fields: Set<ScheduleChangeField>,
        detectedAt: String,
        subject: String = "Математический анализ",
        typeId: Int = 1,
        flowName: String? = "Тестовый поток",
        before: LessonSlot?,
        after: LessonSlot?,
        read: Boolean = false
    ) = ScheduleChange(
        id = id, detectedAt = Instant.parse(detectedAt), kind = kind, fields = fields, subjectName = subject,
        typeId = typeId, flowName = flowName, before = before, after = after, read = read, notified = true
    )

    private fun slot(
        pairId: Long,
        date: LocalDate,
        start: LocalTime = LocalTime.of(8, 20),
        room: String? = "1506",
        building: String? = "Кронверкский проспект, 49",
        formatId: Int = 1,
        format: String? = "Очный",
        teacherIsu: Long? = 300001,
        teacherName: String? = "Тестовый преподаватель"
    ) = LessonSlot(pairId, date, start, start.plusMinutes(90), room, building, formatId, format, teacherIsu, teacherName)

    private fun preview(
        appearance: PreviewAppearance,
        changes: List<ScheduleChange>,
        block: (ActivityScenario<ScheduleChangesPreviewActivity>) -> Unit
    ) {
        ScheduleChangesPreviewActivity.appearance = appearance
        ScheduleChangesPreviewActivity.changes.value = changes
        ScheduleChangesPreviewActivity.readCalls = 0
        try {
            val intent = Intent(ApplicationProvider.getApplicationContext(), ScheduleChangesPreviewActivity::class.java)
            ActivityScenario.launch<ScheduleChangesPreviewActivity>(intent).use { scenario ->
                settle()
                block(scenario)
            }
        } finally {
            ScheduleChangesPreviewActivity.appearance = PreviewAppearance()
            ScheduleChangesPreviewActivity.changes.value = emptyList()
        }
    }

    private fun <T> ActivityScenario<ScheduleChangesPreviewActivity>.withActivity(block: (ScheduleChangesPreviewActivity) -> T): T {
        var result: T? = null
        onActivity { result = block(it) }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun ScheduleChangesPreviewActivity.list(): RecyclerView = fragment.requireView().findViewById(R.id.recycler_view)

    private fun RecyclerView.allRows(): List<View> = (0 until childCount).map(::getChildAt)

    private fun View.text(id: Int): String = findViewById<TextView>(id).text.toString()

    /** The "было → стало" lines of a row, one per changed field. */
    private fun View.lines(): String = findViewById<ViewGroup>(R.id.lines).let { lines ->
        (0 until lines.childCount).joinToString("\n") { (lines.getChildAt(it) as TextView).text }
    }

    private fun settle() = TestUi.settle(400)

    /** Dynamic colours and night mode recreate the host; capture only after it has drawn again. */
    private fun screenshot(name: String) = Screenshots.capture("schedule-changes-screenshots", name) { settle() }

    private companion object {
        val EXPECTED: List<List<String>> = listOf(
            listOf("Сегодня"),
            listOf("Математический анализ", "Добавлена: ср, 9 сентября, 10:00", "", "Лекция · Тестовый поток", "new"),
            listOf("Физика", "Отменена: вт, 8 сентября, 08:20", "", "Практика · ФИЗ ПИИКТ 3.2", "read"),
            listOf("Вчера"),
            // One changed field: its "было → стало" line is the summary, nothing repeats it below.
            listOf("Программирование", "Время: 08:20–09:50 → 10:00–11:30", "",
                "Лабораторная · Тестовый поток", "new"),
            listOf("Физика", "Перенесена на пт, 11 сентября, 15:20",
                "Время: вт, 8 сентября, 13:30 → пт, 11 сентября, 15:20\nАудитория: 1506 · Кронва → 2202 · Ломо",
                "Лекция · Тестовый поток", "new"),
            listOf("3 сентября"),
            listOf("Английский язык", "Формат: Дистанционный", "Формат: Очный → Дистанционный\nАудитория: 1506 · Кронва → —",
                "Практика", "read"),
            listOf("Математический анализ", "Преподаватель: Тестовый преподаватель → Новый преподаватель", "", "Практика · Тестовый поток", "new")
        )
    }
}
