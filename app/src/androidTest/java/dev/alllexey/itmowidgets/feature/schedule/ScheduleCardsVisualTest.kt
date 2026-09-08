package dev.alllexey.itmowidgets.feature.schedule

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.graphics.ColorUtils
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
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
import dev.alllexey.itmowidgets.feature.schedule.ui.ScheduleTimelineMarker
import dev.alllexey.itmowidgets.feature.schedule.ui.renderTimelineMarker
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleDisplayDay
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
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
    fun timelineShapesRemainDistinctInEveryPaletteAndOnRebind() {
        for (appearance in listOf(Appearance(), Appearance(dark = true),
            Appearance(fontScale = 1.3f, seed = 0xff826c24.toInt()),
            Appearance(fontScale = 1.3f, dark = true, seed = 0xff386a20.toInt()))) {
            preview(appearance.dark, appearance.fontScale, appearance.seed) { scenario ->
                scenario.onActivity { activity ->
                    val image = ImageView(activity)
                    val density = activity.resources.displayMetrics.density
                    val size = (14 * density).toInt()
                    val surface = activity.color.resolve(com.google.android.material.R.attr.colorSurfaceContainerLow)
                    for (marker in ScheduleTimelineMarker.entries + ScheduleTimelineMarker.UPCOMING) {
                        image.scaleX = 1.3f
                        image.scaleY = 0.8f
                        image.renderTimelineMarker(marker)
                        assertEquals(1f, image.scaleX, 0f)
                        assertEquals(1f, image.scaleY, 0f)
                        assertNull(image.imageTintList)
                        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                        image.drawable.setBounds(0, 0, size, size)
                        image.drawable.draw(Canvas(bitmap))
                        val ink = when (marker) {
                            ScheduleTimelineMarker.CURRENT, ScheduleTimelineMarker.NEXT -> activity.color.primary
                            else -> activity.color.outline
                        }
                        assertTrue(ColorUtils.calculateContrast(ink, surface) >= 3)
                        val hollow = marker in listOf(ScheduleTimelineMarker.UPCOMING, ScheduleTimelineMarker.AUTO_SIGN)
                        assertEquals("Center of $marker", if (hollow) surface else ink, bitmap.getPixel(size / 2, size / 2))
                        if (marker == ScheduleTimelineMarker.NEXT) {
                            assertEquals("Gap around the next-lesson dot", surface, bitmap.getPixel((4 * density).toInt(), size / 2))
                        }
                        val ring = hollow || marker == ScheduleTimelineMarker.NEXT
                        if (ring) assertEquals("Outer ring of $marker", ink, bitmap.getPixel((2 * density).toInt(), size / 2))
                        if (marker == ScheduleTimelineMarker.AUTO_SIGN) assertNull(image.contentDescription)
                        else assertFalse(image.contentDescription.isNullOrBlank())
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    @Test
    fun nextMarkerMovesAcrossUnchangedDaysAndTimeBoundaries() = preview { scenario ->
        var now = FixedTime.now()
        val clock = object : AcademicTimeProvider {
            override val zoneId = FixedTime.zoneId
            override fun today() = now.toLocalDate()
            override fun now() = now
        }
        lateinit var adapter: DayScheduleAdapter
        lateinit var list: RecyclerView
        fun day(offset: Long): ScheduleDisplayDay {
            val date = FixedTime.today().plusDays(offset)
            return ScheduleDisplayDay(date, DaySchedule(date.dayOfWeek.value, 1, date, null,
                listOf(lesson().copy(subjectName = "Тестовая пара", note = null, teacherFio = null, room = null, building = null))))
        }
        val first = day(1)
        val second = day(2)
        val third = day(3)
        scenario.onActivity { activity ->
            adapter = DayScheduleAdapter(clock)
            list = RecyclerView(activity).apply {
                layoutManager = LinearLayoutManager(activity)
                itemAnimator = null
                this.adapter = adapter
            }
            activity.setContentView(list)
            adapter.submitList(listOf(second, third))
        }
        fun assertMarker(position: Int, description: Int) {
            settle()
            scenario.onActivity { activity ->
                val holder = list.findViewHolderForAdapterPosition(position) as DayScheduleAdapter.DayViewHolder
                assertEquals(activity.getString(description), holder.lessonList.findViewById<ImageView>(R.id.timeline_dot).contentDescription)
            }
        }
        assertMarker(0, R.string.schedule_timeline_next)
        scenario.onActivity { adapter.submitList(listOf(first, second, third)) }
        assertMarker(0, R.string.schedule_timeline_next)
        assertMarker(1, R.string.schedule_timeline_upcoming)
        scenario.onActivity { adapter.submitList(listOf(second, third)) }
        assertMarker(0, R.string.schedule_timeline_next)
        scenario.onActivity {
            now = second.date.atTime(8, 20).atZone(clock.zoneId).toOffsetDateTime()
            adapter.updateLessonStates()
        }
        assertMarker(0, R.string.schedule_timeline_current)
        assertMarker(1, R.string.schedule_timeline_next)
        scenario.onActivity {
            now = now.withHour(9).withMinute(50)
            adapter.updateLessonStates()
        }
        assertMarker(0, R.string.schedule_timeline_completed)
    }

    @Test
    fun recycledLessonsRestoreAllStateAndKeepTimeAndContentOpaque() {
        for (dark in listOf(false, true)) preview(dark = dark) { scenario ->
            scenario.onActivity { activity ->
                val states = listOf(
                    ScheduleItem.LessonState.COMPLETED,
                    ScheduleItem.LessonState.CURRENT,
                    ScheduleItem.LessonState.NEXT,
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
                    ScheduleDisplayDay(date, DaySchedule(date.dayOfWeek.value, 1, date, null, listOf(lesson())))
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
                    val row = checkNotNull(holder.lessonList.findViewById<View>(R.id.item_root))
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
    fun pendingSportSharesLessonAnatomyWithAnUnconfirmedMarkerAndStatus() {
        val appearances = listOf(
            Appearance(), Appearance(dark = true),
            Appearance(widthDp = 320, fontScale = 1.3f, seed = 0xff826c24.toInt()),
            Appearance(widthDp = 320, fontScale = 1.3f, dark = true, seed = 0xff386a20.toInt())
        )
        appearances.forEachIndexed { index, appearance ->
            preview(appearance.dark, appearance.fontScale, appearance.seed) { scenario ->
                for (pendingOnly in listOf(false, true)) {
                    lateinit var holder: DayScheduleAdapter.DayViewHolder
                    lateinit var scroll: ScrollView
                    val seenStatuses = mutableSetOf<String>()
                    fun checkVisiblePendingRows() {
                        val activity = holder.itemView.context
                        assertTextFits(holder.itemView)
                        val lastRow = holder.lessonList.getChildAt(holder.lessonList.childCount - 1)
                        assertTrue("The last lesson must fit inside the measured day", lastRow.bottom <= holder.lessonList.height)
                        val rows = holder.lessonList.descendants().filter { it.id == R.id.pending_sport_root }.toList()
                        rows.forEach { row ->
                            assertFalse(row.descendants().any { it.isClickable })
                            assertEquals(1f, row.alpha, 0f)
                            assertEquals(activity.color.onSurface, row.findViewById<TextView>(R.id.time_start).currentTextColor)
                            assertTrue(row.findViewById<TextView>(R.id.title).typeface.isBold)
                            assertEquals(View.VISIBLE, row.findViewById<View>(R.id.teacher_layout).visibility)
                            assertEquals(View.VISIBLE, row.findViewById<View>(R.id.location_layout).visibility)
                            for (id in listOf(R.id.teacher_layout, R.id.location_layout)) {
                                row.findViewById<ViewGroup>(id).descendants().filterIsInstance<ImageView>().forEach {
                                    assertEquals(activity.color.onSurfaceVariant, it.imageTintList!!.defaultColor)
                                }
                            }
                            val status = row.findViewById<TextView>(R.id.type)
                            seenStatuses += status.text.toString()
                            assertEquals(activity.getString(if (status.text.toString() == activity.getString(R.string.schedule_auto_sign_prediction))
                                R.string.schedule_auto_sign_prediction_description else R.string.schedule_auto_sign_waiting_description),
                                status.contentDescription.toString())
                        }
                    }
                    scenario.onActivity { activity ->
                        val date = FixedTime.today().plusDays(if (pendingOnly) 1 else 0)
                        val start = date.atTime(16, 0).atZone(FixedTime.zoneId).toOffsetDateTime()
                        val waiting = PendingSportBooking(
                            queueId = 1, queueKind = PendingSportBooking.QueueKind.FREE, lessonId = 100,
                            sectionName = "Современные танцы — тестовая секция с длинным названием",
                            start = start, end = start.plusMinutes(90), teacherFio = "Тестовый преподаватель с длинным именем",
                            roomName = "Тестовый корпус на Кронверкском проспекте, 49, спортивный зал", isPrediction = false
                        )
                        val prediction = waiting.copy(queueId = 2, queueKind = PendingSportBooking.QueueKind.AUTO,
                            lessonId = 200, start = start.plusHours(2), end = start.plusHours(3), isPrediction = true)
                        val official = if (pendingOnly) null else DaySchedule(date.dayOfWeek.value, 1, date, null,
                            listOf(lesson(), lesson().copy(pairId = 2, start = LocalTime.of(20, 0), end = LocalTime.of(21, 30))))
                        val adapter = DayScheduleAdapter(FixedTime)
                        adapter.submitList(listOf(ScheduleDisplayDay(date, official, listOf(waiting, prediction))))
                        val frame = FrameLayout(activity).apply { setBackgroundColor(activity.color.surface) }
                        scroll = ScrollView(activity)
                        holder = adapter.onCreateViewHolder(scroll, 0)
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
                    screenshot("pending-$pendingOnly-$index")
                    scenario.onActivity { activity ->
                        // Every row contributes its full height; verify both ends of
                        // the actual scrollable card, including the final prediction.
                        checkVisiblePendingRows()
                        assertEquals(if (pendingOnly) 2 else 4, holder.lessonList.childCount)
                        assertEquals(if (pendingOnly) activity.getString(R.string.schedule_auto_sign_label)
                            else activity.resources.getQuantityString(R.plurals.schedule_lesson_count, 2, 2),
                            holder.numberOfLessons.text.toString())
                        assertNull(holder.lessonList.findViewById<View>(R.id.break_text))
                        scroll.fullScroll(View.FOCUS_DOWN)
                    }
                    settle()
                    screenshot("pending-$pendingOnly-$index-bottom")
                    scenario.onActivity { activity ->
                        checkVisiblePendingRows()
                        assertEquals(setOf(activity.getString(R.string.schedule_auto_sign_waiting),
                            activity.getString(R.string.schedule_auto_sign_prediction)), seenStatuses)
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
                        ))).map { ScheduleDisplayDay(it.date, it) })
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
                        assertTrue(holder.lessonList.childCount >= 3)
                        val guides = holder.lessonList.descendants().filter {
                            it.id == R.id.timeline_guide || it.id == R.id.timeline_guide_break
                        }.map { (it.layoutParams as ConstraintLayout.LayoutParams).guideBegin }.toSet()
                        assertEquals("Lessons and breaks share one timeline gutter", 1, guides.size)
                        holder.lessonList.descendants().filter { it.id == R.id.item_root }.forEach { root ->
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
            ScheduleItem.LessonState.UPCOMING, ScheduleItem.LessonState.NEXT -> colors.onSurface
        }, start.currentTextColor)
        assertEquals(colors.onSurfaceVariant, end.currentTextColor)
        val expectedScale = 1f
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
        instrumentation.runOnMainSync {
            // Capture the actual laid-out test window, not unrelated phone notifications.
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<SettingsPreviewActivity>().single()
            val view = activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val folder = File(instrumentation.targetContext.externalCacheDir, "schedule-cards-screenshots").apply { mkdirs() }
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
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
