package dev.alllexey.itmowidgets.feature.schedule

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.core.graphics.ColorUtils
import androidx.core.widget.RemoteViewsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.core.settings.LessonStyle
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.core.ui.widget.rolesFor
import dev.alllexey.itmowidgets.designsystem.theme.widgetPalette
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.*
import dev.alllexey.itmowidgets.feature.schedule.ui.widget.ScheduleWidgetRenderer
import dev.alllexey.itmowidgets.feature.schedule.ui.widget.ScheduleListRowRenderer
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsPreviewActivity
import dev.alllexey.itmowidgets.testing.Appearances
import dev.alllexey.itmowidgets.testing.toSettingsPreview
import dev.alllexey.itmowidgets.testing.Screenshots
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class ScheduleWidgetRenderingTest {
    @Test
    fun todayAndTomorrowHeadersAreCenteredInActualWidgetRows() {
        for (spec in Appearances.default) {
            SettingsPreviewActivity.appearance = spec.toSettingsPreview()
            ActivityScenario.launch(SettingsPreviewActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val renderer = ScheduleListRowRenderer(activity)
                    val parent = FrameLayout(activity)
                    val today = ScheduleListWidgetItem(
                        kind = ScheduleListWidgetItemKind.HEADER, dateIso = "2026-09-07"
                    )
                    val remote = checkNotNull(renderer.render(today, LessonStyle.DOT))
                    val root = remote.apply(activity, parent)
                    val width = (320 * activity.resources.displayMetrics.density).toInt()
                    for (tomorrow in listOf(false, true)) {
                        checkNotNull(renderer.render(today.copy(tomorrow = tomorrow), LessonStyle.DOT))
                            .reapply(activity, root)
                        root.measure(
                            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                        )
                        root.layout(0, 0, width, root.measuredHeight)
                        val title = root.findViewById<TextView>(R.id.day_title)
                        assertEquals(Gravity.CENTER_HORIZONTAL, title.gravity and Gravity.HORIZONTAL_GRAVITY_MASK)
                        assertEquals(width / 2f, title.left + title.width / 2f, 1f)
                        assertTrue(title.text.startsWith(activity.getString(
                            if (tomorrow) R.string.schedule_widget_tomorrow else R.string.schedule_widget_today
                        )))
                        assertTrue(title.layout.height <= title.height - title.compoundPaddingTop - title.compoundPaddingBottom)
                    }
                }
            }
        }
    }

    @Test
    fun completedLessonsFadeTimeAndContentTogetherAndResetOnReuse() {
        ActivityScenario.launch(SettingsPreviewActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                for (style in LessonStyle.entries) {
                    val parent = FrameLayout(activity)
                    val xml = activity.layoutInflater.inflate(
                        if (style == LessonStyle.DOT) R.layout.item_lesson_list_entry_dot else R.layout.item_lesson_list_entry_dash,
                        parent,
                        false
                    )
                    val remote = ScheduleWidgetRenderer.lessonListRow(activity, lesson(ScheduleWidgetLessonState.COMPLETED), style)
                    val row = remote.apply(activity, parent)
                    assertFade(row, xml, rowTextIds, 0.62f)
                    assertEquals(1f, row.findViewById<View>(R.id.lesson_content).alpha, 0f)
                    assertEquals(1f, row.findViewById<View>(R.id.time_column).alpha, 0f)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        // A launcher may reuse a row rendered before whole-row fading was added.
                        row.findViewById<View>(R.id.lesson_content).alpha = 0.62f
                    }
                    ScheduleWidgetRenderer.lessonListRow(activity, lesson(ScheduleWidgetLessonState.CURRENT), style).reapply(activity, row)
                    assertFade(row, xml, rowTextIds, 1f)
                    assertEquals(1f, row.findViewById<View>(R.id.lesson_content).alpha, 0f)
                    ScheduleWidgetRenderer.lessonListRow(activity, lesson(ScheduleWidgetLessonState.COMPLETED), style).reapply(activity, row)
                    assertFade(row, xml, rowTextIds, 0.62f)

                    val singleXml = activity.layoutInflater.inflate(
                        if (style == LessonStyle.DOT) R.layout.widget_single_lesson_dot else R.layout.widget_single_lesson_dash,
                        parent,
                        false
                    )
                    val completed = ScheduleWidgetSnapshot(
                        SingleLessonWidgetContent(SingleLessonWidgetKind.LESSON, lesson(ScheduleWidgetLessonState.COMPLETED)),
                        emptyList(), style, style
                    )
                    val single = ScheduleWidgetRenderer.singleLessonViews(activity, completed).apply(activity, parent)
                    val content = single.findViewById<View>(R.id.lesson_content)
                    assertFade(content, singleXml, singleTextIds, 0.62f)
                    val current = completed.copy(singleLesson = SingleLessonWidgetContent(
                        SingleLessonWidgetKind.LESSON, lesson(ScheduleWidgetLessonState.CURRENT)
                    ))
                    ScheduleWidgetRenderer.singleLessonViews(activity, current).reapply(activity, single)
                    assertFade(content, singleXml, singleTextIds, 1f)
                }
            }
        }
    }

    @Test
    fun theAppThemeColoursTheWidgetAndAReusedViewDropsItWhenTheSwitchGoesOff() {
        for (spec in Appearances.default) {
            SettingsPreviewActivity.appearance = spec.toSettingsPreview()
            val intent = Intent(ApplicationProvider.getApplicationContext(), SettingsPreviewActivity::class.java)
            ActivityScenario.launch<SettingsPreviewActivity>(intent).use { scenario ->
                scenario.onActivity { activity ->
                    val parent = FrameLayout(activity)
                    val xml = activity.layoutInflater.inflate(R.layout.widget_single_lesson_dot, parent, false)
                    val palette = ThemeSpec(accent = AccentColor.TEAL).widgetPalette()
                    val roles = palette.rolesFor(activity)
                    val current = ScheduleWidgetSnapshot(
                        SingleLessonWidgetContent(SingleLessonWidgetKind.LESSON, lesson(ScheduleWidgetLessonState.CURRENT)),
                        emptyList(), LessonStyle.DOT, LessonStyle.DOT
                    )
                    val root = ScheduleWidgetRenderer.singleLessonViews(activity, current, palette).apply(activity, parent)

                    assertEquals(Color.BLACK or roles.onSurface, root.findViewById<TextView>(R.id.title).currentTextColor)
                    assertEquals(Color.BLACK or roles.primary, root.findViewById<TextView>(R.id.more_lessons_text).currentTextColor)
                    assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widget_theme_surface).visibility)
                    assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widget_theme_outline).visibility)

                    ScheduleWidgetRenderer.singleLessonViews(activity, current).reapply(activity, root)

                    singleTextIds.forEach { id ->
                        assertEquals("$id", xml.findViewById<TextView>(id).currentTextColor, root.findViewById<TextView>(id).currentTextColor)
                    }
                    assertEquals(View.GONE, root.findViewById<View>(R.id.widget_theme_surface).visibility)
                    assertEquals(View.GONE, root.findViewById<View>(R.id.widget_theme_outline).visibility)
                }
            }
        }
    }

    @Test
    fun emptyMessageIsCenteredInBothStylesAndLessonLayoutRestoresOnReuse() {
        for (spec in Appearances.default) {
            SettingsPreviewActivity.appearance = spec.toSettingsPreview()
            val intent = Intent(ApplicationProvider.getApplicationContext(), SettingsPreviewActivity::class.java)
            ActivityScenario.launch<SettingsPreviewActivity>(intent).use { scenario ->
                scenario.onActivity { activity ->
                    for (style in LessonStyle.entries) {
                        val snapshot = ScheduleWidgetSnapshot(
                            SingleLessonWidgetContent(SingleLessonWidgetKind.NO_MORE_TODAY), emptyList(), style, style
                        )
                        val root = ScheduleWidgetRenderer.singleLessonViews(activity, snapshot).apply(activity, FrameLayout(activity))
                        val width = (320 * activity.resources.displayMetrics.density).toInt()
                        val height = (100 * activity.resources.displayMetrics.density).toInt()
                        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                        root.layout(0, 0, width, height)
                        val message = root.findViewById<View>(R.id.widget_message)
                        assertEquals(View.GONE, root.findViewById<View>(R.id.lesson_content).visibility)
                        assertEquals(width / 2f, message.left + message.width / 2f, 1f)
                        assertEquals(height / 2f, message.top + message.height / 2f, 1f)
                        for (id in listOf(R.id.widget_message_title, R.id.widget_message_hint)) {
                            val text = root.findViewById<TextView>(id)
                            assertEquals(Gravity.CENTER, text.gravity)
                            assertTrue(text.layout.height <= text.height - text.compoundPaddingTop - text.compoundPaddingBottom)
                        }
                        Screenshots.save("widget-message-screenshots", "${style.name}-${spec.name}") {
                            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { root.draw(Canvas(it)) }
                        }
                        val populated = snapshot.copy(singleLesson = SingleLessonWidgetContent(SingleLessonWidgetKind.LESSON, lesson(ScheduleWidgetLessonState.CURRENT)))
                        ScheduleWidgetRenderer.singleLessonViews(activity, populated).reapply(activity, root)
                        assertEquals(View.GONE, message.visibility)
                        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.lesson_content).visibility)
                        assertEquals(Gravity.START or Gravity.TOP, root.findViewById<TextView>(R.id.title).gravity)
                    }
                }
            }
        }
    }

    @Test
    fun pendingRowsUseBothWidgetLayoutsAndKeepUnconfirmedStatusAcrossReuse() {
        for (spec in Appearances.default) {
            SettingsPreviewActivity.appearance = spec.toSettingsPreview()
            ActivityScenario.launch(SettingsPreviewActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val width = (320 * activity.resources.displayMetrics.density).toInt()
                    for (style in LessonStyle.entries) for (status in ScheduleWidgetPendingStatus.entries) {
                        val pending = lesson(ScheduleWidgetLessonState.UPCOMING).copy(
                            subject = "Функциональная тренировка и подготовка", pendingStatus = status, typeId = 11
                        )
                        val snapshot = ScheduleWidgetSnapshot(
                            SingleLessonWidgetContent(SingleLessonWidgetKind.LESSON, pending), emptyList(), style, style
                        )
                        for (single in listOf(false, true)) {
                            val remote = if (single) ScheduleWidgetRenderer.singleLessonViews(activity, snapshot)
                                else ScheduleWidgetRenderer.lessonListRow(activity, pending, style)
                            val root = remote.apply(activity, FrameLayout(activity))
                            // Match the real Settings preview's AppCompat Activity inflater,
                            // while retaining RemoteViews-compatible framework image methods.
                            assertEquals(android.widget.ImageView::class.java,
                                root.findViewById<View>(R.id.type_indicator).javaClass)
                            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                            root.layout(0, 0, width, root.measuredHeight)
                            val type = root.findViewById<TextView>(R.id.type)
                            assertEquals(activity.getString(if (status == ScheduleWidgetPendingStatus.PREDICTED)
                                R.string.schedule_widget_pending_prediction else R.string.schedule_widget_pending_waiting), type.text)
                            assertEquals(activity.getString(if (status == ScheduleWidgetPendingStatus.PREDICTED)
                                R.string.schedule_auto_sign_prediction_description
                                else R.string.schedule_auto_sign_waiting_description), type.contentDescription)
                            assertEquals(0, type.layout.getEllipsisCount(0))
                            Screenshots.save("widget-pending-screenshots", "$single-${style.name}-${status.name}-${spec.name}") {
                                Bitmap.createBitmap(width, root.height, Bitmap.Config.ARGB_8888).also { root.draw(Canvas(it)) }
                            }
                            val official = pending.copy(pendingStatus = null)
                            if (single) ScheduleWidgetRenderer.singleLessonViews(activity, snapshot.copy(
                                singleLesson = SingleLessonWidgetContent(SingleLessonWidgetKind.LESSON, official)
                            )).reapply(activity, root)
                            else ScheduleWidgetRenderer.lessonListRow(activity, official, style).reapply(activity, root)
                            assertEquals(activity.getString(R.string.title_sport), type.text)
                            assertNull(type.contentDescription)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun textSizeScalesEveryLabelFromTheLayoutSizesAndResetsOnReuse() {
        // Linear sp: non-linear font scaling on large font scales would break the ratio check.
        SettingsPreviewActivity.appearance = Appearances.light.toSettingsPreview()
        ActivityScenario.launch(SettingsPreviewActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val parent = FrameLayout(activity)
                val width = (320 * activity.resources.displayMetrics.density).toInt()
                val singleIds = listOf(
                    R.id.type, R.id.time_start, R.id.time_separator, R.id.time_end, R.id.title,
                    R.id.secondary_text, R.id.more_lessons_text, R.id.widget_message_title, R.id.widget_message_hint
                )
                for (style in LessonStyle.entries) {
                    val normal = ScheduleWidgetSnapshot(
                        singleLesson = SingleLessonWidgetContent(SingleLessonWidgetKind.LESSON, lesson(ScheduleWidgetLessonState.CURRENT)),
                        lessonList = emptyList(),
                        singleLessonStyle = style,
                        lessonListStyle = style
                    )
                    // The layout's own sizes, untouched by the renderer.
                    val xml = activity.layoutInflater.inflate(
                        if (style == LessonStyle.DOT) R.layout.widget_single_lesson_dot else R.layout.widget_single_lesson_dash,
                        parent,
                        false
                    )
                    val root = ScheduleWidgetRenderer.singleLessonViews(activity, normal).apply(activity, parent)
                    singleIds.forEach { id -> assertEquals("$style $id", xml.textSize(id), root.textSize(id), 0.5f) }

                    ScheduleWidgetRenderer.singleLessonViews(activity, normal.copy(compactTextSize = WidgetTextSize.EXTRA_LARGE))
                        .reapply(activity, root)
                    singleIds.forEach { id -> assertEquals("$style $id", xml.textSize(id) * 1.4f, root.textSize(id), 1f) }

                    // A launcher reuses the view: a later normal snapshot must drop the size again.
                    ScheduleWidgetRenderer.singleLessonViews(activity, normal).reapply(activity, root)
                    singleIds.forEach { id -> assertEquals("$style $id", xml.textSize(id), root.textSize(id), 0.5f) }
                }

                val rowIds = listOf(R.id.time_start, R.id.time_end, R.id.title, R.id.type, R.id.secondary_text)
                for (style in LessonStyle.entries) {
                    val layout = if (style == LessonStyle.DOT) R.layout.item_lesson_list_entry_dot else R.layout.item_lesson_list_entry_dash
                    val xml = activity.layoutInflater.inflate(layout, parent, false)
                    val row = ScheduleWidgetRenderer.lessonListRow(activity, lesson(ScheduleWidgetLessonState.CURRENT), style).apply(activity, parent)
                    rowIds.forEach { id -> assertEquals("$style $id", xml.textSize(id), row.textSize(id), 0.5f) }

                    ScheduleWidgetRenderer.lessonListRow(activity, lesson(ScheduleWidgetLessonState.CURRENT), style, WidgetTextSize.EXTRA_LARGE)
                        .reapply(activity, row)
                    rowIds.forEach { id -> assertEquals("$style $id", xml.textSize(id) * 1.4f, row.textSize(id), 1f) }
                    // The time column follows its text instead of clipping "13:30" at a fixed width.
                    row.layoutAt(width)
                    val timeStart = row.findViewById<TextView>(R.id.time_start)
                    assertTrue(row.findViewById<View>(R.id.time_column).width >= 44 * activity.resources.displayMetrics.density - 1)
                    assertEquals(0, timeStart.layout.getEllipsisCount(0))
                    assertTrue(timeStart.layout.getLineMax(0) <= timeStart.width - timeStart.compoundPaddingLeft - timeStart.compoundPaddingRight + 1)
                    assertTrue(row.findViewById<TextView>(R.id.title).width > 0)
                }

                val renderer = ScheduleListRowRenderer(activity)
                val header = checkNotNull(renderer.render(
                    ScheduleListWidgetItem(ScheduleListWidgetItemKind.HEADER, dateIso = "2026-09-07"), LessonStyle.DOT, WidgetTextSize.EXTRA_LARGE
                )).apply(activity, parent)
                val headerXml = activity.layoutInflater.inflate(R.layout.item_lesson_list_day_title, parent, false)
                assertEquals(headerXml.textSize(R.id.day_title) * 1.4f, header.textSize(R.id.day_title), 1f)
                val empty = checkNotNull(renderer.render(
                    ScheduleListWidgetItem(ScheduleListWidgetItemKind.EMPTY_TODAY), LessonStyle.DOT, WidgetTextSize.EXTRA_LARGE
                )).apply(activity, parent)
                val emptyXml = activity.layoutInflater.inflate(R.layout.item_lesson_list_empty, parent, false)
                assertEquals(emptyXml.textSize(R.id.no_lessons) * 1.4f, empty.textSize(R.id.no_lessons), 1f)
            }
        }
    }

    @Test
    fun dayListCollectionItemsCarryIdentityIdsAndScaledTextForEveryListKind() {
        SettingsPreviewActivity.appearance = Appearances.light.toSettingsPreview()
        ActivityScenario.launch(SettingsPreviewActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val renderer = ScheduleListRowRenderer(activity)
                val parent = FrameLayout(activity)
                val math = lesson(ScheduleWidgetLessonState.COMPLETED)
                val physics = lesson(ScheduleWidgetLessonState.UPCOMING).copy(subject = "Физика", start = "15:20", end = "16:50")
                val day = listOf(
                    ScheduleListWidgetItem(ScheduleListWidgetItemKind.HEADER, dateIso = "2026-09-07"),
                    ScheduleListWidgetItem(ScheduleListWidgetItemKind.LESSON, math),
                    ScheduleListWidgetItem(ScheduleListWidgetItemKind.LESSON, physics),
                    // An official and a pending row with the same identity still get distinct ids.
                    ScheduleListWidgetItem(ScheduleListWidgetItemKind.LESSON, physics.copy(pendingStatus = ScheduleWidgetPendingStatus.WAITING)),
                    ScheduleListWidgetItem(ScheduleListWidgetItemKind.END)
                )
                for (style in LessonStyle.entries) {
                    val snapshot = ScheduleWidgetSnapshot(
                        SingleLessonWidgetContent(SingleLessonWidgetKind.LOADING), day, style, style,
                        fullTextSize = WidgetTextSize.EXTRA_LARGE
                    )
                    val items = renderer.collectionItems(snapshot)
                    assertEquals(day.size, items.itemCount)
                    assertTrue(items.hasStableIds())
                    assertEquals(9, items.viewTypeCount)
                    val ids = items.ids()
                    assertEquals(ids.size, ids.toSet().size)

                    // Ids follow the row, not its position or state: the launcher keeps scroll and recycling.
                    val later = renderer.collectionItems(snapshot.copy(lessonList = day.map { item ->
                        item.copy(lesson = item.lesson?.copy(state = ScheduleWidgetLessonState.COMPLETED))
                    }))
                    assertEquals(ids, later.ids())
                    val withoutFirst = renderer.collectionItems(snapshot.copy(lessonList = day - day[1]))
                    assertEquals(ids - ids[1], withoutFirst.ids())
                    val tomorrow = renderer.collectionItems(snapshot.copy(lessonList = day.map { item ->
                        if (item.kind == ScheduleListWidgetItemKind.HEADER) item.copy(dateIso = "2026-09-08") else item
                    }))
                    assertTrue(ids.intersect(tomorrow.ids().toSet()).isEmpty())

                    val lessonLayout = if (style == LessonStyle.DOT) R.layout.item_lesson_list_entry_dot else R.layout.item_lesson_list_entry_dash
                    val expected = listOf(
                        R.layout.item_lesson_list_day_title to R.id.day_title,
                        lessonLayout to R.id.title,
                        lessonLayout to R.id.title,
                        lessonLayout to R.id.title,
                        R.layout.item_lesson_list_end to R.id.end_marker
                    )
                    expected.forEachIndexed { index, (layout, textId) ->
                        val row = items.getItemView(index).apply(activity, parent)
                        val xml = activity.layoutInflater.inflate(layout, parent, false)
                        assertEquals("$style $index", xml.textSize(textId) * 1.4f, row.textSize(textId), 1f)
                    }
                }

                val messages = mapOf(
                    ScheduleListWidgetItemKind.EMPTY_TODAY to (R.layout.item_lesson_list_empty to R.id.no_lessons),
                    ScheduleListWidgetItemKind.EMPTY_TODAY_AND_TOMORROW to (R.layout.item_lesson_list_empty to R.id.no_lessons),
                    ScheduleListWidgetItemKind.NO_MORE_TODAY to (R.layout.item_lesson_list_no_more to R.id.no_more_lessons),
                    ScheduleListWidgetItemKind.ERROR to (R.layout.item_lesson_list_error to R.id.empty_view),
                    ScheduleListWidgetItemKind.SIGNED_OUT to (R.layout.item_lesson_list_error to R.id.empty_view),
                    ScheduleListWidgetItemKind.LOADING to (R.layout.item_lesson_list_updating to R.id.empty_view)
                )
                val messageIds = messages.map { (kind, view) ->
                    val (layout, textId) = view
                    val items = renderer.collectionItems(ScheduleWidgetSnapshot(
                        SingleLessonWidgetContent(SingleLessonWidgetKind.LOADING), listOf(ScheduleListWidgetItem(kind)),
                        LessonStyle.DOT, LessonStyle.DOT, fullTextSize = WidgetTextSize.EXTRA_LARGE
                    ))
                    assertEquals("$kind", 1, items.itemCount)
                    val row = items.getItemView(0).apply(activity, parent)
                    val xml = activity.layoutInflater.inflate(layout, parent, false)
                    assertEquals("$kind", xml.textSize(textId) * 1.4f, row.textSize(textId), 1f)
                    items.getItemId(0)
                }
                // A switch between two single-message states replaces the row instead of rebinding it.
                assertEquals(messageIds.size, messageIds.toSet().size)
            }
        }
    }

    private fun RemoteViewsCompat.RemoteCollectionItems.ids(): List<Long> = List(itemCount, ::getItemId)

    /**
     * API 31+ fades [faded] as a whole. Below it `View.setAlpha` is not a RemoteViews method, so every label keeps
     * its XML colour with the alpha folded in and the type indicator gets an image alpha.
     */
    private fun assertFade(faded: View, xml: View, textIds: List<Int>, alpha: Float) {
        val wholeView = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        assertEquals(if (wholeView) alpha else 1f, faded.alpha, 0.001f)
        assertEquals(
            if (wholeView) 255 else (alpha * 255).roundToInt(),
            faded.findViewById<ImageView>(R.id.type_indicator).imageAlpha
        )
        textIds.forEach { id ->
            val color = xml.findViewById<TextView>(id).currentTextColor
            val expected = if (wholeView) color else ColorUtils.setAlphaComponent(color, (Color.alpha(color) * alpha).roundToInt())
            assertEquals("$id", expected, faded.findViewById<TextView>(id).currentTextColor)
        }
    }

    // XML sizes land as whole pixels while the renderer applies exact floats, hence the 1 px tolerance.
    private fun View.textSize(id: Int): Float = findViewById<TextView>(id).textSize

    private fun View.layoutAt(width: Int) {
        measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        layout(0, 0, width, measuredHeight)
    }

    private val singleTextIds = listOf(
        R.id.type, R.id.time_start, R.id.time_separator, R.id.time_end, R.id.title, R.id.secondary_text, R.id.more_lessons_text
    )
    private val rowTextIds = listOf(R.id.time_start, R.id.time_end, R.id.title, R.id.type, R.id.secondary_text)

    private fun lesson(state: ScheduleWidgetLessonState) = ScheduleWidgetLesson(
        "Программирование", "13:30", "15:00", 2, "Иванов И. И.", "1506", "Кронверкский проспект, 49", state
    )
}
