package dev.alllexey.itmowidgets.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.navigation.fragment.NavHostFragment
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.viewpager2.widget.ViewPager2
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportFragment
import dev.alllexey.itmowidgets.testing.TestSession
import dev.alllexey.itmowidgets.testing.TestUi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Widget, notification, tile, shortcut and App Link intents on the real `MainActivity`. */
@RunWith(AndroidJUnit4::class)
class MainActivityDeepLinkTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @After
    fun clearSessionAndFirstRunFlag() {
        TestSession.signOut()
        TestSession.resetOnboarding()
    }

    @Test
    fun qrShortcutFromColdStartOpensThePassAboveHome() {
        signedIn()
        ActivityScenario.launch<MainActivity>(route(MainActivity.ACTION_OPEN_QR_PASS)).use {
            awaitRoute(R.id.navigation_home, R.id.qr_pass)
            back()
            awaitRoute(R.id.navigation_home, overlay = null)
            onActivity { assertNull(root(it).navController.previousBackStackEntry) }
        }
    }

    @Test
    fun todayShortcutOpensTheScheduleAndBackReturnsHome() {
        signedIn()
        ActivityScenario.launch<MainActivity>(route(MainActivity.ACTION_OPEN_TODAY)).use {
            awaitRoute(R.id.navigation_schedule, overlay = null)
            back()
            awaitRoute(R.id.navigation_home, overlay = null)
        }
    }

    @Test
    fun routeWaitsForSignInAndOnboarding() {
        TestSession.signOut()
        TestSession.resetOnboarding()
        ActivityScenario.launch<MainActivity>(route(MainActivity.ACTION_OPEN_QR_PASS)).use {
            awaitRoute(R.id.auth, overlay = null)
            TestSession.seedActiveSession()
            awaitRoute(R.id.onboarding, overlay = null)
            TestSession.completeOnboarding()
            awaitRoute(R.id.navigation_home, R.id.qr_pass)
        }
    }

    @Test
    fun routeRunsOnceAcrossRecreation() {
        signedIn()
        ActivityScenario.launch<MainActivity>(route(MainActivity.ACTION_OPEN_QR_PASS)).use { scenario ->
            awaitRoute(R.id.navigation_home, R.id.qr_pass)
            scenario.recreate()
            awaitRoute(R.id.navigation_home, R.id.qr_pass)
            onActivity { activity ->
                assertEquals(1, activity.supportFragmentManager.backStackEntryCount)
                assertNull(overlay(activity)!!.navController.previousBackStackEntry)
            }
            back()
            awaitRoute(R.id.navigation_home, overlay = null)
            scenario.recreate()
            awaitRoute(R.id.navigation_home, overlay = null)
        }
    }

    @Test
    fun routeArrivingWhileOpenReplacesTheOverlay() {
        signedIn()
        // ActivityScenario cannot close an activity that went through onNewIntent; this one finishes itself.
        instrumentation.startActivitySync(route(MainActivity.ACTION_OPEN_SPORT))
        try {
            awaitRoute(R.id.navigation_sport, overlay = null)
            onActivity { it.openScreen(AppScreen.SETTINGS, null) }
            awaitRoute(R.id.navigation_sport, R.id.settings)
            // What a tile or a widget sends to a running task: the same instance gets onNewIntent.
            context.startActivity(
                route(MainActivity.ACTION_OPEN_QR_PASS)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            awaitRoute(R.id.navigation_home, R.id.qr_pass)
            onActivity { assertEquals(1, it.supportFragmentManager.backStackEntryCount) }
            back()
            awaitRoute(R.id.navigation_home, overlay = null)
        } finally {
            runCatching { onActivity { it.finish() } }
        }
    }

    @Test
    fun launchFromRecentsDoesNotRepeatTheRoute() {
        signedIn()
        val intent = route(MainActivity.ACTION_OPEN_QR_PASS).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        ActivityScenario.launch<MainActivity>(intent).use {
            awaitRoute(R.id.navigation_home, overlay = null)
            // The route never runs later either.
            TestUi.settle(500)
            awaitRoute(R.id.navigation_home, overlay = null)
        }
    }

    @Test
    fun notificationAndWidgetRoutesReturnHomeOnBack() {
        signedIn()
        mapOf(
            MainActivity.ACTION_OPEN_SCHEDULE to R.id.navigation_schedule,
            MainActivity.ACTION_OPEN_SPORT to R.id.navigation_sport,
            MainActivity.ACTION_OPEN_RECORDBOOK to R.id.navigation_recordbook
        ).forEach { (action, destination) ->
            ActivityScenario.launch<MainActivity>(route(action)).use {
                awaitRoute(destination, overlay = null)
                back()
                awaitRoute(R.id.navigation_home, overlay = null)
                onActivity { assertNull(action, root(it).navController.previousBackStackEntry) }
            }
        }
    }

    @Test
    fun profileLinkOpensTheProfileAboveTheProfileTab() {
        signedIn()
        ActivityScenario.launch<MainActivity>(link("/u/100001")).use {
            awaitRoute(R.id.navigation_me, R.id.user_profile)
            onActivity { activity ->
                val arguments = overlay(activity)!!.navController.currentBackStackEntry?.arguments
                assertEquals(100001, arguments?.getInt(UserScreenArgs.ISU))
            }
            back()
            awaitRoute(R.id.navigation_me, overlay = null)
            back()
            awaitRoute(R.id.navigation_home, overlay = null)
        }
    }

    @Test
    fun sportLinkOpensTheSignPage() {
        signedIn()
        listOf("/sport/1", "/sport/p/1").forEach { path ->
            ActivityScenario.launch<MainActivity>(link(path)).use {
                awaitRoute(R.id.navigation_sport, overlay = null)
                eventually {
                    onActivity { activity ->
                        val sport = root(activity).childFragmentManager.fragments.filterIsInstance<SportFragment>().single()
                        assertEquals(path, 1, sport.requireView().findViewById<ViewPager2>(R.id.sport_view_pager).currentItem)
                    }
                }
            }
        }
    }

    @Test
    fun malformedLinkShowsTheUnavailableDialogAtHome() {
        signedIn()
        ActivityScenario.launch<MainActivity>(link("/sport/abc")).use {
            awaitRoute(R.id.navigation_home, overlay = null)
            onView(withText(R.string.app_link_unavailable_title)).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText(R.string.common_got_it)).inRoot(isDialog()).perform(click())
            awaitRoute(R.id.navigation_home, overlay = null)
        }
    }

    @Test
    fun linkWaitsForSignIn() {
        TestSession.signOut()
        TestSession.resetOnboarding()
        ActivityScenario.launch<MainActivity>(link("/u/100001")).use {
            awaitRoute(R.id.auth, overlay = null)
            TestSession.seedActiveSession()
            awaitRoute(R.id.onboarding, overlay = null)
            TestSession.completeOnboarding()
            awaitRoute(R.id.navigation_me, R.id.user_profile)
        }
    }

    @Test
    fun linkFromRecentsDoesNotRepeat() {
        signedIn()
        ActivityScenario.launch<MainActivity>(link("/u/100001").addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)).use {
            awaitRoute(R.id.navigation_home, overlay = null)
            TestUi.settle(500)
            awaitRoute(R.id.navigation_home, overlay = null)
        }
    }

    private fun signedIn() {
        TestSession.seedActiveSession()
        TestSession.completeOnboarding()
    }

    private fun route(action: String) = Intent(context, MainActivity::class.java)
        .setAction(action)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    /** What a browser or a messenger sends for a verified link, aimed at this app's activity. */
    private fun link(path: String) = Intent(Intent.ACTION_VIEW, Uri.parse("https://dev.widgets.alllexey.dev$path"))
        .setClass(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    private fun awaitRoute(rootDestination: Int, overlay: Int?) = eventually {
        onActivity { activity ->
            assertEquals(rootDestination, root(activity).navController.currentDestination?.id)
            val host = overlay(activity)
            if (overlay == null) {
                assertNull(host)
            } else {
                assertNotNull(host)
                assertEquals(overlay, host!!.navController.currentDestination?.id)
            }
        }
    }

    private fun back() = onActivity { it.onBackPressedDispatcher.onBackPressed() }

    // ActivityScenario matches the original Intent and loses track when onNewIntent replaces it.
    private fun onActivity(block: (MainActivity) -> Unit) {
        var failure: Throwable? = null
        instrumentation.runOnMainSync {
            try {
                val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().single()
                block(activity)
            } catch (error: Throwable) {
                failure = error
            }
        }
        failure?.let { throw it }
    }

    private fun root(activity: MainActivity) =
        activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment

    private fun overlay(activity: MainActivity) =
        (activity.supportFragmentManager.findFragmentById(R.id.overlay_container) as? AppOverlayHostFragment)
            ?.takeUnless { it.isRemoving }

    private fun eventually(assertion: () -> Unit) =
        TestUi.eventually(attempts = 80, delayMillis = 100, message = "The route did not settle", assertion = assertion)
}
