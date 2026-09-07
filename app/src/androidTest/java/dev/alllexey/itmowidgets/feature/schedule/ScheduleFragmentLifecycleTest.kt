package dev.alllexey.itmowidgets.feature.schedule

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.ui.ScheduleFragment
import dev.alllexey.itmowidgets.feature.schedule.ui.ScheduleLifecycleTestActivity
import java.time.LocalDate
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

    private fun withSchedule(block: (ActivityScenario<ScheduleLifecycleTestActivity>) -> Unit) {
        ScheduleLifecycleTestActivity.days = MutableStateFlow(sampleDays())
        ActivityScenario.launch(ScheduleLifecycleTestActivity::class.java).use { scenario ->
            eventually(scenario) { assertEquals(30, it.recycler().adapter!!.itemCount) }
            block(scenario)
        }
        ScheduleLifecycleTestActivity.days = MutableStateFlow(emptyList())
    }

    private fun scrollToMiddle(scenario: ActivityScenario<ScheduleLifecycleTestActivity>): Pair<Int, Int> {
        scenario.onActivity { (it.recycler().layoutManager as LinearLayoutManager).scrollToPositionWithOffset(8, -17) }
        eventually(scenario) { assertEquals(8, (it.recycler().layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()) }
        var anchor = 0 to 0
        scenario.onActivity { anchor = it.anchor() }
        return anchor
    }

    private fun awaitAnchor(scenario: ActivityScenario<ScheduleLifecycleTestActivity>, anchor: Pair<Int, Int>) {
        eventually(scenario) { assertEquals(anchor, it.anchor()) }
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
}
