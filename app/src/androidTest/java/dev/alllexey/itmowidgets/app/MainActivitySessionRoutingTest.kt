package dev.alllexey.itmowidgets.app

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.shell.ShellModeRule
import dev.alllexey.itmowidgets.app.shell.ShellTags
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.ShellSurface
import dev.alllexey.itmowidgets.feature.auth.AuthSemantics
import dev.alllexey.itmowidgets.feature.auth.ui.AuthTestTags
import dev.alllexey.itmowidgets.feature.onboarding.ui.OnboardingFragment
import dev.alllexey.itmowidgets.testing.ShellProbe
import dev.alllexey.itmowidgets.testing.TestSession
import dev.alllexey.itmowidgets.testing.TestUi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Session and first-run routing of the real `MainActivity`. Navigation is read through `ShellProbe`, so each body runs
 * in every shell [ShellModeRule] knows; the bar is tapped by its legacy menu ids or its Compose test tags.
 */
@RunWith(AndroidJUnit4::class)
class MainActivitySessionRoutingTest {

    @get:Rule
    val shells = ShellModeRule()

    @After
    fun clearSessionAndFirstRunFlag() {
        TestSession.signOut()
        TestSession.resetOnboarding()
    }

    @Test
    fun activeSessionOpensAuthenticatedGraphWithoutShowingAuthDestination() {
        TestSession.seedActiveSession()
        TestSession.completeOnboarding()

        ActivityScenario.launch(MainActivity::class.java).use {
            eventually { assertEquals(TABS, ShellProbe.current().surface) }
            assertEquals(AppTab.HOME, ShellProbe.current().tab)
        }
    }

