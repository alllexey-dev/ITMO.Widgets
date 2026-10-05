package dev.alllexey.itmowidgets.feature.schedule.reference

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.debug.MemoryCalendarSync
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.SchedulePreferencesRepository
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.designsystem.AppScreenshotRule
import dev.alllexey.itmowidgets.designsystem.XmlReferenceCapture
import dev.alllexey.itmowidgets.feature.schedule.FakeScheduleChangesRepository
import dev.alllexey.itmowidgets.feature.schedule.FakeScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleViewModel
import dev.alllexey.itmowidgets.feature.schedule.reference.ScheduleReferenceFixtures.FRIEND_ISU
import dev.alllexey.itmowidgets.feature.schedule.reference.ScheduleReferenceFixtures.FRIEND_NAME
import dev.alllexey.itmowidgets.feature.schedule.reference.ScheduleReferenceFixtures.FixedTime
import dev.alllexey.itmowidgets.feature.schedule.ui.ScheduleFragment
import dev.alllexey.itmowidgets.feature.schedule.ui.ScheduleLifecycleTestActivity
import dev.alllexey.itmowidgets.feature.schedule.ui.UserScheduleFragment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `UserScheduleScreen` of LS-6b before its port: the titled shell around a friend's schedule, on the schedule host.
 * The host fakes only its own top-level schedule, so the nested one gets the same fakes here.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class UserScheduleReferenceScreenshotTest {

    @get:Rule
    val shots = AppScreenshotRule(this)

    private val references = XmlReferenceCapture(shots, module = "feature-schedule")

    @Before
    fun hostDefaults() = ScheduleReferenceFixtures.resetScheduleHost()

    @After
    fun resetHost() = ScheduleReferenceFixtures.resetScheduleHost()

    @Test
    fun content() = ScheduleReferenceFixtures.onEachLaunch(
        ScheduleLifecycleTestActivity::class.java,
        onCreated = ::showUser,
    ) {
        references.host(
            "UserScheduleScreen_content",
            ScheduleLifecycleTestActivity::class.java,
            appearance = { ScheduleLifecycleTestActivity.appearance = it },
            ready = { activity ->
                val schedule = activity.userSchedule()?.requireView()
                val recycler = schedule?.findViewById<RecyclerView>(R.id.outer_recycler_view)
                recycler != null && recycler.isShown && recycler.childCount > 0 &&
                    !recycler.hasPendingAdapterUpdates() &&
                    !schedule.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh_layout).isRefreshing
            },
        )
    }

    private fun showUser(activity: ScheduleLifecycleTestActivity) {
        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(FriendScheduleFakes(), true)
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.schedule_test_container, UserScheduleFragment().apply {
                arguments = Bundle().apply {
                    putInt(UserScreenArgs.ISU, FRIEND_ISU)
                    putString(UserScreenArgs.NAME, FRIEND_NAME)
                }
            })
            .commitNow()
    }

    private fun ScheduleLifecycleTestActivity.userSchedule(): ScheduleFragment? =
        supportFragmentManager.fragments.filterIsInstance<UserScheduleFragment>().firstOrNull()
            ?.childFragmentManager?.fragments?.filterIsInstance<ScheduleFragment>()?.firstOrNull()
            ?.takeIf { it.view != null }

    /** What the host does for its own schedule, for the one inside [UserScheduleFragment]. */
    private class FriendScheduleFakes : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentPreCreated(fm: FragmentManager, fragment: Fragment, savedInstanceState: Bundle?) {
            if (fragment !is ScheduleFragment || fragment.parentFragment !is UserScheduleFragment) return
            fragment.timeProvider = FixedTime
            ViewModelProvider(fragment, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = ScheduleViewModel(
                    FakeScheduleRepository().apply {
                        schedulesFor(FRIEND_ISU).value = ScheduleReferenceFixtures.friendDays()
                    },
                    FixedTime,
                    SavedStateHandle(mapOf(ScheduleViewModel.ARG_USER_ISU to FRIEND_ISU)),
                    object : SchedulePreferencesRepository {
                        override fun observeSportAutoSignEnabled() = flowOf(false)
                    },
                    object : PendingSportBookingsRepository {
                        override fun observePendingBookings() =
                            MutableStateFlow<AppResult<List<PendingSportBooking>>>(AppResult.Success(emptyList()))
                        override suspend fun refresh() = Unit
                    },
                    FakeScheduleChangesRepository(),
                    MemoryCalendarSync(),
                ) as T
            })[ScheduleViewModel::class.java]
        }
    }
}
