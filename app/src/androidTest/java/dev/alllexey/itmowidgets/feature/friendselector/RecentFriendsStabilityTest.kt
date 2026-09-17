package dev.alllexey.itmowidgets.feature.friendselector

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.FriendSelectorFixture
import dev.alllexey.itmowidgets.app.SettingsNavigationTestActivity
import dev.alllexey.itmowidgets.core.friend.FriendListState
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.feature.friendselector.ui.FriendSelectorDialogFragment
import dev.alllexey.itmowidgets.feature.friendselector.ui.RecentFriendAdapter
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecentFriendsStabilityTest {
    @Test fun recentTargetsStayPutUntilConfirmationAcrossThemesRefreshAndRecreation() {
        try {
            listOf(
                SettingsNavigationTestActivity.Appearance(),
                SettingsNavigationTestActivity.Appearance(dark = true),
                SettingsNavigationTestActivity.Appearance(fontScale = 1.3f, colorSeed = 0xff087f5b.toInt()),
                SettingsNavigationTestActivity.Appearance(dark = true, fontScale = 1.3f, colorSeed = 0xff087f5b.toInt())
            ).forEachIndexed { index, appearance ->
                SettingsNavigationTestActivity.appearance = appearance
                val fixture = FriendSelectorFixture().apply { friendState.value = FriendListState.Loading }
                SettingsNavigationTestActivity.friendSelectorFixture = fixture
                ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
                    scenario.onActivity { open(it, 100001) }
                    settle()
                    scenario.onActivity {
                        assertEquals(1, recent(it).adapter!!.itemCount)
                        assertTrue(root(it).findViewById<View>(R.id.progress).isShown)
                    }
                    capture("recent-loading-$index")
                    fixture.friendState.value = FriendListState.Content(fixture.friends)
                    settle()
                    val expected = listOf(Long.MIN_VALUE, 100001L, 100002L, 100003L, 100004L, 100005L)
                    var moves = 0
                    var resets = 0
                    scenario.onActivity {
                        val adapter = recent(it).adapter!!
                        assertTrue(adapter.hasStableIds())
                        assertEquals(expected, ids(recent(it)))
                        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                            override fun onChanged() { resets++ }
                            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) { moves += itemCount }
                        })
                    }
                    capture("recent-before-$index")
                    repeat(2) {
                        scenario.onActivity { activity ->
                            val list = recent(activity)
                            list.findViewHolderForItemId(100002)!!.itemView.performClick()
                        }
                        settle()
                        scenario.onActivity { activity ->
                            val list = recent(activity)
                            assertEquals(expected, ids(list))
                            assertTrue(list.findViewHolderForItemId(100002)!!.itemView.isSelected)
                            assertTrue(fixture.recorded.isEmpty())
                        }
                    }
                    scenario.onActivity { recent(it).scrollToPosition(5) }
                    settle()
                    lateinit var bounds: Map<Long, Rect>
                    scenario.onActivity { activity ->
                        val list = recent(activity)
                        bounds = bounds(list)
                    }
                    capture("recent-scrolled-before-$index")
                    scenario.onActivity { recent(it).findViewHolderForItemId(100005)!!.itemView.performClick() }
                    settle()
                    scenario.onActivity {
                        assertEquals(expected, ids(recent(it)))
                        assertEquals(bounds, bounds(recent(it)))
                        assertEquals(0, moves)
                        assertEquals(0, resets)
                    }
                    capture("recent-selected-$index")
                    scenario.recreate()
                    settle()
                    scenario.onActivity {
                        assertEquals(expected, ids(recent(it)))
                        assertEquals(bounds, bounds(recent(it)))
                        assertTrue(recent(it).findViewHolderForItemId(100005)!!.itemView.isSelected)
                        recent(it).scrollToPosition(0)
                    }
                    settle()
                    scenario.onActivity { recent(it).findViewHolderForItemId(Long.MIN_VALUE)!!.itemView.performClick() }
                    settle()
                    // A late own profile and externally updated history must not undo "My schedule" or move chips.
                    fixture.recentIsus = fixture.recentIsus.reversed()
                    fixture.currentUser.value = fixture.friends.first().copy(isu = 200000, name = "Тестовый пользователь")
                    fixture.friendState.value = FriendListState.Content(fixture.friends.reversed())
                    settle()
                    scenario.onActivity {
                        assertEquals(expected, ids(recent(it)))
                        assertTrue(recent(it).findViewHolderForItemId(Long.MIN_VALUE)!!.itemView.isSelected)
                        root(it).findViewById<TextView>(R.id.search_input).text = "100007"
                    }
                    settle()
                    scenario.onActivity {
                        val list = root(it).findViewById<RecyclerView>(R.id.recycler_view)
                        assertEquals(1, list.adapter!!.itemCount)
                        list.getChildAt(0).performClick()
                    }
                    settle()
                    scenario.onActivity {
                        assertEquals(expected, ids(recent(it)))
                        assertTrue(fixture.recorded.isEmpty())
                        root(it).findViewById<View>(R.id.apply_button).performClick()
                    }
                    settle()
                    assertEquals(listOf(100007), fixture.recorded)
                    assertEquals(listOf(100007), fixture.results)
                    scenario.onActivity { open(it, 100007) }
                    settle()
                    scenario.onActivity {
                        assertEquals(100007L, recent(it).adapter!!.getItemId(1))
                        assertEquals(6, recent(it).adapter!!.itemCount)
                        recent(it).findViewHolderForItemId(100005)!!.itemView.performClick()
                    }
                    val reopenedOrder = listOf(Long.MIN_VALUE, 100007L, 100005L, 100004L, 100003L, 100002L)
                    for ((name, state) in listOf(
                        "error" to FriendListState.Error(AppError.Network),
                        "disabled" to FriendListState.Disabled
                    )) {
                        fixture.friendState.value = state
                        settle()
                        scenario.onActivity {
                            assertEquals(reopenedOrder, ids(recent(it)))
                            assertFalse(root(it).findViewById<View>(R.id.apply_button).isEnabled)
                            assertTrue(root(it).findViewById<View>(R.id.state_container).isShown)
                        }
                        capture("recent-$name-$index")
                    }
                    fixture.friendState.value = FriendListState.Content(emptyList())
                    settle()
                    capture("recent-empty-$index")
                    scenario.onActivity {
                        assertEquals(listOf(Long.MIN_VALUE), ids(recent(it)))
                        assertTrue(root(it).findViewById<View>(R.id.apply_button).isEnabled)
                    }
                    fixture.friendState.value = FriendListState.Content(fixture.friends)
                    settle()
                    scenario.onActivity {
                        assertEquals(reopenedOrder, ids(recent(it)))
                        assertTrue(recent(it).findViewHolderForItemId(Long.MIN_VALUE)!!.itemView.isSelected)
                        root(it).findViewById<View>(R.id.close_button).performClick()
                    }
                    settle()
                    assertEquals(listOf(100007), fixture.recorded)
                    assertEquals(listOf(100007), fixture.results)
                }
            }
        } finally {
            SettingsNavigationTestActivity.appearance = SettingsNavigationTestActivity.Appearance()
            SettingsNavigationTestActivity.friendSelectorFixture = FriendSelectorFixture()
        }
    }

    @Test fun ownProfileUpdatesAndIdenticalSubmissionsDoNotResetOrMoveAdapterItems() {
        ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
            scenario.onActivity {
                val fixture = FriendSelectorFixture()
                val adapter = RecentFriendAdapter {}
                var resets = 0
                var moves = 0
                adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                    override fun onChanged() { resets++ }
                    override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) { moves += itemCount }
                })
                adapter.submitItems(fixture.friends.take(5), 100001, null)
                val expected = (0 until adapter.itemCount).map(adapter::getItemId)
                adapter.setSelectedIsu(100004)
                adapter.setSelectedIsu(null)
                adapter.setCurrentUser(fixture.friends.last())
                adapter.submitItems(fixture.friends.take(5), null, fixture.friends.last())
                assertEquals(expected, (0 until adapter.itemCount).map(adapter::getItemId))
                assertEquals(0, resets)
                assertEquals(0, moves)
            }
        }
    }

    private fun open(activity: SettingsNavigationTestActivity, selected: Int) {
        FriendSelectorDialogFragment.show(activity.supportFragmentManager, selected)
    }

    private fun root(activity: SettingsNavigationTestActivity): View =
        activity.supportFragmentManager.findFragmentByTag(FriendSelectorDialogFragment.TAG)!!.requireView()
    private fun recent(activity: SettingsNavigationTestActivity) = root(activity).findViewById<RecyclerView>(R.id.recent_recycler_view)
    private fun ids(list: RecyclerView) = (0 until list.adapter!!.itemCount).map(list.adapter!!::getItemId)
    private fun bounds(list: RecyclerView): Map<Long, Rect> = (0 until list.childCount).associate { index ->
        val child = list.getChildAt(index)
        list.getChildViewHolder(child).itemId to Rect(child.left, child.top, child.right, child.bottom)
    }
    private fun settle() { InstrumentationRegistry.getInstrumentation().waitForIdleSync(); SystemClock.sleep(400) }
    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val directory = File(instrumentation.targetContext.externalCacheDir, "recent-friends-screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
