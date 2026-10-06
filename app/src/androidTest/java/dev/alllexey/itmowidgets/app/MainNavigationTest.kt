package dev.alllexey.itmowidgets.app

import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.feature.debug.ui.DebugToolsTestTags
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPage
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestUi
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainNavigationTest {
    @Test
    fun nestedSettingsNeverResizeOrReplaceRootAndBackUncoversSameProfile() {
        ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
            scenario.onActivity { assertTrue(it.navigation.selectRoot(R.id.navigation_me)) }
            settle()
            lateinit var profile: View
            lateinit var bounds: Rect
            lateinit var barBounds: Rect
            scenario.onActivity {
                profile = it.host.childFragmentManager.primaryNavigationFragment!!.requireView()
                bounds = bounds(it.binding.navHostFragment)
                barBounds = bounds(it.binding.bottomNavView)
                capture(it, "profile")
            }
            onView(withId(R.id.settings_row)).perform(click())
            settle()
            scenario.onActivity { capture(it, "settings") }
            onView(withText(R.string.settings_qr_short_title)).perform(click())
            settle()
            scenario.onActivity { capture(it, "qr") }
            repeat(2) {
                scenario.onActivity {
                    assertSame(profile, it.host.childFragmentManager.primaryNavigationFragment!!.requireView())
                    assertEquals(bounds, bounds(it.binding.navHostFragment))
                    assertEquals(barBounds, bounds(it.binding.bottomNavView))
                    assertEquals(View.VISIBLE, it.binding.bottomNavView.visibility)
                    assertTrue(it.binding.overlayContainer.z > it.binding.bottomNavView.z)
                    assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, it.binding.navHostFragment.importantForAccessibility)
                    assertSame(it.navigation.overlayHost, it.supportFragmentManager.primaryNavigationFragment)
                }
                onView(withId(R.id.back_button)).perform(click())
                settle()
            }
            scenario.onActivity {
                capture(it, "returned-profile")
                assertNull(it.navigation.overlayHost)
                assertSame(profile, it.host.childFragmentManager.primaryNavigationFragment!!.requireView())
                assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, it.binding.navHostFragment.importantForAccessibility)
                assertSame(it.host, it.supportFragmentManager.primaryNavigationFragment)
                assertEquals(bounds, bounds(it.binding.navHostFragment))
                assertTrue(it.overlayEnteredReady.isNotEmpty())
                assertTrue(it.overlayEnteredReady.all { ready -> ready })
            }
        }
    }

    @Test
    fun everyRootChangeAndReselectionDiscardsEntireContextualHistory() {
        ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
            for (destination in MainNavigationCoordinator.ROOTS) {
                scenario.onActivity { it.navigation.selectRoot(R.id.navigation_me) }
                settle()
                onView(withId(R.id.settings_row)).perform(click())
                settle()
                onView(withText(R.string.settings_qr_short_title)).perform(click())
                settle()
                scenario.onActivity { assertTrue(it.navigation.selectRoot(destination)) }
                settle()
                scenario.onActivity {
                    assertEquals(destination, it.host.navController.currentDestination?.id)
                    assertNull(it.navigation.overlayHost)
                    assertEquals(0, it.supportFragmentManager.backStackEntryCount)
                    assertTrue(it.navigation.selectRoot(R.id.navigation_me))
                }
                settle()
                scenario.onActivity {
                    assertEquals(R.id.navigation_me, it.host.navController.currentDestination?.id)
                    assertNull(it.navigation.overlayHost)
                    assertNotNull(it.host.requireView().findViewById<View>(R.id.settings_row))
                }
            }
        }
    }

    @Test
    fun recreationRestoresOverlayDepthButFollowingRootSelectionDoesNot() {
        ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
            scenario.onActivity { it.navigation.selectRoot(R.id.navigation_me) }
            settle()
            onView(withId(R.id.settings_row)).perform(click())
            settle()
            onView(withText(R.string.settings_qr_short_title)).perform(click())
            settle()
            scenario.recreate()
            settle()
            scenario.onActivity {
                assertEquals(R.id.navigation_me, it.host.navController.currentDestination?.id)
                assertEquals(SettingsPage.QR_WIDGET.name, it.navigation.overlayHost!!.navController.currentBackStackEntry!!.arguments!!.getString(SettingsPage.ARGUMENT))
                assertEquals(1, it.supportFragmentManager.backStackEntryCount)
                assertSame(it.navigation.overlayHost, it.supportFragmentManager.primaryNavigationFragment)
            }
            onView(withId(R.id.back_button)).perform(click())
            settle()
            scenario.onActivity {
                assertEquals(SettingsPage.ROOT.name, it.navigation.overlayHost!!.navController.currentBackStackEntry!!.arguments!!.getString(SettingsPage.ARGUMENT))
                // This is also the path used by MainActivity for a schedule-widget intent.
                assertTrue(it.navigation.selectRoot(R.id.navigation_schedule))
            }
            settle()
            scenario.recreate()
            settle()
            scenario.onActivity {
                assertNull(it.navigation.overlayHost)
                assertEquals(R.id.navigation_schedule, it.host.navController.currentDestination?.id)
                it.navigation.selectRoot(R.id.navigation_me)
            }
            settle()
            scenario.onActivity { assertNull(it.navigation.overlayHost) }
        }
    }

    @Test
    fun debugSectionUsesSameOverlayAndRejectsUnknownRootWithoutClosingIt() {
        ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.navigation.selectRoot(R.id.navigation_me)
                it.openScreen(AppScreen.DEBUG_TOOLS, Bundle.EMPTY)
            }
            settle()
            scenario.onActivity {
                assertEquals(R.id.debug_tools, it.navigation.overlayHost!!.navController.currentDestination?.id)
                assertFalse(it.navigation.selectRoot(R.id.settings))
                assertNotNull(it.navigation.overlayHost)
                assertEquals(View.VISIBLE, it.binding.bottomNavView.visibility)
            }
            scenario.onActivity { clickDebugToolsBack(it) }
            settle()
            scenario.onActivity {
                assertNull(it.navigation.overlayHost)
                assertEquals(R.id.navigation_me, it.host.navController.currentDestination?.id)
            }
        }
    }

    @Test
    fun rootChangeDuringPendingEnterDoesNotLeaveBlockingSurface() {
        ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
            scenario.onActivity { it.navigation.selectRoot(R.id.navigation_me) }
            settle()
            scenario.onActivity {
                it.openScreen(AppScreen.SETTINGS)
                assertTrue(it.navigation.selectRoot(R.id.navigation_schedule))
            }
            settle()
            scenario.onActivity {
                assertNull(it.navigation.overlayHost)
                assertEquals(0, it.binding.overlayContainer.childCount)
                assertEquals(0, it.supportFragmentManager.backStackEntryCount)
                assertEquals(R.id.navigation_schedule, it.host.navController.currentDestination?.id)
                assertSame(it.host, it.supportFragmentManager.primaryNavigationFragment)
                assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, it.binding.bottomNavView.importantForAccessibility)
            }
        }
    }

    @Test
    fun everyRootKeepsItsStateAcrossSwitchesAndRecreation() {
        SettingsNavigationTestActivity.homeFixture = HomeFixture()
        ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
            settle()
            scenario.onActivity { (it.feed().layoutManager as LinearLayoutManager).scrollToPositionWithOffset(3, -24) }
            settle()
            var feedAnchor = 0 to 0
            scenario.onActivity {
                feedAnchor = it.feedAnchor()
                assertTrue("The feed must be scrolled away from its top", feedAnchor != 0 to 0)
            }
            scenario.onActivity { assertTrue(it.navigation.selectRoot(R.id.navigation_schedule)) }
            settle()
            scenario.onActivity { it.blankScroll().scrollTo(0, BLANK_SCROLL) }
            settle()

            fun assertKept() {
                for (destination in listOf(
                    R.id.navigation_home, R.id.navigation_schedule, R.id.navigation_me,
                    R.id.navigation_home, R.id.navigation_schedule
                )) {
                    scenario.onActivity { assertTrue(it.navigation.selectRoot(destination)) }
                    settle()
                    scenario.onActivity {
                        val controller = it.host.navController
                        assertEquals(destination, controller.currentDestination?.id)
                        val previous = controller.previousBackStackEntry?.destination?.id
                        assertTrue("The stack grew: $previous", previous == null || previous == R.id.navigation_home)
                        when (destination) {
                            R.id.navigation_home -> assertEquals(feedAnchor, it.feedAnchor())
                            R.id.navigation_schedule -> assertEquals(BLANK_SCROLL, it.blankScroll().scrollY)
                        }
                    }
                }
            }

            assertKept()
            scenario.recreate()
            settle()
            scenario.onActivity { assertEquals(BLANK_SCROLL, it.blankScroll().scrollY) }
            assertKept()
        }
    }

    @Test
    fun reselectingTheBarItemPopsTheRootsDialog() {
        ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
            scenario.onActivity { assertTrue(it.navigation.selectRoot(R.id.navigation_schedule)) }
            settle()
            scenario.onActivity {
                it.blankScroll().scrollTo(0, BLANK_SCROLL)
                it.host.navController.navigate(R.id.friend_selector)
            }
            settle()
            scenario.onActivity {
                assertEquals(R.id.friend_selector, it.host.navController.currentDestination?.id)
                it.binding.bottomNavView.selectedItemId = R.id.navigation_schedule
            }
            settle()
            scenario.onActivity {
                assertEquals(R.id.navigation_schedule, it.host.navController.currentDestination?.id)
                assertTrue(it.host.childFragmentManager.fragments.none { fragment -> fragment is DialogFragment })
                assertEquals(BLANK_SCROLL, it.blankScroll().scrollY)
            }
        }
    }

    @Test
    fun everyFullScreenDestinationCoversTheBar() {
        val screens = listOf(
            AppScreen.SETTINGS to null,
            AppScreen.QR_PASS to null,
            AppScreen.USER_PROFILE to Bundle().apply { putInt(UserScreenArgs.ISU, 100001) },
            AppScreen.MY_ITMO_WEB to null
        )
        ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
            for ((screen, arguments) in screens) {
                scenario.onActivity { it.openScreen(screen, arguments) }
                settle()
                scenario.onActivity {
                    val overlay = checkNotNull(it.navigation.overlayHost) { "$screen did not open" }
                    assertTrue(screen.name, windowBounds(it.binding.overlayContainer).contains(windowBounds(it.binding.bottomNavView)))
                    assertTrue(screen.name, it.binding.overlayContainer.z > it.binding.bottomNavView.z)
                    val root = overlay.requireView()
                    assertTrue(screen.name, root.isClickable)
                    assertEquals(screen.name, 255, (root.background as ColorDrawable).alpha)
                    assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, it.binding.bottomNavView.importantForAccessibility)

                    // A tap where the bar is drawn lands on the overlay.
                    val bar = windowBounds(it.binding.bottomNavView)
                    val time = SystemClock.uptimeMillis()
                    listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
                        val event = MotionEvent.obtain(time, time, action, bar.exactCenterX(), bar.exactCenterY(), 0)
                        it.dispatchTouchEvent(event)
                        event.recycle()
                    }
                }
                settle()
                scenario.onActivity {
                    assertEquals(screen.name, R.id.navigation_home, it.host.navController.currentDestination?.id)
                    assertEquals(screen.name, R.id.navigation_home, it.binding.bottomNavView.selectedItemId)
                }
                // The tap may have opened something inside the overlay; Back leaves it level by level.
                repeat(3) {
                    scenario.onActivity { if (it.navigation.overlayHost != null) it.onBackPressedDispatcher.onBackPressed() }
                    settle()
                }
                scenario.onActivity {
                    assertNull(screen.name, it.navigation.overlayHost)
                    assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, it.binding.bottomNavView.importantForAccessibility)
                }
            }
        }
    }

    @Test
    fun backFromAnotherRootReturnsHome() {
        ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
            scenario.onActivity { assertTrue(it.navigation.selectRoot(R.id.navigation_schedule)) }
            settle()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            settle()
            scenario.onActivity {
                assertEquals(R.id.navigation_home, it.host.navController.currentDestination?.id)
                assertNull(it.host.navController.previousBackStackEntry)
            }
        }
    }

    private fun SettingsNavigationTestActivity.feed(): RecyclerView =
        host.childFragmentManager.primaryNavigationFragment!!.requireView().findViewById(R.id.home_feed)

    /** The first visible card of the feed and its offset from the top of the list. */
    private fun SettingsNavigationTestActivity.feedAnchor(): Pair<Int, Int> {
        val feed = feed()
        val layout = feed.layoutManager as LinearLayoutManager
        val position = layout.findFirstVisibleItemPosition()
        return position to (layout.findViewByPosition(position)!!.top - feed.paddingTop)
    }

    private fun SettingsNavigationTestActivity.blankScroll(): ScrollView =
        host.childFragmentManager.primaryNavigationFragment!!.requireView().findViewById(R.id.blank_tab_scroll)

    private fun windowBounds(view: View): Rect {
        val location = IntArray(2).also(view::getLocationInWindow)
        return Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
    }

    /** Called from the main thread inside `onActivity`. */
    private fun capture(activity: SettingsNavigationTestActivity, name: String) {
        val config = activity.resources.configuration
        Screenshots.draw("navigation-screenshots", "${config.uiMode}-${config.screenWidthDp}-${config.fontScale}-$name", activity.binding.root)
    }

    private fun bounds(view: View) = Rect(view.left, view.top, view.right, view.bottom)

    /** Taps the Compose back button of the debug tools through its test tag (no compose test rule on this classpath). */
    private fun clickDebugToolsBack(activity: SettingsNavigationTestActivity) {
        val root = activity.navigation.overlayHost!!.childFragmentManager.primaryNavigationFragment!!.requireView()
        val owner = ((root as ViewGroup).getChildAt(0) as ViewRootForTest).semanticsOwner
        val back = generateSequence(listOf(owner.unmergedRootSemanticsNode)) { level ->
            level.flatMap { it.children }.ifEmpty { null }
        }.flatten().first { it.config.getOrNull(SemanticsProperties.TestTag) == DebugToolsTestTags.BACK }
        assertTrue(back.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke() == true)
    }

    private fun settle() = TestUi.settle(500)

    private companion object {
        const val BLANK_SCROLL = 600
    }
}
