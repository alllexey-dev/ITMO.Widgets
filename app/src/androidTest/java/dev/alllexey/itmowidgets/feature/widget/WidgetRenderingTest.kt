package dev.alllexey.itmowidgets.feature.widget

import android.content.Context
import android.os.Build
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetFormat
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewMoment
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewScenario
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSelector
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot
import dev.alllexey.itmowidgets.feature.schedule.ui.widget.NextLessonWidgetRenderer
import dev.alllexey.itmowidgets.feature.schedule.ui.widget.ScheduleListRows
import dev.alllexey.itmowidgets.feature.schedule.ui.widget.ScheduleSettingsPreview
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetSampleState
import dev.alllexey.itmowidgets.feature.sport.ui.widget.SportSettingsPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every widget layout inflates on the device's API level as the launcher inflates it (RemoteViews accept only their
 * own view classes and methods; the day widget places its rows only from API 31) and draws its key parts. The JVM
 * screenshots (`WidgetsScreenshotTest`) check how they look.
 */
@RunWith(AndroidJUnit4::class)
class WidgetRenderingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scenario = SchedulePreviewScenario(ScheduleWidgetSelector())

    @Test
    fun theNextLessonWidgetShowsTheLessonItsRoomAndItsCountdown() {
        val view = applied(schedule(ScheduleWidgetFormat.COMPACT, SchedulePreviewMoment.LESSON))

        assertTrue(text(view, R.id.next_headline).startsWith(context.getString(R.string.widget_next_now, "13:10").take(7)))
        assertTrue(text(view, R.id.next_place).startsWith("1506"))
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.next_progress).visibility)
        assertEquals(2, view.findViewById<android.view.ViewGroup>(R.id.next_rest_list).childCount)
    }

    @Test
    fun theSmallWidgetAndTheSignedOutMessageInflate() {
        val small = applied(
            NextLessonWidgetRenderer.layout(
                context, ScheduleWidgetSnapshot.signedOut(), null, scenario.instant(SchedulePreviewMoment.LESSON), null, medium = false
            )
        )
        assertEquals(View.VISIBLE, small.findViewById<View>(R.id.widget_message).visibility)
        assertEquals(context.getString(R.string.schedule_widget_signed_out), text(small, R.id.widget_message))
    }

    @Test
    fun theDayWidgetPlacesItsLessonsOnTheAxisFromApi31AndStacksThemBelow() {
        val view = applied(schedule(ScheduleWidgetFormat.FULL, SchedulePreviewMoment.LESSON))
        val canvas = view.findViewById<android.view.ViewGroup>(R.id.day_canvas)
        val list = view.findViewById<android.view.ViewGroup>(R.id.day_list)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            assertEquals(View.VISIBLE, canvas.visibility)
            assertTrue(canvas.childCount > 5)
        } else {
            assertEquals(View.VISIBLE, list.visibility)
            assertEquals(5, list.childCount)
        }
    }

    @Test
    fun theClassicListRowsInflateWithTheDayHeaderAndEveryLesson() {
        val snapshot = scenario.snapshot(ScheduleWidgetSettings(), SchedulePreviewMoment.LESSON, ScheduleSettingsPreview.labels(context))
        val rows = ScheduleListRows(context).rows(snapshot).map { applied(it.views) }

        assertEquals(snapshot.day.lessons.size + 2, rows.size)
        assertTrue(text(rows.first(), R.id.day_title).startsWith(context.getString(R.string.schedule_widget_today)))
        assertEquals(snapshot.day.lessons.first().subject, text(rows[1], R.id.title))
    }

    @Test
    fun theSportWidgetShowsTheRingAndTheBookings() {
        val medium = applied(SportSettingsPreview.views(context, WidgetPreviewSettings.Sport(), medium = true, SportWidgetSampleState.QUEUE))
        val small = applied(SportSettingsPreview.views(context, WidgetPreviewSettings.Sport(), medium = false, SportWidgetSampleState.DONE))

        assertEquals("30", text(medium, R.id.ring_total))
        assertEquals(2, medium.findViewById<android.view.ViewGroup>(R.id.sport_items).childCount)
        assertEquals("104", text(small, R.id.ring_total))
    }

    private fun schedule(format: ScheduleWidgetFormat, moment: SchedulePreviewMoment) =
        ScheduleSettingsPreview.views(context, scenario, WidgetPreviewSettings.Schedule(ScheduleWidgetSettings(), format), moment)

    private fun applied(remote: RemoteViews): View {
        var view: View? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view = remote.apply(context, FrameLayout(context)) }
        return checkNotNull(view)
    }

    private fun text(view: View, id: Int): String = view.findViewById<TextView>(id).text.toString()
}