    @Test
    fun signInSaysTheAppIsUnofficial() {
        TestSession.signOut()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity { activity = it }

            eventually {
                val notice = AuthSemantics.node(activity, AuthTestTags.UNOFFICIAL_NOTICE)
                assertEquals(
                    activity.getString(R.string.app_unofficial_notice),
                    notice?.config?.getOrNull(SemanticsProperties.Text)?.joinToString { it.text },
                )
            }
            val content = AuthSemantics.bounds(activity, AuthTestTags.CONTENT)
            val notice = AuthSemantics.bounds(activity, AuthTestTags.UNOFFICIAL_NOTICE)
            val signIn = AuthSemantics.bounds(activity, AuthTestTags.ITMO_ID_LOGIN)
            assertTrue("The notice sits under the sign-in buttons", notice.top > signIn.bottom)
            assertTrue("The notice stays inside the screen", content.contains(notice))
        }
    }

    @Test
    fun firstRunOpensTheFlowInsteadOfTheBottomTabs() {
        TestSession.seedActiveSession()
        TestSession.resetOnboarding()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            eventually { scenario.onActivity(::assertOnboardingShown) }

            // The flow owns the window until it is passed: no tab, no bar.
            assertFlowOwnsTheWindow()
        }
    }

    @Test
    fun aReplayTakesTheWindowBackFromTheTabs() {
        TestSession.seedActiveSession()
        TestSession.completeOnboarding()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            eventually { assertEquals(TABS, ShellProbe.current().surface) }

            // What `Повторить первоначальную настройку` does: only the flag changes.
            TestSession.resetOnboarding()

            eventually { scenario.onActivity(::assertOnboardingShown) }
            assertFlowOwnsTheWindow()
        }
    }

    @Test
    fun tabsDoNotStackUpAfterTheFirstRunFlow() {
        TestSession.seedActiveSession()
        TestSession.resetOnboarding()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            eventually { assertEquals(ShellSurface.Onboarding, ShellProbe.current().surface) }
            TestSession.completeOnboarding()
            eventually { assertTabRoot(AppTab.HOME) }

            for (tab in listOf(AppTab.SCHEDULE, AppTab.SPORT, AppTab.HOME, AppTab.SCHEDULE)) {
                selectTab(tab)
                eventually { assertTabRoot(tab) }
            }
            // The Compose shell turns its Back handler on with the frame after the tap, as a finger never outruns.
            TestUi.settle(SETTLE_MILLIS)
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            eventually { assertTabRoot(AppTab.HOME) }
        }
    }

    /** The tabs show [tab]'s root with nothing above it. */
    private fun assertTabRoot(tab: AppTab) {
        val shown = ShellProbe.current()
        assertEquals(TABS, shown.surface)
        assertEquals(tab, shown.tab)
        assertEquals(emptyList<AppRoute>(), shown.overlays)
        assertNull(shown.floating)
    }

    private fun assertFlowOwnsTheWindow() {
        val shown = ShellProbe.current()
        assertEquals(ShellSurface.Onboarding, shown.surface)
        assertNull(shown.tab)
    }

    /** A tap on [tab] in the bar, repeated until the tab shows: right after the first-run flow a tap can get lost. */
    private fun selectTab(tab: AppTab) = eventually {
        if (ShellProbe.current().tab != tab) {
            when (shells.mode) {
                ShellModeRule.Mode.LEGACY -> onView(withId(TAB_ITEMS.getValue(tab))).perform(click())
                ShellModeRule.Mode.NAV3 -> tapComposeTab(tab)
            }
        }
        assertEquals(tab, ShellProbe.current().tab)
    }

    /** The Compose bar's item of [tab], tapped through its semantics click (no compose test rule here). */
    private fun tapComposeTab(tab: AppTab) = TestUi.instrumentation.runOnMainSync {
        val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
            .filterIsInstance<MainActivity>().single()
        val item = activity.window.decorView.descendants()
            .filterIsInstance<ViewRootForTest>()
            .flatMap { it.semanticsOwner.rootSemanticsNode.subtree() }
            .first { it.config.getOrNull(SemanticsProperties.TestTag) == ShellTags.tab(tab) }
        checkNotNull(item.config[SemanticsActions.OnClick].action).invoke()
    }

    /** The Compose flow tagged `onboarding_root` is on screen, read through semantics (no compose test rule here). */
    private fun assertOnboardingShown(activity: MainActivity) {
        val shown = activity.window.decorView.descendants()
            .filter { it.isShown }
            .filterIsInstance<ViewRootForTest>()
            .flatMap { it.semanticsOwner.unmergedRootSemanticsNode.subtree() }
            .any { node ->
                node.config.getOrNull(SemanticsProperties.TestTag) == OnboardingFragment.ROOT_TEST_TAG &&
                    node.size.height > 0
            }
        assertTrue("The first-run flow is not shown", shown)
    }

    private fun View.descendants(): Sequence<View> {
        val group = this as? ViewGroup ?: return sequenceOf(this)
        return sequenceOf(this) + (0 until group.childCount).asSequence().flatMap { group.getChildAt(it).descendants() }
    }

    private fun SemanticsNode.subtree(): Sequence<SemanticsNode> =
        sequenceOf(this) + children.asSequence().flatMap { it.subtree() }

    private fun eventually(assertion: () -> Unit) =
        TestUi.eventually(attempts = RETRY_COUNT, delayMillis = RETRY_DELAY_MILLIS, assertion = assertion)

    private companion object {
        const val RETRY_COUNT = 20
        const val RETRY_DELAY_MILLIS = 100L
        const val SETTLE_MILLIS = 300L
        val TABS = ShellSurface.Tabs(demoBanner = false)

        /** The legacy bar's menu items. */
        val TAB_ITEMS = mapOf(
            AppTab.RECORDBOOK to R.id.navigation_recordbook,
            AppTab.SCHEDULE to R.id.navigation_schedule,
            AppTab.HOME to R.id.navigation_home,
            AppTab.SPORT to R.id.navigation_sport,
            AppTab.ME to R.id.navigation_me,
        )
    }
}
