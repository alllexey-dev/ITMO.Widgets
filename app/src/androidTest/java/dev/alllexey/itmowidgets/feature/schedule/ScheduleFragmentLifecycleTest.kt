package dev.alllexey.itmowidgets.feature.schedule

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleUiState
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleViewModel
import dev.alllexey.itmowidgets.feature.schedule.ui.ScheduleFragment
import dev.alllexey.itmowidgets.feature.schedule.ui.ScheduleLifecycleTestActivity
import java.io.File
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleFragmentLifecycleTest {
    @Test
    fun savingBeforeAnyViewExistsDoesNotRequireBinding() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val fragment = ScheduleFragment()
            fragment.onSaveInstanceState(Bundle())
            assertNull(fragment.view)
        }
    }

    @Test
    fun backStackSaveStopAndActivityRecreationKeepScrollWithoutAView() = withSchedule { scenario ->
        val anchor = scrollToMiddle(scenario)
        scenario.onActivity { activity ->
            val schedule = activity.schedule()
            activity.showAnotherScreen()
            assertNull(schedule.view)
            // This invokes the exact FragmentStateManager save path from the crash.
            assertNotNull(activity.supportFragmentManager.saveFragmentInstanceState(schedule))
        }
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        scenario.recreate()
        scenario.onActivity { activity ->
            assertNull(activity.schedule().view)
            assertTrue(activity.supportFragmentManager.popBackStackImmediate())
        }
        awaitAnchor(scenario, anchor)
    }

    @Test
    fun savedFragmentRestorationKeepsScrollWhenScheduleArrivesLater() = withSchedule { scenario ->
        val anchor = scrollToMiddle(scenario)
        val days = ScheduleLifecycleTestActivity.days.value
        // A fresh Fragment/ViewModel restores saved state before its data arrives,
        // as after process death (configuration recreation would retain the ViewModel).
        ScheduleLifecycleTestActivity.days = MutableStateFlow(emptyList())
        scenario.onActivity { activity ->
            val manager = activity.supportFragmentManager
            val saved = manager.saveFragmentInstanceState(activity.schedule())
            val replacement = ScheduleFragment().apply { setInitialSavedState(saved) }
            manager.beginTransaction()
                .replace(R.id.schedule_test_container, replacement, ScheduleLifecycleTestActivity.SCHEDULE_TAG)
                .commitNow()
        }
        eventually(scenario) { assertEquals(0, it.recycler().adapter!!.itemCount) }
        scenario.onActivity { activity ->
            activity.showAnotherScreen()
            assertNull(activity.schedule().view)
            assertNotNull(activity.supportFragmentManager.saveFragmentInstanceState(activity.schedule()))
            assertTrue(activity.supportFragmentManager.popBackStackImmediate())
        }
        ScheduleLifecycleTestActivity.days.value = days
        awaitAnchor(scenario, anchor)
    }

    @Test
    fun savingALiveViewDoesNotRewindLaterScrollingOnDataUpdate() = withSchedule { scenario ->
        scrollToMiddle(scenario)
        scenario.onActivity { activity ->
            assertNotNull(activity.supportFragmentManager.saveFragmentInstanceState(activity.schedule()))
            (activity.recycler().layoutManager as LinearLayoutManager).scrollToPositionWithOffset(12, -9)
        }
        eventually(scenario) { assertEquals(12, (it.recycler().layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()) }
        var anchor = 0 to 0
        scenario.onActivity { anchor = it.anchor() }
        ScheduleLifecycleTestActivity.days.value = sampleDays(31)
        eventually(scenario) { assertEquals(31, it.recycler().adapter!!.itemCount) }
        awaitAnchor(scenario, anchor)
    }

    @Test
    fun refreshingPaginatedScheduleKeepsTheVisibleDayAndOffsetThroughEveryFrame() =
        withSchedule(sampleDays(60), restrictToRequestedRange = true, initialItemCount = 16) { scenario ->
            val anchor = scrollPastInitialPage(scenario)
            screenshot("before-refresh")
            val refresh = CompletableDeferred<Unit>()
            val cachesCleared = AtomicBoolean(false)
            val anchorMoved = AtomicBoolean(false)
            val updatedDays = sampleDays(60).map { it.copy(note = "Обновлено") }
            ScheduleLifecycleTestActivity.clearOutcome = {
                cachesCleared.set(true)
                ScheduleLifecycleTestActivity.days.value = emptyList()
            }
            ScheduleLifecycleTestActivity.refreshOutcome = {
                refresh.await()
                ScheduleLifecycleTestActivity.days.value = updatedDays
                AppResult.Success(Unit)
            }
            lateinit var root: View
            val observer = ViewTreeObserver.OnPreDrawListener {
                val list = root.findViewById<RecyclerView>(R.id.outerRecyclerView)
                val layout = list.layoutManager as LinearLayoutManager
                val position = layout.findFirstVisibleItemPosition()
                val offset = layout.findViewByPosition(position)?.top?.minus(list.paddingTop)
                if (position != anchor.first || offset != anchor.second) anchorMoved.set(true)
                true
            }
            scenario.onActivity { activity ->
                root = activity.schedule().requireView()
                root.viewTreeObserver.addOnPreDrawListener(observer)
                activity.viewModel().loadInitialSchedule(forceRefresh = true)
            }
            try {
                eventually(scenario) { activity ->
                    assertTrue(root.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh_layout).isRefreshing)
                    assertEquals(30, activity.recycler().adapter!!.itemCount)
                    assertEquals(anchor, activity.anchor())
                }
                screenshot("during-refresh")
                refresh.complete(Unit)
                eventually(scenario) { activity ->
                    val state = activity.viewModel().uiState.value as ScheduleUiState.Content
                    assertFalse(state.loadingMore)
                    assertTrue(state.schedule.all { it.note == "Обновлено" })
                    assertEquals(30, activity.recycler().adapter!!.itemCount)
                    assertFalse(root.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh_layout).isRefreshing)
                }
                awaitAnchor(scenario, anchor)
                screenshot("after-refresh")
                assertFalse("Refresh must not clear the schedule cache", cachesCleared.get())
                assertFalse("The visible anchor moved in a drawn refresh frame", anchorMoved.get())
            } finally {
                refresh.complete(Unit)
                scenario.onActivity { root.viewTreeObserver.removeOnPreDrawListener(observer) }
            }
        }

    @Test
    fun refreshFailureAndSnackbarRetryKeepThePaginatedViewport() =
        withSchedule(sampleDays(60), restrictToRequestedRange = true, initialItemCount = 16) { scenario ->
            val anchor = scrollPastInitialPage(scenario)
            ScheduleLifecycleTestActivity.clearOutcome = {
                ScheduleLifecycleTestActivity.days.value = emptyList()
            }
            ScheduleLifecycleTestActivity.refreshOutcome = { AppResult.Failure(AppError.Network) }
            scenario.onActivity { it.viewModel().loadInitialSchedule(forceRefresh = true) }
            eventually(scenario) { activity ->
                val state = activity.viewModel().uiState.value as ScheduleUiState.Content
                assertFalse(state.loadingMore)
                assertEquals(30, activity.recycler().adapter!!.itemCount)
                assertEquals(anchor, activity.anchor())
                assertEquals(View.GONE, activity.schedule().requireView()
                    .findViewById<View>(R.id.schedule_state_container).visibility)
                assertNotNull(activity.findViewById<View>(com.google.android.material.R.id.snackbar_action))
            }
            val retry = CompletableDeferred<Unit>()
            val retryStarted = AtomicBoolean(false)
            ScheduleLifecycleTestActivity.refreshOutcome = {
                retryStarted.set(true)
                retry.await()
                AppResult.Success(Unit)
            }
            try {
                scenario.onActivity { activity ->
                    activity.findViewById<View>(com.google.android.material.R.id.snackbar_action).performClick()
                }
                eventually(scenario) { activity ->
                    assertTrue("The Snackbar action must start another request", retryStarted.get())
                    assertTrue((activity.viewModel().uiState.value as ScheduleUiState.Content).loadingMore)
                    assertTrue(activity.schedule().requireView()
                        .findViewById<SwipeRefreshLayout>(R.id.swipe_refresh_layout).isRefreshing)
                    assertEquals(anchor, activity.anchor())
                }
                retry.complete(Unit)
                eventually(scenario) { activity ->
                    val state = activity.viewModel().uiState.value as ScheduleUiState.Content
                    assertFalse(state.loadingMore)
                    assertEquals(30, activity.recycler().adapter!!.itemCount)
                    assertEquals(anchor, activity.anchor())
                }
            } finally {
                retry.complete(Unit)
            }
        }

    @Test
    fun pendingListUpdatesCannotTouchDestroyedOrReplacementViews() = withSchedule { scenario ->
        repeat(5) { index ->
            scenario.onActivity { activity ->
                ScheduleLifecycleTestActivity.days.value = sampleDays(40 + index)
                activity.showAnotherScreen()
                assertNull(activity.schedule().view)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity -> assertTrue(activity.supportFragmentManager.popBackStackImmediate()) }
        }
        eventually(scenario) { assertEquals(44, it.recycler().adapter!!.itemCount) }
    }

    @Test
    fun contentEmptyContentTransitionsDoNotDrawAPlaceholderOverRows() = withSchedule { scenario ->
        val overlapDrawn = AtomicBoolean(false)
        val placeholderDrawn = AtomicBoolean(false)
        lateinit var root: View
        val observer = ViewTreeObserver.OnPreDrawListener {
            if (root.findViewById<View>(R.id.schedule_state_container).visibility == View.VISIBLE) {
                placeholderDrawn.set(true)
                val recycler = root.findViewById<RecyclerView>(R.id.outerRecyclerView)
                // Removed children can still be drawn by an ItemAnimator after adapter count reaches zero.
                if (recycler.adapter!!.itemCount > 0 || recycler.childCount > 0) overlapDrawn.set(true)
            }
            true
        }
        scenario.onActivity { activity ->
            root = activity.schedule().requireView()
            root.viewTreeObserver.addOnPreDrawListener(observer)
        }
        try {
            ScheduleLifecycleTestActivity.days.value = emptyList()
            eventually(scenario) { activity ->
                assertEquals(0, activity.recycler().adapter!!.itemCount)
                assertState(activity, R.string.schedule_empty_title, retryVisible = false)
                assertTrue("Empty state has not reached a drawn frame", placeholderDrawn.get())
            }
            ScheduleLifecycleTestActivity.days.value = sampleDays(31)
            eventually(scenario) { activity ->
                assertEquals(31, activity.recycler().adapter!!.itemCount)
                assertEquals(View.GONE, root.findViewById<View>(R.id.schedule_state_container).visibility)
            }
            assertFalse("Placeholder and schedule rows must not share a drawn frame", overlapDrawn.get())
        } finally {
            scenario.onActivity { root.viewTreeObserver.removeOnPreDrawListener(observer) }
        }
    }

    @Test
    fun refreshErrorArrivingBeforeEmptyDiffCommitRemainsVisibleAfterCommit() {
        val days = GatedDays(sampleDays())
        withSchedule(days) { scenario ->
            try {
                days.arm()
                scenario.onActivity { ScheduleLifecycleTestActivity.days.value = emptyList() }
                assertTrue("Empty diff did not reach its background executor", days.awaitDiff())
                ScheduleLifecycleTestActivity.refreshOutcome = { AppResult.Failure(AppError.Network) }
                scenario.onActivity { activity -> activity.viewModel().loadInitialSchedule() }
                eventually(scenario) { activity ->
                    assertEquals(ScheduleUiState.Error(AppError.Network, null), activity.viewModel().uiState.value)
                    assertEquals(30, activity.recycler().adapter!!.itemCount)
                }
                days.release()
                eventually(scenario) { activity ->
                    assertEquals(0, activity.recycler().adapter!!.itemCount)
                    assertState(activity, R.string.common_load_error_title, retryVisible = true)
                }
                assertFalse("Diff gate timed out before the error was rendered", days.timedOut.get())
            } finally {
                days.release()
            }
        }
    }

    @Test
    fun refreshStartingBeforeEmptyDiffCommitKeepsLoadingUntilItCompletes() {
        val days = GatedDays(sampleDays())
        val refresh = CompletableDeferred<Unit>()
        withSchedule(days) { scenario ->
            try {
                days.arm()
                scenario.onActivity { ScheduleLifecycleTestActivity.days.value = emptyList() }
                assertTrue("Empty diff did not reach its background executor", days.awaitDiff())
                ScheduleLifecycleTestActivity.refreshOutcome = {
                    refresh.await()
                    AppResult.Success(Unit)
                }
                scenario.onActivity { activity -> activity.viewModel().loadInitialSchedule() }
                eventually(scenario) { activity ->
                    assertEquals(ScheduleUiState.Loading(null), activity.viewModel().uiState.value)
                }
                days.release()
                eventually(scenario) { activity ->
                    val root = activity.schedule().requireView()
                    assertEquals(0, activity.recycler().adapter!!.itemCount)
                    assertEquals(View.GONE, root.findViewById<View>(R.id.schedule_state_container).visibility)
                    assertTrue(root.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh_layout).isRefreshing)
                }
                refresh.complete(Unit)
                eventually(scenario) { activity ->
                    assertState(activity, R.string.schedule_empty_title, retryVisible = false)
                    assertFalse(activity.schedule().requireView()
                        .findViewById<SwipeRefreshLayout>(R.id.swipe_refresh_layout).isRefreshing)
                }
                assertFalse("Diff gate timed out before loading was rendered", days.timedOut.get())
            } finally {
                days.release()
                refresh.complete(Unit)
            }
        }
    }

    private fun withSchedule(
        initialDays: List<DaySchedule> = sampleDays(),
        restrictToRequestedRange: Boolean = false,
        initialItemCount: Int = initialDays.size,
        block: (ActivityScenario<ScheduleLifecycleTestActivity>) -> Unit
    ) {
        ScheduleLifecycleTestActivity.days = MutableStateFlow(initialDays)
        ScheduleLifecycleTestActivity.refreshOutcome = { AppResult.Success(Unit) }
        ScheduleLifecycleTestActivity.clearOutcome = {}
        ScheduleLifecycleTestActivity.restrictToRequestedRange = restrictToRequestedRange
        try {
            ActivityScenario.launch(ScheduleLifecycleTestActivity::class.java).use { scenario ->
                eventually(scenario) { assertEquals(initialItemCount, it.recycler().adapter!!.itemCount) }
                block(scenario)
            }
        } finally {
            ScheduleLifecycleTestActivity.days = MutableStateFlow(emptyList())
            ScheduleLifecycleTestActivity.refreshOutcome = { AppResult.Success(Unit) }
            ScheduleLifecycleTestActivity.clearOutcome = {}
            ScheduleLifecycleTestActivity.restrictToRequestedRange = false
        }
    }

    private fun assertState(activity: ScheduleLifecycleTestActivity, titleRes: Int, retryVisible: Boolean) {
        val root = activity.schedule().requireView()
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.schedule_state_container).visibility)
        assertEquals(activity.getString(titleRes), root.findViewById<TextView>(R.id.schedule_state_title).text.toString())
        assertEquals(if (retryVisible) View.VISIBLE else View.GONE, root.findViewById<View>(R.id.schedule_state_action).visibility)
    }

    private fun scrollToMiddle(scenario: ActivityScenario<ScheduleLifecycleTestActivity>): Pair<Int, Int> {
        scenario.onActivity { (it.recycler().layoutManager as LinearLayoutManager).scrollToPositionWithOffset(8, -17) }
        eventually(scenario) { assertEquals(8, (it.recycler().layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()) }
        var anchor = 0 to 0
        scenario.onActivity { anchor = it.anchor() }
        return anchor
    }

    private fun scrollPastInitialPage(scenario: ActivityScenario<ScheduleLifecycleTestActivity>): Pair<Int, Int> {
        scenario.onActivity { it.viewModel().fetchNextDays() }
        eventually(scenario) { assertEquals(30, it.recycler().adapter!!.itemCount) }
        scenario.onActivity {
            (it.recycler().layoutManager as LinearLayoutManager).scrollToPositionWithOffset(17, -19)
        }
        eventually(scenario) {
            assertEquals(17, (it.recycler().layoutManager as LinearLayoutManager).findFirstVisibleItemPosition())
        }
        var anchor = 0 to 0
        scenario.onActivity { anchor = it.anchor() }
        return anchor
    }

    private fun awaitAnchor(scenario: ActivityScenario<ScheduleLifecycleTestActivity>, anchor: Pair<Int, Int>) {
        eventually(scenario) { assertEquals(anchor, it.anchor()) }
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        // Layout can be committed before the device compositor presents its new buffer.
        SystemClock.sleep(300)
        instrumentation.waitForIdleSync()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.externalCacheDir, "schedule-refresh-screenshots")
            .apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun ScheduleLifecycleTestActivity.anchor(): Pair<Int, Int> {
        val recycler = recycler()
        val layout = recycler.layoutManager as LinearLayoutManager
        val position = layout.findFirstVisibleItemPosition()
        return position to (layout.findViewByPosition(position)!!.top - recycler.paddingTop)
    }

    private fun ScheduleLifecycleTestActivity.schedule() =
        supportFragmentManager.findFragmentByTag(ScheduleLifecycleTestActivity.SCHEDULE_TAG) as ScheduleFragment

    private fun ScheduleLifecycleTestActivity.recycler() = schedule().requireView().findViewById<RecyclerView>(R.id.outerRecyclerView)

    private fun ScheduleLifecycleTestActivity.viewModel() = ViewModelProvider(schedule())[ScheduleViewModel::class.java]

    private fun ScheduleLifecycleTestActivity.showAnotherScreen() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.schedule_test_container, Fragment())
            .addToBackStack("details")
            .commit()
        supportFragmentManager.executePendingTransactions()
    }

    private fun eventually(scenario: ActivityScenario<ScheduleLifecycleTestActivity>, assertion: (ScheduleLifecycleTestActivity) -> Unit) {
        var failure: AssertionError? = null
        repeat(40) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            try {
                scenario.onActivity(assertion)
                return
            } catch (error: AssertionError) {
                failure = error
            }
            Thread.sleep(50)
        }
        throw checkNotNull(failure)
    }

    private fun sampleDays(count: Int = 30) = (0 until count).map { offset ->
        val date = LocalDate.of(2026, 9, 6).plusDays(offset.toLong())
        DaySchedule(date.dayOfWeek.value, 1, date, null, emptyList())
    }

    /** Holds DiffUtil's background size read; UI-thread reads never wait. */
    private class GatedDays(private val values: List<DaySchedule>) : AbstractList<DaySchedule>() {
        private val armed = AtomicBoolean(false)
        private val reached = CountDownLatch(1)
        private val released = CountDownLatch(1)
        val timedOut = AtomicBoolean(false)

        override val size: Int
            get() {
                if (armed.get() && Looper.myLooper() != Looper.getMainLooper()) {
                    reached.countDown()
                    if (!released.await(5, TimeUnit.SECONDS)) timedOut.set(true)
                }
                return values.size
            }

        override fun get(index: Int): DaySchedule = values[index]

        fun arm() = armed.set(true)

        fun awaitDiff(): Boolean = reached.await(2, TimeUnit.SECONDS)

        fun release() {
            armed.set(false)
            released.countDown()
        }
    }
}
