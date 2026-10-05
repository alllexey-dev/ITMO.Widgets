package dev.alllexey.itmowidgets.feature.schedule.reference

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.designsystem.AppScreenshotRule
import dev.alllexey.itmowidgets.designsystem.XmlReferenceCapture
import dev.alllexey.itmowidgets.feature.schedule.ui.changes.ScheduleChangesPreviewActivity
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `ScheduleChangesScreen` of LS-4 before its port: the history on its debug host, with changes and without. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class ScheduleChangesReferenceScreenshotTest {

    @get:Rule
    val shots = AppScreenshotRule(this)

    private val references = XmlReferenceCapture(shots, module = "feature-schedule")

    @Before
    fun hostDefaults() = ScheduleReferenceFixtures.resetChangesHost()

    @After
    fun resetHost() = ScheduleReferenceFixtures.resetChangesHost()

    @Test
    fun list() {
        // Opening the screen marks everything read; each launch starts from the unread history again.
        ScheduleReferenceFixtures.onEachLaunch(ScheduleChangesPreviewActivity::class.java, beforeCreate = {
            ScheduleChangesPreviewActivity.changes.value = ScheduleReferenceFixtures.history()
        }) {
            capture("ScheduleChangesScreen_list") { activity ->
                val list = activity.fragment.requireView().findViewById<RecyclerView>(R.id.recycler_view)
                list.isShown && list.childCount > 0 && !list.hasPendingAdapterUpdates()
            }
        }
    }

    @Test
    fun empty() = capture("ScheduleChangesScreen_empty") { activity ->
        activity.fragment.requireView().findViewById<View>(R.id.state_container).isShown
    }

    private fun capture(preview: String, ready: (ScheduleChangesPreviewActivity) -> Boolean) = references.host(
        preview,
        ScheduleChangesPreviewActivity::class.java,
        appearance = { ScheduleChangesPreviewActivity.appearance = it },
        ready = ready,
    )
}
