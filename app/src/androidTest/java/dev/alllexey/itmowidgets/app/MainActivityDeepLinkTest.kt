package dev.alllexey.itmowidgets.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.navigation.fragment.NavHostFragment
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.shell.ShellHost
import dev.alllexey.itmowidgets.app.shell.ShellModeRule
import dev.alllexey.itmowidgets.core.navigation.AppEntryIntents
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.ShellSurface
import dev.alllexey.itmowidgets.feature.sport.ui.SportPage
import dev.alllexey.itmowidgets.feature.sport.ui.SportScreenTestTags
import dev.alllexey.itmowidgets.testing.ShellProbe
import dev.alllexey.itmowidgets.testing.TestSession
import dev.alllexey.itmowidgets.testing.TestUi
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Widget, notification, tile, shortcut and App Link intents on the real `MainActivity`. Navigation is read through
 * `ShellProbe`, so each body runs in every shell [ShellModeRule] knows.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityDeepLinkTest {
    @get:Rule
    val shells = ShellModeRule()

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
        ActivityScenario.launch<MainActivity>(route(AppEntryIntents.ACTION_OPEN_QR_PASS)).use {
            awaitRoute(AppTab.HOME, AppRoutes.QrPass)
            back()
            awaitRoute(AppTab.HOME, overlay = null)
            onActivity(::assertNothingUnderTheTab)
        }
    }

    @Test
    fun todayShortcutOpensTheScheduleAndBackReturnsHome() {
        signedIn()
        ActivityScenario.launch<MainActivity>(route(AppEntryIntents.ACTION_OPEN_TODAY)).use {
            awaitRoute(AppTab.SCHEDULE, overlay = null)
            back()
            awaitRoute(AppTab.HOME, overlay = null)
        }
    }

    @Test
    fun routeWaitsForSignInAndOnboarding() {
        TestSession.signOut()
        TestSession.resetOnboarding()
        ActivityScenario.launch<MainActivity>(route(AppEntryIntents.ACTION_OPEN_QR_PASS)).use {
            awaitSurface(ShellSurface.Auth)
            TestSession.seedActiveSession()
            awaitSurface(ShellSurface.Onboarding)
            TestSession.completeOnboarding()
            awaitRoute(AppTab.HOME, AppRoutes.QrPass)
        }
    }

    @Test
    fun routeRunsOnceAcrossRecreation() {
        signedIn()
        ActivityScenario.launch<MainActivity>(route(AppEntryIntents.ACTION_OPEN_QR_PASS)).use { scenario ->
            awaitRoute(AppTab.HOME, AppRoutes.QrPass)
            scenario.recreate()
            // One pass, not a second one stacked on the restored one.
            awaitRoute(AppTab.HOME, AppRoutes.QrPass)
            back()
            awaitRoute(AppTab.HOME, overlay = null)
            scenario.recreate()
            awaitRoute(AppTab.HOME, overlay = null)
        }
    }

    @Test
    fun routeArrivingWhileOpenReplacesTheOverlay() {
        signedIn()
        // ActivityScenario cannot close an activity that went through onNewIntent; this one finishes itself.
        // Not startActivitySync: it waits for an idle main thread, and the Sport tab animates its loading for as long
        // as the real my.itmo.ru takes to answer the seeded session (SH-FIX-DL). awaitRoute polls instead.
        context.startActivity(route(AppEntryIntents.ACTION_OPEN_SPORT))
        try {
            awaitRoute(AppTab.SPORT, overlay = null)
            // What a tile, a widget or a browser sends to a running task: the same instance gets onNewIntent.
            context.startActivity(link("/u/100001").setFlags(RUNNING_TASK))
            awaitRoute(AppTab.ME, AppRoutes.UserProfile(100001))
            context.startActivity(route(AppEntryIntents.ACTION_OPEN_QR_PASS).setFlags(RUNNING_TASK))
            awaitRoute(AppTab.HOME, AppRoutes.QrPass)
            back()
            awaitRoute(AppTab.HOME, overlay = null)
        } finally {
            runCatching { onActivity { it.finish() } }
        }
    }

    @Test
    fun launchFromRecentsDoesNotRepeatTheRoute() {
        signedIn()
        val intent = route(AppEntryIntents.ACTION_OPEN_QR_PASS).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        ActivityScenario.launch<MainActivity>(intent).use {
            awaitRoute(AppTab.HOME, overlay = null)
            // The route never runs later either.
            TestUi.settle(500)
            awaitRoute(AppTab.HOME, overlay = null)
        }
    }

    @Test
    fun notificationAndWidgetRoutesReturnHomeOnBack() {
        signedIn()
        listOf(
            AppEntryIntents.ACTION_OPEN_SCHEDULE to AppTab.SCHEDULE,
            AppEntryIntents.ACTION_OPEN_SPORT to AppTab.SPORT,
            AppEntryIntents.ACTION_OPEN_RECORDBOOK to AppTab.RECORDBOOK,
        ).forEach { (action, tab) ->
            ActivityScenario.launch<MainActivity>(route(action)).use {
                awaitRoute(tab, overlay = null)
                back()
                awaitRoute(AppTab.HOME, overlay = null)
                onActivity(::assertNothingUnderTheTab)
            }
        }
    }

    @Test
    fun profileLinkOpensTheProfileAboveTheProfileTab() {
        signedIn()
        ActivityScenario.launch<MainActivity>(link("/u/100001")).use {
            awaitRoute(AppTab.ME, AppRoutes.UserProfile(100001))
            back()
            awaitRoute(AppTab.ME, overlay = null)
            back()
            awaitRoute(AppTab.HOME, overlay = null)
        }
    }

    @Test
    fun sportLinkOpensTheSignPage() {
        signedIn()
        listOf("/sport/1", "/sport/p/1").forEach { path ->
            ActivityScenario.launch<MainActivity>(link(path)).use {
                awaitRoute(AppTab.SPORT, overlay = null)
                eventually {
                    onActivity { activity ->
                        assertTrue(path, selected(activity, SportScreenTestTags.tab(SportPage.SIGN)))
                    }
                }
            }
        }
    }

    @Test
    fun malformedLinkShowsTheUnavailableDialogAtHome() {
        signedIn()
        ActivityScenario.launch<MainActivity>(link("/sport/abc")).use {
            awaitRoute(AppTab.HOME, overlay = null)
            when (shells.mode) {
                // The legacy alert is a MaterialAlertDialogBuilder dialog, which the probe does not see.
                ShellModeRule.Mode.LEGACY -> {
                    onView(withText(R.string.app_link_unavailable_title)).inRoot(isDialog()).check(matches(isDisplayed()))
                    onView(withText(R.string.common_got_it)).inRoot(isDialog()).perform(click())
                }
                ShellModeRule.Mode.NAV3 -> {
                    eventually { assertEquals(AppRoutes.LinkUnavailable, ShellProbe.current().floating) }
                    onView(isRoot()).inRoot(isDialog()).perform(clickComposeText(context.getString(R.string.common_got_it)))
                    eventually { assertNull(ShellProbe.current().floating) }
                }
            }
            awaitRoute(AppTab.HOME, overlay = null)
        }
    }

    @Test
    fun linkWaitsForSignIn() {
        TestSession.signOut()
        TestSession.resetOnboarding()
        ActivityScenario.launch<MainActivity>(link("/u/100001")).use {
            awaitSurface(ShellSurface.Auth)
            TestSession.seedActiveSession()
            awaitSurface(ShellSurface.Onboarding)
            TestSession.completeOnboarding()
            awaitRoute(AppTab.ME, AppRoutes.UserProfile(100001))
        }
    }

    @Test
    fun linkFromRecentsDoesNotRepeat() {
        signedIn()
        ActivityScenario.launch<MainActivity>(link("/u/100001").addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)).use {
            awaitRoute(AppTab.HOME, overlay = null)
            TestUi.settle(500)
            awaitRoute(AppTab.HOME, overlay = null)
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

    /** The tabs show [tab] with exactly [overlay] above it (nothing for null). */
    private fun awaitRoute(tab: AppTab, overlay: AppRoute?) = eventually {
        val shown = ShellProbe.current()
        assertEquals(TABS, shown.surface)
        assertEquals(tab, shown.tab)
        assertEquals(listOfNotNull(overlay), shown.overlays)
    }

    private fun awaitSurface(surface: ShellSurface) = eventually { assertEquals(surface, ShellProbe.current().surface) }

    /**
     * The tab's own history is empty, so Back from here leaves the app. The Navigation 3 tab stack is its root alone
     * (`ShellBackStack.tabStack`); the legacy one is checked on its NavController.
     */
    private fun assertNothingUnderTheTab(activity: MainActivity) {
        if (ShellHost.of(activity) != null) return
        val root = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        assertNull(root.navController.previousBackStackEntry)
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

    /** Whether the Compose node tagged [tag] in the activity's window is selected; both shells host the same route. */
    private fun selected(activity: MainActivity, tag: String): Boolean = semantics(activity.window.decorView)
        .filter { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
        .any { it.config.getOrNull(SemanticsProperties.Selected) == true }

    /** Clicks the Compose button reading [text] in the window of the root it runs on (a dialog's). */
    private fun clickComposeText(text: String) = object : ViewAction {
        override fun getConstraints(): Matcher<View> = isRoot()

        override fun getDescription() = "click the Compose node reading \"$text\""

        override fun perform(uiController: UiController, view: View) {
            val node = semantics(view, merged = true).first { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text } &&
                    node.config.getOrNull(SemanticsActions.OnClick) != null
            }
            checkNotNull(node.config[SemanticsActions.OnClick].action).invoke()
            uiController.loopMainThreadUntilIdle()
        }
    }

    private fun semantics(view: View, merged: Boolean = false): Sequence<SemanticsNode> =
        view.descendants().filterIsInstance<ViewRootForTest>().flatMap { root ->
            val owner = root.semanticsOwner
            (if (merged) owner.rootSemanticsNode else owner.unmergedRootSemanticsNode).subtree()
        }

    private fun View.descendants(): Sequence<View> {
        val group = this as? ViewGroup ?: return sequenceOf(this)
        return sequenceOf(this) + (0 until group.childCount).asSequence().flatMap { group.getChildAt(it).descendants() }
    }

    private fun SemanticsNode.subtree(): Sequence<SemanticsNode> =
        sequenceOf(this) + children.asSequence().flatMap { it.subtree() }

    private fun eventually(assertion: () -> Unit) =
        TestUi.eventually(attempts = 80, delayMillis = 100, message = "The route did not settle", assertion = assertion)

    private companion object {
        val TABS = ShellSurface.Tabs(demoBanner = false)
        const val RUNNING_TASK =
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
}
