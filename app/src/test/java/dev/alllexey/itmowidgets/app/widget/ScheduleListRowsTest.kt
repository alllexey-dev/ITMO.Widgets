package dev.alllexey.itmowidgets.app.widget

import android.content.Context
import android.util.SizeF
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.feature.debug.ui.PreviewHostApplication
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.DaySummary
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.DaySummaryKind
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.DayWidgetContent
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.NextLessonWidgetContent
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewMoment
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewScenario
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSelector
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.WidgetContentKind
import dev.alllexey.itmowidgets.feature.schedule.ui.widget.ScheduleListRow
import dev.alllexey.itmowidgets.feature.schedule.ui.widget.ScheduleListRows
import dev.alllexey.itmowidgets.feature.schedule.ui.widget.ScheduleSettingsPreview
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The classic list's rows from the day widget's content: the header, the lessons and the end, or one message. */
@RunWith(RobolectricTestRunner::class)
@Config(application = PreviewHostApplication::class)
class ScheduleListRowsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scenario = SchedulePreviewScenario(ScheduleWidgetSelector())

    @Test
    fun aDayIsItsHeaderItsLessonsAndTheEndWithIdsThatSurviveARedraw() {
        val snapshot = scenario.snapshot(ScheduleWidgetSettings(), SchedulePreviewMoment.LESSON, ScheduleSettingsPreview.labels(context))
        val rows = ScheduleListRows(context).rows(snapshot)

        assertEquals(snapshot.day.lessons.size + 2, rows.size)
        assertEquals("Сегодня · 7 сентября", text(rows.first(), R.id.day_title))
        assertEquals(snapshot.day.lessons.first().subject, text(rows[1], R.id.title))
        assertEquals(context.getString(R.string.schedule_widget_end_today), text(rows.last(), R.id.end_marker))
        assertEquals(rows.size, rows.map { it.id }.toSet().size)
        assertEquals(rows.map { it.id }, ScheduleListRows(context).rows(snapshot).map { it.id })
    }

    @Test
    fun theEndMarkerShowsOnlyWhereEveryRowFitsAboveIt() {
        val snapshot = scenario.snapshot(ScheduleWidgetSettings(), SchedulePreviewMoment.LESSON, ScheduleSettingsPreview.labels(context))
        val roomy = ScheduleListRows(context).rows(snapshot, SizeF(320f, 600f))
        val tight = ScheduleListRows(context).rows(snapshot, SizeF(320f, 250f))

        assertEquals(context.getString(R.string.schedule_widget_end_today), text(roomy.last(), R.id.end_marker))
        assertEquals(roomy.dropLast(1).map { it.id }, tight.map { it.id })
    }

    @Test
    fun anEmptyDaySaysWhyInOneRow() {
        assertEquals(context.getString(R.string.schedule_widget_empty_today), message(empty(tomorrow = false, DaySummaryKind.NONE_TODAY), R.id.no_lessons))
        assertEquals(context.getString(R.string.schedule_widget_no_more_today), message(empty(tomorrow = false, DaySummaryKind.OVER), R.id.no_more_lessons))
        assertEquals(
            context.getString(R.string.schedule_widget_empty_today_and_tomorrow),
            message(empty(tomorrow = true, DaySummaryKind.OVER), R.id.no_lessons)
        )
        assertEquals(context.getString(R.string.schedule_widget_signed_out), message(ScheduleWidgetSnapshot.signedOut(), R.id.empty_view))
    }

    private fun empty(tomorrow: Boolean, summary: DaySummaryKind) = ScheduleWidgetSnapshot(
        next = NextLessonWidgetContent(WidgetContentKind.EMPTY),
        day = DayWidgetContent(WidgetContentKind.EMPTY, date = "2026-09-07", tomorrow = tomorrow, summary = DaySummary(summary)),
    )

    private fun message(snapshot: ScheduleWidgetSnapshot, textId: Int): String =
        text(ScheduleListRows(context).rows(snapshot).single(), textId)

    private fun text(row: ScheduleListRow, id: Int): String =
        row.views.apply(context, FrameLayout(context)).findViewById<TextView>(id).text.toString()
}
