package dev.alllexey.itmowidgets.feature.schedule.reference

import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.designsystem.AppScreenshotRule
import dev.alllexey.itmowidgets.designsystem.XmlReferenceCapture
import dev.alllexey.itmowidgets.feature.schedule.reference.ScheduleReferenceFixtures.FixedTime
import dev.alllexey.itmowidgets.feature.schedule.ui.ScheduleLifecycleTestActivity
import dev.alllexey.itmowidgets.feature.schedule.ui.details.PendingSportDetailsBottomSheet
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `PendingSportDetailsContent` of LS-5b before its port: the sheet of an auto-sign row over the schedule host. The
 * sheet is a dialog window, so the capture is the sheet itself, not the activity behind it.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class PendingSportDetailsReferenceScreenshotTest {

    @get:Rule
    val shots = AppScreenshotRule(this)

    private val references = XmlReferenceCapture(shots, module = "feature-schedule")

    @Before
    fun hostDefaults() = ScheduleReferenceFixtures.resetScheduleHost()

    @After
    fun resetHost() = ScheduleReferenceFixtures.resetScheduleHost()

    /** A free queue the student waits in. */
    @Test
    fun waiting() = capture("PendingSportDetailsContent_waiting", ScheduleReferenceFixtures.waitingBooking())

    /** The auto-sign prediction. */
    @Test
    fun predicted() = capture("PendingSportDetailsContent_predicted", ScheduleReferenceFixtures.predictedBooking())

    private fun capture(preview: String, booking: PendingSportBooking) =
        ScheduleReferenceFixtures.onEachLaunch(ScheduleLifecycleTestActivity::class.java, onCreated = { activity ->
            PendingSportDetailsBottomSheet.newInstance(booking, FixedTime.timeZone)
                .show(activity.supportFragmentManager, PendingSportDetailsBottomSheet.TAG)
        }) {
            references.host(
                preview,
                ScheduleLifecycleTestActivity::class.java,
                appearance = { ScheduleLifecycleTestActivity.appearance = it },
                ready = { activity -> activity.sheet()?.let { it.isResumed && it.requireView().height > 0 } == true },
                view = { activity -> ScheduleReferenceFixtures.sheetSurface(activity, checkNotNull(activity.sheet())) },
            )
        }

    private fun ScheduleLifecycleTestActivity.sheet() =
        supportFragmentManager.findFragmentByTag(PendingSportDetailsBottomSheet.TAG) as PendingSportDetailsBottomSheet?
}
