package dev.alllexey.itmowidgets.feature.schedule

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Room
import dev.alllexey.itmowidgets.feature.schedule.ui.DayScheduleAdapter
import dev.alllexey.itmowidgets.feature.schedule.ui.LessonAdapter
import dev.alllexey.itmowidgets.feature.schedule.ui.ScheduleItem
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsPreviewActivity
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleCardsVisualTest {
    @Test
    fun recycledLessonsRestoreAllStateAndKeepTimeAndContentOpaque() {
        for (dark in listOf(false, true)) preview(dark = dark) { scenario ->
            scenario.onActivity { activity ->
                val states = listOf(
                    ScheduleItem.LessonState.COMPLETED,
                    ScheduleItem.LessonState.CURRENT,
                    ScheduleItem.LessonState.COMPLETED,
                    ScheduleItem.LessonState.UPCOMING
                )
                val adapter = LessonAdapter(states.map { ScheduleItem.LessonItem(lesson(), it, true) })
                val holder = adapter.onCreateViewHolder(FrameLayout(activity), adapter.getItemViewType(0))
                states.forEachIndexed { index, state ->
                    val root = holder.itemView
                    root.alpha = 0.72f
                    root.findViewById<View>(R.id.card_container).alpha = 0.7f
                    adapter.onBindViewHolder(holder, index)
                    assertLessonState(root, state)
                    for (id in listOf(R.id.teacher_layout, R.id.note_layout, R.id.location_layout)) {
                        root.findViewById<ViewGroup>(id).descendants().filterIsInstance<ImageView>().forEach {
                            assertEquals(activity.color.onSurfaceVariant, it.imageTintList!!.defaultColor)
                            assertNull(it.contentDescription)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun recycledDayHolderFadesPastDaysOnceAndRestoresTodayAndFuture() {
        for (dark in listOf(false, true)) preview(dark = dark) { scenario ->
            lateinit var adapter: DayScheduleAdapter
            lateinit var holder: DayScheduleAdapter.DayViewHolder
            scenario.onActivity { activity ->
                adapter = DayScheduleAdapter(FixedTime)
                adapter.submitList(listOf(-1L, 0L, 1L).map { offset ->
                    val date = FixedTime.today().plusDays(offset)
                    DaySchedule(date.dayOfWeek.value, 1, date, null, listOf(lesson()))
                })
                val frame = FrameLayout(activity)
                holder = adapter.onCreateViewHolder(frame, 0)
                frame.addView(holder.itemView)
                activity.setContentView(frame)
            }
            for (position in listOf(0, 1, 2, 0)) {
                scenario.onActivity { adapter.onBindViewHolder(holder, position) }
                settle()
                scenario.onActivity { activity ->
                    val expectedAlpha = if (position == 0) 0.72f else 1f
                    assertEquals(expectedAlpha, holder.itemRoot.alpha, 0f)
                    assertEquals(if (position == 1) activity.color.primary else activity.color.onSurface,
                        holder.dayTitle.currentTextColor)
                    val row = checkNotNull(holder.innerRecyclerView.findViewById<View>(R.id.item_root))
                    assertEquals(1f, row.alpha, 0f)
                    assertEquals(1f, row.findViewById<View>(R.id.card_container).alpha, 0f)
                    for (id in listOf(R.id.title, R.id.time_start, R.id.time_end, R.id.location_building)) {
                        assertEquals("Only the day applies opacity to $id", expectedAlpha,
                            effectiveAlpha(row.findViewById(id), holder.itemRoot), 0.001f)
                    }
                }
            }
        }
    }

    @Test
    fun scheduleDaysRemainReadableAcrossThemesNarrowWidthsAndLargeFonts() {
        val appearances = listOf(
            Appearance(),
            Appearance(dark = true),
            Appearance(widthDp = 320, fontScale = 1.3f, seed = 0xff826c24.toInt()),
            Appearance(widthDp = 320, fontScale = 1.3f, dark = true, seed = 0xff386a20.toInt())
        )
        appearances.forEachIndexed { index, appearance ->
            preview(appearance.dark, appearance.fontScale, appearance.seed) { scenario ->
                for (offset in listOf(-1L, 0L, 1L)) {
                    lateinit var holder: DayScheduleAdapter.DayViewHolder
                    lateinit var scroll: ScrollView
                    scenario.onActivity { activity ->
                        val date = FixedTime.today().plusDays(offset)
                        val adapter = DayScheduleAdapter(FixedTime)
                        adapter.submitList(listOf(DaySchedule(date.dayOfWeek.value, 1, date, null, listOf(
                            lesson(),
                            lesson().copy(pairId = 2, start = LocalTime.of(11, 40), end = LocalTime.of(13, 10)),
                            lesson().copy(pairId = 3, start = LocalTime.of(15, 20), end = LocalTime.of(16, 50))
                        ))))
                        val frame = FrameLayout(activity).apply { setBackgroundColor(activity.color.surface) }
                        scroll = ScrollView(activity)
                        holder = adapter.onCreateViewHolder(scroll, 0)
                        holder.itemRoot.alpha = 0.72f
                        adapter.onBindViewHolder(holder, 0)
                        scroll.addView(holder.itemView)
                        frame.addView(scroll, FrameLayout.LayoutParams(
                            if (appearance.widthDp > 0) (appearance.widthDp * activity.resources.displayMetrics.density).toInt() else -1,
                            -1, Gravity.CENTER_HORIZONTAL
                        ))
                        activity.setContentView(frame)
                        ViewCompat.setOnApplyWindowInsetsListener(frame) { view, insets ->
                            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                            insets
                        }
                        ViewCompat.requestApplyInsets(frame)
                    }
                    settle()
                    screenshot("day-$offset-appearance-$index")
                    scenario.onActivity {
                        assertEquals(if (offset < 0) 0.72f else 1f, holder.itemRoot.alpha, 0f)
                        assertTextFits(holder.itemView)
                        assertTrue(holder.innerRecyclerView.childCount >= 3)
                        val guides = holder.innerRecyclerView.descendants().filter {
                            it.id == R.id.timeline_guide || it.id == R.id.timeline_guide_break
                        }.map { (it.layoutParams as ConstraintLayout.LayoutParams).guideBegin }.toSet()
                        assertEquals("Lessons and breaks share one timeline gutter", 1, guides.size)
                        holder.innerRecyclerView.descendants().filter { it.id == R.id.item_root }.forEach { root ->
                            assertEquals(1f, root.alpha, 0f)
                            assertEquals(1f, root.findViewById<View>(R.id.card_container).alpha, 0f)
                        }
                        scroll.fullScroll(View.FOCUS_DOWN)
                    }
                    settle()
                    screenshot("day-$offset-appearance-$index-bottom")
                }
            }
        }
    }

    private fun effectiveAlpha(view: View, ancestor: View): Float {
        var alpha = 1f
        var current = view
        while (true) {
            alpha *= current.alpha
            if (current === ancestor) return alpha
            current = current.parent as View
        }
    }

    private fun assertLessonState(root: View, state: ScheduleItem.LessonState) {
        val colors = root.context.color
        val title = root.findViewById<TextView>(R.id.title)
        val start = root.findViewById<TextView>(R.id.time_start)
        val end = root.findViewById<TextView>(R.id.time_end)
        assertEquals("08:20", start.text.toString())
        assertEquals("09:50", end.text.toString())
        assertEquals(1f, root.alpha, 0f)
        assertEquals(1f, root.findViewById<View>(R.id.card_container).alpha, 0f)
        assertEquals(1f, root.findViewById<View>(R.id.location_building).alpha, 0f)
        assertEquals(1f, start.alpha, 0f)
        assertEquals(1f, end.alpha, 0f)
        assertEquals(if (state == ScheduleItem.LessonState.COMPLETED) colors.onSurfaceVariant else colors.onSurface, title.currentTextColor)
        assertEquals(when (state) {
            ScheduleItem.LessonState.CURRENT -> colors.primary
            ScheduleItem.LessonState.COMPLETED -> colors.onSurfaceVariant
            ScheduleItem.LessonState.UPCOMING -> colors.onSurface
        }, start.currentTextColor)
        assertEquals(colors.onSurfaceVariant, end.currentTextColor)
        val expectedScale = when (state) {
            ScheduleItem.LessonState.CURRENT -> 1.3f
            ScheduleItem.LessonState.COMPLETED -> 0.8f
            ScheduleItem.LessonState.UPCOMING -> 1f
        }
        val dot = root.findViewById<View>(R.id.timeline_dot)
        assertEquals(expectedScale, dot.scaleX, 0f)
        assertEquals(expectedScale, dot.scaleY, 0f)
        val surface = colors.resolve(com.google.android.material.R.attr.colorSurfaceContainerLow)
        for (text in listOf(title, start, end)) {
            assertTrue("Text contrast: ${text.text}", ColorUtils.calculateContrast(text.currentTextColor, surface) >= 4.5)
        }
    }

    private fun assertTextFits(root: View) {
        root.descendants().filterIsInstance<TextView>().filter { it.visibility == View.VISIBLE && it.text.isNotEmpty() }.forEach { text ->
            val layout = text.layout ?: return@forEach
            assertTrue("Height: ${text.text}", layout.height <= text.height - text.compoundPaddingTop - text.compoundPaddingBottom)
            for (line in 0 until layout.lineCount) {
                assertTrue("Width: ${text.text}", layout.getLineMax(line) <= text.width - text.compoundPaddingLeft - text.compoundPaddingRight + 1)
            }
            val parent = text.parent as? ViewGroup ?: return@forEach
            assertTrue("Left edge: ${text.text}", text.left >= 0)
            assertTrue("Right edge: ${text.text}", text.right <= parent.width)
        }
    }

    private fun preview(dark: Boolean = false, fontScale: Float = 1f, seed: Int? = null, block: (ActivityScenario<SettingsPreviewActivity>) -> Unit) {
        SettingsPreviewActivity.appearance = SettingsPreviewActivity.Appearance(fontScale, dark)
        try {
            val intent = Intent(ApplicationProvider.getApplicationContext(), SettingsPreviewActivity::class.java)
            seed?.let { intent.putExtra(SettingsPreviewActivity.EXTRA_COLOR_SEED, it) }
            ActivityScenario.launch<SettingsPreviewActivity>(intent).use(block)
        } finally {
            SettingsPreviewActivity.appearance = SettingsPreviewActivity.Appearance()
        }
    }

    private fun settle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        SystemClock.sleep(300)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val folder = File(instrumentation.targetContext.externalCacheDir, "schedule-cards-screenshots").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) (0 until childCount).forEach { yieldAll(getChildAt(it).descendants()) }
    }

    private fun lesson() = Lesson(
        pairId = 1, start = LocalTime.of(8, 20), end = LocalTime.of(9, 50), type = "Лекция", typeId = Lesson.TypeId(1),
        note = "Организационная информация о занятии", subjectName = "Математический анализ (продвинутый уровень)",
        subjectId = 1, groupName = "Тестовая группа", flowId = 1, flowTypeId = 2, teacherIsu = null,
        teacherFio = "Тестовый преподаватель с длинным именем", room = Room("1506"),
        building = Building("Кронверкский проспект, 49"), buildingId = 13, mainBuildingId = 13,
        format = "Очный", formatId = 1, zoomUrl = null, zoomPassword = null, zoomInfo = null
    )

    private data class Appearance(val widthDp: Int = 0, val fontScale: Float = 1f, val dark: Boolean = false, val seed: Int? = null)

    private object FixedTime : AcademicTimeProvider {
        override val zoneId: ZoneId = ZoneId.of("Europe/Moscow")
        override fun today(): LocalDate = LocalDate.of(2026, 9, 7)
        override fun now() = today().atTime(12, 0).atZone(zoneId).toOffsetDateTime()
    }
}
