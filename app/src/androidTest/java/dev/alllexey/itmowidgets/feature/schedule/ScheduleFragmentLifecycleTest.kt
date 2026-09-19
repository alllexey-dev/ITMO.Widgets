package dev.alllexey.itmowidgets.feature.schedule

import android.os.Bundle
import android.os.Looper
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
import dev.alllexey.itmowidgets.core.navigation.FriendSelectionContract
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.util.DataState
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleUiState
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleDisplayDay
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleViewModel
import dev.alllexey.itmowidgets.feature.schedule.ui.ScheduleFragment
import dev.alllexey.itmowidgets.feature.schedule.ui.ScheduleLifecycleTestActivity
import dev.alllexey.itmowidgets.feature.schedule.ui.DayScheduleAdapter
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestUi
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
                val list = root.findViewById<RecyclerView>(R.id.outer_recycler_view)
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
    fun liveAutoSignPreferenceUpdatesTheVisibleDayWithoutResettingItsAnchor() = withSchedule { scenario ->
        val anchor = scrollToMiddle(scenario)
        val date = sampleDays()[anchor.first].date
        val start = date.atTime(16, 0).atZone(java.time.ZoneId.of("Europe/Moscow")).toOffsetDateTime()
        ScheduleLifecycleTestActivity.pendingSport.value = DataState.Success(listOf(PendingSportBooking(
            queueId = 77, queueKind = PendingSportBooking.QueueKind.AUTO, lessonId = 777,
            sectionName = "Тестовая секция плавания", start = start, end = start.plusMinutes(90),
            teacherFio = "Тестовый преподаватель", roomName = "Тестовый корпус", isPrediction = true
        )))
        ScheduleLifecycleTestActivity.showPendingSport.value = true
        eventually(scenario) { activity ->
            val state = activity.viewModel().uiState.value as ScheduleUiState.Content
            assertEquals(1, state.displayDays.single { it.date == date }.pendingSport.size)
            assertTrue(state.schedule.single { it.date == date }.lessons.isEmpty())
            assertNotNull(activity.recycler().findViewById<View>(R.id.pending_sport_root))
            assertEquals(anchor, activity.anchor())
            assertEquals(30, activity.recycler().adapter!!.itemCount)
        }
        screenshot("pending-visible")
        ScheduleLifecycleTestActivity.showPendingSport.value = false
        eventually(scenario) { activity ->
            assertNull(activity.recycler().findViewById<View>(R.id.pending_sport_root))
            assertEquals(anchor, activity.anchor())
            assertEquals(30, activity.recycler().adapter!!.itemCount)
        }
    }

    @Test
    fun pendingOnlyRefreshAndScrollPaginationKeepTheVisibleDayThroughEveryFrame() =
        withSchedule(initialDays = emptyList(), restrictToRequestedRange = true) { scenario ->
            val today = LocalDate.of(2026, 9, 7)
            ScheduleLifecycleTestActivity.pendingSport.value = DataState.Success((1..42).flatMap { day ->
                (0..2).map { index ->
                    val id = (day * 10 + index).toLong()
                    val start = today.plusDays(day.toLong()).atTime(14 + index * 2, 0)
                        .atZone(java.time.ZoneId.of("Europe/Moscow")).toOffsetDateTime()
                    PendingSportBooking(
                        queueId = id, queueKind = PendingSportBooking.QueueKind.AUTO, lessonId = 1000 + id,
                        sectionName = "Тестовая секция плавания с ожиданием свободного места",
                        start = start, end = start.plusMinutes(90), teacherFio = "Тестовый преподаватель",
                        roomName = "Тестовый спортивный корпус", isPrediction = true
                    )
                }
            })
            ScheduleLifecycleTestActivity.showPendingSport.value = true
            eventually(scenario) { activity ->
                assertEquals(14, activity.recycler().adapter!!.itemCount)
                val state = activity.viewModel().uiState.value
                assertTrue(state is ScheduleUiState.Content)
                assertFalse((state as ScheduleUiState.Content).loadingMore)
                assertTrue(state.schedule.isEmpty())
            }
            val anchor = scrollToMiddle(scenario)
            var expectedAnchor: Pair<Int, Int>? = anchor
            val invalidFrame = AtomicBoolean(false)
            val request = CompletableDeferred<AppResult<Unit>>()
            val page = CompletableDeferred<AppResult<Unit>>()
            val pageStarted = AtomicBoolean(false)
            lateinit var root: View
            val observer = ViewTreeObserver.OnPreDrawListener {
                val list = root.findViewById<RecyclerView>(R.id.outer_recycler_view)
                val layout = list.layoutManager as LinearLayoutManager
                val first = layout.findFirstVisibleItemPosition()
                val offset = layout.findViewByPosition(first)?.top?.minus(list.paddingTop)
                if (!list.isShown || list.adapter!!.itemCount == 0 ||
                    (expectedAnchor != null && expectedAnchor != (first to offset))) {
                    invalidFrame.set(true)
                }
                true
            }
            ScheduleLifecycleTestActivity.refreshOutcome = { request.await() }
            scenario.onActivity { activity ->
                root = activity.schedule().requireView()
                root.viewTreeObserver.addOnPreDrawListener(observer)
                activity.viewModel().loadInitialSchedule(forceRefresh = true)
            }
            try {
                eventually(scenario) { activity ->
                    val current = activity.viewModel().uiState.value
                    assertTrue(current is ScheduleUiState.Content)
                    assertTrue((current as ScheduleUiState.Content).loadingMore)
                    assertEquals(14, activity.recycler().adapter!!.itemCount)
                    assertEquals(anchor, activity.anchor())
                    assertTrue(activity.recycler().isShown)
                }
                screenshot("pending-only-refresh")
                request.complete(AppResult.Success(Unit))
                eventually(scenario) { activity ->
                    assertFalse((activity.viewModel().uiState.value as ScheduleUiState.Content).loadingMore)
                    assertEquals(anchor, activity.anchor())
                }

                ScheduleLifecycleTestActivity.refreshOutcome = {
                    pageStarted.set(true)
                    page.await()
                }
                scenario.onActivity { activity ->
                    expectedAnchor = null // The next movement is the user's deliberate scroll.
                    (activity.recycler().layoutManager as LinearLayoutManager)
                        .scrollToPositionWithOffset(12, -19)
                }
                val pageAnchor = 12 to -19
                eventually(scenario) { activity ->
                    assertTrue("Scrolling near the end must request the next range", pageStarted.get())
                    val current = activity.viewModel().uiState.value
                    assertTrue(current is ScheduleUiState.Content)
                    assertTrue((current as ScheduleUiState.Content).loadingMore)
                    assertTrue(current.schedule.isEmpty())
                    assertEquals(28, activity.recycler().adapter!!.itemCount)
                    assertEquals(pageAnchor, activity.anchor())
                    assertTrue(activity.recycler().isShown)
                }
                scenario.onActivity { expectedAnchor = pageAnchor }
                screenshot("pending-only-pagination")
                page.complete(AppResult.Success(Unit))
                eventually(scenario) { activity ->
                    assertFalse((activity.viewModel().uiState.value as ScheduleUiState.Content).loadingMore)
                    assertEquals(28, activity.recycler().adapter!!.itemCount)
                    assertEquals(pageAnchor, activity.anchor())
                }
                assertFalse("Pending-only refresh/pagination hid rows or moved a resting anchor", invalidFrame.get())
            } finally {
                request.complete(AppResult.Success(Unit))
                page.complete(AppResult.Success(Unit))
                scenario.onActivity { root.viewTreeObserver.removeOnPreDrawListener(observer) }
            }
        }

    @Test
    fun pendingOnlyRowsAreRemovedDuringLoadingAndStayRemovedAfterFailure() {
        for (removal in listOf("preference", "services", "error")) {
            withSchedule(initialDays = emptyList()) { scenario ->
                val start = LocalDate.of(2026, 9, 8).atTime(16, 0)
                    .atZone(java.time.ZoneId.of("Europe/Moscow")).toOffsetDateTime()
                ScheduleLifecycleTestActivity.pendingSport.value = DataState.Success(listOf(PendingSportBooking(
                    queueId = 78, queueKind = PendingSportBooking.QueueKind.AUTO, lessonId = 778,
                    sectionName = "Тестовая автозапись без учебных пар", start = start, end = start.plusMinutes(90),
                    teacherFio = "Тестовый преподаватель", roomName = "Тестовый корпус", isPrediction = true
                )))
                ScheduleLifecycleTestActivity.showPendingSport.value = true
                eventually(scenario) { activity ->
                    val current = activity.viewModel().uiState.value
                    assertTrue(current is ScheduleUiState.Content)
                    val state = current as ScheduleUiState.Content
                    assertFalse(state.loadingMore)
                    assertTrue(state.schedule.isEmpty())
                    assertEquals(1, activity.recycler().adapter!!.itemCount)
                    assertTrue(activity.recycler().findViewById<View>(R.id.pending_sport_root)?.isShown == true)
                }

                val request = CompletableDeferred<AppResult<Unit>>()
                ScheduleLifecycleTestActivity.refreshOutcome = { request.await() }
                try {
                    scenario.onActivity { it.viewModel().loadInitialSchedule(forceRefresh = true) }
                    eventually(scenario) { activity ->
                        val current = activity.viewModel().uiState.value
                        assertTrue(current is ScheduleUiState.Content)
                        assertTrue((current as ScheduleUiState.Content).loadingMore)
                        assertEquals(1, activity.recycler().adapter!!.itemCount)
                        assertTrue(activity.recycler().findViewById<View>(R.id.pending_sport_root)?.isShown == true)
                    }
                    when (removal) {
                        "preference" -> ScheduleLifecycleTestActivity.showPendingSport.value = false
                        "services" -> ScheduleLifecycleTestActivity.pendingSport.value = DataState.Success(emptyList())
                        else -> ScheduleLifecycleTestActivity.pendingSport.value = DataState.Error(AppError.Network)
                    }
                    eventually(scenario) { activity ->
                        val root = activity.schedule().requireView()
                        assertTrue(activity.viewModel().uiState.value is ScheduleUiState.Loading)
                        assertEquals("Stale pending rows after $removal", 0, activity.recycler().adapter!!.itemCount)
                        assertNull(activity.recycler().findViewById<View>(R.id.pending_sport_root))
                        assertEquals(View.GONE, root.findViewById<View>(R.id.schedule_state_container).visibility)
                        assertTrue(root.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh_layout).isRefreshing)
                    }
                    request.complete(AppResult.Failure(AppError.Network))
                    eventually(scenario) { activity ->
                        assertEquals(ScheduleUiState.Error(AppError.Network, null), activity.viewModel().uiState.value)
                        assertEquals(0, activity.recycler().adapter!!.itemCount)
                        assertNull(activity.recycler().findViewById<View>(R.id.pending_sport_root))
                        assertState(activity, R.string.common_load_error_title, retryVisible = true)
                        assertFalse(activity.schedule().requireView()
                            .findViewById<SwipeRefreshLayout>(R.id.swipe_refresh_layout).isRefreshing)
                    }
                    ScheduleLifecycleTestActivity.refreshOutcome = { AppResult.Success(Unit) }
                    ScheduleLifecycleTestActivity.days.value = sampleDays()
                    scenario.onActivity { it.viewModel().loadInitialSchedule(forceRefresh = true) }
                    eventually(scenario) { activity ->
                        assertEquals(30, activity.recycler().adapter!!.itemCount)
                        assertTrue(activity.recycler().isShown)
                        assertEquals(View.GONE, activity.schedule().requireView()
                            .findViewById<View>(R.id.schedule_state_container).visibility)
                    }
                } finally {
                    request.complete(AppResult.Success(Unit))
                }
            }
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
                val recycler = root.findViewById<RecyclerView>(R.id.outer_recycler_view)
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
        withSchedule { scenario ->
            val days = installDiffGate(scenario)
            lateinit var activity: ScheduleLifecycleTestActivity
            scenario.onActivity { activity = it }
            try {
                days.arm()
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    ScheduleLifecycleTestActivity.days.value = emptyList()
                }
                assertTrue("Empty diff did not reach its background executor", days.awaitDiff())
                ScheduleLifecycleTestActivity.refreshOutcome = { AppResult.Failure(AppError.Network) }
                // ActivityScenario.onActivity waits for main-loop idleness first.
                // While a diff is deliberately blocked, order the competing state
                // directly on main and release the barrier in that same action.
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    try {
                        activity.viewModel().loadInitialSchedule()
                        assertEquals(ScheduleUiState.Error(AppError.Network, null), activity.viewModel().uiState.value)
                        assertEquals(30, activity.recycler().adapter!!.itemCount)
                    } finally {
                        days.release()
                    }
                }
                eventually(scenario) { activity ->
                    assertEquals(0, activity.recycler().adapter!!.itemCount)
                    assertState(activity, R.string.common_load_error_title, retryVisible = true)
                }
            } finally {
                days.release()
            }
        }
    }

    @Test
    fun refreshStartingBeforeEmptyDiffCommitKeepsLoadingUntilItCompletes() {
        val refresh = CompletableDeferred<Unit>()
        withSchedule { scenario ->
            val days = installDiffGate(scenario)
            lateinit var activity: ScheduleLifecycleTestActivity
            scenario.onActivity { activity = it }
            try {
                days.arm()
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    ScheduleLifecycleTestActivity.days.value = emptyList()
                }
                assertTrue("Empty diff did not reach its background executor", days.awaitDiff())
                ScheduleLifecycleTestActivity.refreshOutcome = {
                    refresh.await()
                    AppResult.Success(Unit)
                }
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    try {
                        activity.viewModel().loadInitialSchedule()
                        assertEquals(ScheduleUiState.Loading(null), activity.viewModel().uiState.value)
                        assertEquals(30, activity.recycler().adapter!!.itemCount)
                    } finally {
                        days.release()
                    }
                }
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
            } finally {
                days.release()
                refresh.complete(Unit)
            }
        }
    }

    @Test
    fun switchingToAFriendKeepsTheVisibleDayAndItsOffset() = withSchedule { scenario ->
        val anchor = scrollToMiddle(scenario)
        val anchoredDay = sampleDays()[anchor.first].date
        // A friend has their own days: the same date sits at another position.
        ScheduleLifecycleTestActivity.friendDays.value = sampleDays(30).drop(3)
        selectFriend(scenario)
        eventually(scenario) { activity ->
            val state = activity.viewModel().uiState.value
            assertTrue(state is ScheduleUiState.Content)
            assertEquals(FRIEND_ISU, (state as ScheduleUiState.Content).selectedUser?.isu)
            assertEquals(27, activity.recycler().adapter!!.itemCount)
            assertTrue("The friend's schedule must be visible", activity.recycler().isShown)
            assertFalse(activity.recycler().hasPendingAdapterUpdates())
            assertEquals(anchoredDay to anchor.second, activity.visibleDay())
        }
        screenshot("friend-schedule-anchored")
    }

    @Test
    fun switchingToAFriendPagesUntilADayBeyondTheInitialRange() =
        withSchedule(sampleDays(60), restrictToRequestedRange = true, initialItemCount = 16) { scenario ->
            val anchor = scrollPastInitialPage(scenario)
            val anchoredDay = sampleDays(60)[anchor.first].date
            // The anchored day was paginated in and is outside the initial range
            // a switched-to schedule starts from.
            ScheduleLifecycleTestActivity.friendDays.value = sampleDays(60).drop(1)
            selectFriend(scenario)
            eventually(scenario) { activity ->
                val state = activity.viewModel().uiState.value
                assertTrue(state is ScheduleUiState.Content)
                assertFalse((state as ScheduleUiState.Content).loadingMore)
                // The anchored day is two pages into the friend's schedule.
                assertTrue(activity.recycler().adapter!!.itemCount >= 29)
                assertTrue("The friend's schedule must be visible", activity.recycler().isShown)
                assertFalse(activity.recycler().hasPendingAdapterUpdates())
                assertEquals(anchoredDay to anchor.second, activity.visibleDay())
            }
        }

    @Test
    fun switchingToAFriendWithoutTheAnchoredDayOpensOnToday() =
        withSchedule(sampleDays(60), restrictToRequestedRange = true, initialItemCount = 16) { scenario ->
            scrollPastInitialPage(scenario)
            // The friend's schedule ends before the anchored day, so paging can
            // never reach it: the switch must settle on today instead of hiding.
            ScheduleLifecycleTestActivity.friendDays.value = sampleDays(10)
            selectFriend(scenario)
            eventually(scenario) { activity ->
                val state = activity.viewModel().uiState.value
                assertTrue(state is ScheduleUiState.Content)
                assertFalse((state as ScheduleUiState.Content).loadingMore)
                assertEquals(10, activity.recycler().adapter!!.itemCount)
                assertTrue("The friend's schedule must be visible", activity.recycler().isShown)
                assertFalse(activity.recycler().hasPendingAdapterUpdates())
                // Today, with the same peek of the previous day a fresh screen has.
                assertEquals(20, activity.dayTop(LocalDate.of(2026, 9, 7)))
            }
        }

    private fun selectFriend(scenario: ActivityScenario<ScheduleLifecycleTestActivity>) {
        scenario.onActivity { activity ->
            activity.supportFragmentManager.setFragmentResult(
                FriendSelectionContract.RESULT_KEY,
                Bundle().apply {
                    putInt(FriendSelectionContract.RESULT_USER_ISU, FRIEND_ISU)
                    putString(FriendSelectionContract.RESULT_USER_NAME, "Тестовый друг")
                }
            )
        }
    }

    private fun withSchedule(
        initialDays: List<DaySchedule> = sampleDays(),
        restrictToRequestedRange: Boolean = false,
        initialItemCount: Int = initialDays.size,
        block: (ActivityScenario<ScheduleLifecycleTestActivity>) -> Unit
    ) {
        ScheduleLifecycleTestActivity.days = MutableStateFlow(initialDays)
        ScheduleLifecycleTestActivity.friendDays = MutableStateFlow(emptyList())
        ScheduleLifecycleTestActivity.refreshOutcome = { AppResult.Success(Unit) }
        ScheduleLifecycleTestActivity.clearOutcome = {}
        ScheduleLifecycleTestActivity.restrictToRequestedRange = restrictToRequestedRange
        ScheduleLifecycleTestActivity.showPendingSport = MutableStateFlow(false)
        ScheduleLifecycleTestActivity.pendingSport = MutableStateFlow(DataState.Success(emptyList()))
        ScheduleLifecycleTestActivity.refreshPendingOutcome = {}
        try {
            ActivityScenario.launch(ScheduleLifecycleTestActivity::class.java).use { scenario ->
                eventually(scenario) { assertEquals(initialItemCount, it.recycler().adapter!!.itemCount) }
                block(scenario)
            }
        } finally {
            ScheduleLifecycleTestActivity.days = MutableStateFlow(emptyList())
            ScheduleLifecycleTestActivity.friendDays = MutableStateFlow(emptyList())
            ScheduleLifecycleTestActivity.refreshOutcome = { AppResult.Success(Unit) }
            ScheduleLifecycleTestActivity.clearOutcome = {}
            ScheduleLifecycleTestActivity.restrictToRequestedRange = false
            ScheduleLifecycleTestActivity.showPendingSport = MutableStateFlow(false)
            ScheduleLifecycleTestActivity.pendingSport = MutableStateFlow(DataState.Success(emptyList()))
            ScheduleLifecycleTestActivity.refreshPendingOutcome = {}
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
        eventually(scenario) { activity ->
            val recycler = activity.recycler()
            // A source emission, AsyncListDiffer commit, and restored-anchor
            // layout are separate events; main-loop idleness alone is not enough.
            assertTrue("The restored schedule must be visible", recycler.isShown)
            assertTrue("The anchor's day has not been committed", recycler.adapter!!.itemCount > anchor.first)
            assertFalse("The committed schedule still has pending adapter updates", recycler.hasPendingAdapterUpdates())
            assertFalse("The restored anchor still needs layout", recycler.isLayoutRequested)
            assertEquals(anchor, activity.anchor())
        }
    }

    /** Layout can be committed before the device compositor presents its new buffer. */
    private fun screenshot(name: String) = Screenshots.capture("schedule-refresh-screenshots", name) { TestUi.settle(300) }

    private fun ScheduleLifecycleTestActivity.anchor(): Pair<Int, Int> {
        val recycler = recycler()
        val layout = recycler.layoutManager as LinearLayoutManager
        val position = layout.findFirstVisibleItemPosition()
        assertTrue("The schedule has not laid out a visible day", position != RecyclerView.NO_POSITION)
        val firstDay = layout.findViewByPosition(position)
            ?: throw AssertionError("The first visible day at $position has not been attached")
        return position to (firstDay.top - recycler.paddingTop)
    }

    private fun ScheduleLifecycleTestActivity.visibleDay(): Pair<LocalDate, Int> {
        val recycler = recycler()
        val layout = recycler.layoutManager as LinearLayoutManager
        val position = layout.findFirstVisibleItemPosition()
        assertTrue("The schedule has not laid out a visible day", position != RecyclerView.NO_POSITION)
        val firstDay = layout.findViewByPosition(position)
            ?: throw AssertionError("The first visible day at $position has not been attached")
        val date = (recycler.adapter as DayScheduleAdapter).currentList[position].date
        return date to (firstDay.top - recycler.paddingTop)
    }

    private fun ScheduleLifecycleTestActivity.dayTop(date: LocalDate): Int? {
        val recycler = recycler()
        val index = (recycler.adapter as DayScheduleAdapter).currentList.indexOfFirst { it.date == date }
        assertTrue("The schedule has no $date", index != -1)
        val day = (recycler.layoutManager as LinearLayoutManager).findViewByPosition(index)
        return day?.top?.minus(recycler.paddingTop)
    }

    private fun ScheduleLifecycleTestActivity.schedule() =
        supportFragmentManager.findFragmentByTag(ScheduleLifecycleTestActivity.SCHEDULE_TAG) as ScheduleFragment

    private fun ScheduleLifecycleTestActivity.recycler() = schedule().requireView().findViewById<RecyclerView>(R.id.outer_recycler_view)

    private fun ScheduleLifecycleTestActivity.viewModel() = ViewModelProvider(schedule())[ScheduleViewModel::class.java]

    private fun ScheduleLifecycleTestActivity.showAnotherScreen() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.schedule_test_container, Fragment())
            .addToBackStack("details")
            .commit()
        supportFragmentManager.executePendingTransactions()
    }

    private fun eventually(scenario: ActivityScenario<ScheduleLifecycleTestActivity>, assertion: (ScheduleLifecycleTestActivity) -> Unit) =
        TestUi.eventually(attempts = 40, delayMillis = 50, idleBetween = true) { scenario.onActivity(assertion) }

    private fun sampleDays(count: Int = 30) = (0 until count).map { offset ->
        val date = LocalDate.of(2026, 9, 6).plusDays(offset.toLong())
        DaySchedule(date.dayOfWeek.value, 1, date, null, emptyList())
    }

    private fun installDiffGate(scenario: ActivityScenario<ScheduleLifecycleTestActivity>): GatedDays<ScheduleDisplayDay> {
        lateinit var days: GatedDays<ScheduleDisplayDay>
        val committed = AtomicBoolean(false)
        scenario.onActivity { activity ->
            val adapter = activity.recycler().adapter as DayScheduleAdapter
            days = GatedDays(adapter.currentList.toList())
            adapter.submitList(days) { committed.set(true) }
        }
        eventually(scenario) { assertTrue("The gated display list was not committed", committed.get()) }
        return days
    }

    private companion object {
        const val FRIEND_ISU = 123456
    }

    /**
     * Holds DiffUtil's background size read; UI-thread reads never wait. Explicit
     * release is paired with finally in each test, so no wall-clock deadline can
     * silently commit the old diff before the competing state has been asserted.
     */
    private class GatedDays<T>(private val values: List<T>) : AbstractList<T>() {
        private val armed = AtomicBoolean(false)
        private val reached = CountDownLatch(1)
        private val released = CountDownLatch(1)

        override val size: Int
            get() {
                if (armed.get() && Looper.myLooper() != Looper.getMainLooper()) {
                    reached.countDown()
                    released.await()
                }
                return values.size
            }

        override fun get(index: Int): T = values[index]

        fun arm() = armed.set(true)

        fun awaitDiff(): Boolean = reached.await(2, TimeUnit.SECONDS)

        fun release() {
            armed.set(false)
            released.countDown()
        }
    }
}
