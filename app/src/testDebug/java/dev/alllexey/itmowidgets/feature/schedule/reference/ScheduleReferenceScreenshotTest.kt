package dev.alllexey.itmowidgets.feature.schedule.reference

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.FriendSelectionContract
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.designsystem.AppScreenshotRule
import dev.alllexey.itmowidgets.designsystem.XmlReferenceCapture
import dev.alllexey.itmowidgets.feature.schedule.reference.ScheduleReferenceFixtures.FRIEND_ISU
import dev.alllexey.itmowidgets.feature.schedule.reference.ScheduleReferenceFixtures.FRIEND_NAME
import dev.alllexey.itmowidgets.feature.schedule.ui.ScheduleFragment
import dev.alllexey.itmowidgets.feature.schedule.ui.ScheduleLifecycleTestActivity
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `ScheduleScreen` of LS-6a before its port: the real Fragment on its debug host, in every state of the list. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class ScheduleReferenceScreenshotTest {

    @get:Rule
    val shots = AppScreenshotRule(this)

    private val references = XmlReferenceCapture(shots, module = "feature-schedule")

    @Before
    fun hostDefaults() = ScheduleReferenceFixtures.resetScheduleHost()

    @After
    fun resetHost() = ScheduleReferenceFixtures.resetScheduleHost()

    @Test
    fun own() {
        ScheduleLifecycleTestActivity.days = MutableStateFlow(ScheduleReferenceFixtures.ownDays())
        ScheduleLifecycleTestActivity.showPendingSport = MutableStateFlow(true)
        ScheduleLifecycleTestActivity.pendingSport =
            MutableStateFlow(AppResult.Success(ScheduleReferenceFixtures.pendingSport()))
        ScheduleLifecycleTestActivity.changes.value = listOf(ScheduleReferenceFixtures.roomChange())
        capture("ScheduleScreen_own") { it.showsRows() }
    }

    @Test
    fun friend() {
        ScheduleLifecycleTestActivity.days = MutableStateFlow(ScheduleReferenceFixtures.ownDays())
        ScheduleLifecycleTestActivity.friendDays = MutableStateFlow(ScheduleReferenceFixtures.friendDays())
        // The friend selector's answer, as the real selector delivers it to the schedule.
        ScheduleReferenceFixtures.onEachLaunch(ScheduleLifecycleTestActivity::class.java, onCreated = { activity ->
            activity.supportFragmentManager.setFragmentResult(FriendSelectionContract.RESULT_KEY, Bundle().apply {
                putInt(FriendSelectionContract.RESULT_USER_ISU, FRIEND_ISU)
                putString(FriendSelectionContract.RESULT_USER_NAME, FRIEND_NAME)
            })
        }) {
            capture("ScheduleScreen_friend") { activity ->
                activity.showsRows() && activity.schedule().findViewById<View>(R.id.selected_user_card).isShown &&
                    !activity.schedule().findViewById<SwipeRefreshLayout>(R.id.swipe_refresh_layout).isRefreshing
            }
        }
    }

    @Test
    fun skeleton() {
        // No cache and a first load that never answers: the placeholder rows.
        ScheduleLifecycleTestActivity.refreshOutcome = { awaitCancellation() }
        capture("ScheduleScreen_skeleton") { it.schedule().findViewById<View>(R.id.schedule_skeleton).isShown }
    }

    @Test
    fun empty() = capture("ScheduleScreen_empty") { it.showsState() }

    @Test
    fun error() {
        ScheduleLifecycleTestActivity.refreshOutcome = { AppResult.Failure(AppError.Network) }
        capture("ScheduleScreen_error") { it.showsState() }
    }

    private fun capture(preview: String, ready: (ScheduleLifecycleTestActivity) -> Boolean) = references.host(
        preview,
        ScheduleLifecycleTestActivity::class.java,
        appearance = { ScheduleLifecycleTestActivity.appearance = it },
        ready = ready,
    )

    private fun ScheduleLifecycleTestActivity.schedule(): View =
        (supportFragmentManager.findFragmentByTag(ScheduleLifecycleTestActivity.SCHEDULE_TAG) as ScheduleFragment)
            .requireView()

    private fun ScheduleLifecycleTestActivity.showsRows(): Boolean {
        val recycler = schedule().findViewById<RecyclerView>(R.id.outer_recycler_view)
        return recycler.isShown && recycler.childCount > 0 && !recycler.hasPendingAdapterUpdates()
    }

    private fun ScheduleLifecycleTestActivity.showsState() =
        schedule().findViewById<View>(R.id.schedule_state_container).isShown
}
