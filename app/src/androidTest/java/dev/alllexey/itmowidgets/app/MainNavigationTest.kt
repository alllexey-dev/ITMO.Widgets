package dev.alllexey.itmowidgets.app

import android.graphics.Rect
import android.os.Bundle
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
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
            onView(withId(R.id.back_button)).perform(click())
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

    /** Called from the main thread inside `onActivity`. */
    private fun capture(activity: SettingsNavigationTestActivity, name: String) {
        val config = activity.resources.configuration
        Screenshots.draw("navigation-screenshots", "${config.uiMode}-${config.screenWidthDp}-${config.fontScale}-$name", activity.binding.root)
    }

    private fun bounds(view: View) = Rect(view.left, view.top, view.right, view.bottom)

    private fun settle() = TestUi.settle(500)
}
