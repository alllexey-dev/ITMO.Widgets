package dev.alllexey.itmowidgets.feature.schedule

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.settings.LessonStyle
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.*
import dev.alllexey.itmowidgets.feature.schedule.ui.widget.ScheduleWidgetRenderer
import dev.alllexey.itmowidgets.feature.schedule.ui.widget.ScheduleListRowRenderer
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsPreviewActivity
import dev.alllexey.itmowidgets.testing.Appearances
import dev.alllexey.itmowidgets.testing.Appearances.toSettingsPreview
import dev.alllexey.itmowidgets.testing.Screenshots
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

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
    fun completedRowsFadeTimeAndContentTogetherAndResetOnReuse() {
        ActivityScenario.launch(SettingsPreviewActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                for (style in LessonStyle.entries) {
                    val parent = FrameLayout(activity)
                    val remote = ScheduleWidgetRenderer.lessonListRow(activity, lesson(ScheduleWidgetLessonState.COMPLETED), style)
                    val row = remote.apply(activity, parent)
                    assertEquals(0.62f, row.alpha, 0.001f)
                    assertEquals(1f, row.findViewById<View>(R.id.lesson_content).alpha, 0f)
                    assertEquals(1f, row.findViewById<View>(R.id.time_column).alpha, 0f)
                    // A launcher may reuse a row rendered before whole-row fading was added.
                    row.findViewById<View>(R.id.lesson_content).alpha = 0.62f
                    ScheduleWidgetRenderer.lessonListRow(activity, lesson(ScheduleWidgetLessonState.CURRENT), style).reapply(activity, row)
                    assertEquals(1f, row.alpha, 0f)
                    assertEquals(1f, row.findViewById<View>(R.id.lesson_content).alpha, 0f)
                    ScheduleWidgetRenderer.lessonListRow(activity, lesson(ScheduleWidgetLessonState.COMPLETED), style).reapply(activity, row)
                    assertEquals(0.62f, row.alpha, 0.001f)
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
                            assertTrue(type.contentDescription.contains("не подтвержден"))
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

    private fun lesson(state: ScheduleWidgetLessonState) = ScheduleWidgetLesson(
        "Программирование", "13:30", "15:00", 2, "Иванов И. И.", "1506", "Кронверкский проспект, 49", state
    )
}
