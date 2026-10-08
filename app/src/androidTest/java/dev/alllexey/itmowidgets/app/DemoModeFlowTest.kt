package dev.alllexey.itmowidgets.app

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.navigation.fragment.NavHostFragment
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import dev.alllexey.itmowidgets.app.shell.Nav3AppNavigator
import dev.alllexey.itmowidgets.app.shell.ShellHost
import dev.alllexey.itmowidgets.app.shell.ShellModeRule
import dev.alllexey.itmowidgets.app.shell.ShellTags
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.ShellSurface
import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.demo.DemoStudy
import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.navigation.toBundle
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.ui.navigation.AppRoot
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.feature.auth.AuthSemantics
import dev.alllexey.itmowidgets.feature.auth.ui.AuthTestTags
import dev.alllexey.itmowidgets.feature.recordbook.data.demo.DemoRecordbook
import dev.alllexey.itmowidgets.feature.resources.ui.SubjectLinksSheetTestTags
import dev.alllexey.itmowidgets.feature.schedule.ui.list.ScheduleListTestTags
import dev.alllexey.itmowidgets.feature.sport.ui.SportPage
import dev.alllexey.itmowidgets.feature.sport.ui.SportScreenTestTags
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportFragment
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.ShellProbe
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.datetime.toKotlinLocalDate
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.EntryPointAccessors
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.notification.NotificationDebugEntryPoint
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.testing.TestSession
import dev.alllexey.itmowidgets.testing.TestUi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The hidden demo entry through the real MainActivity, from the sign-in screen and back, and every main screen of the
 * demo. Navigation is read through `ShellProbe`, so each body runs in every shell [ShellModeRule] knows; it is driven
 * through the legacy `AppNavigator` calls or the Compose shell's navigator.
 */
@RunWith(AndroidJUnit4::class)
class DemoModeFlowTest {

    @get:Rule
    val shells = ShellModeRule()

    private val dependencies
        get() = EntryPointAccessors.fromApplication(
            ApplicationProvider.getApplicationContext<Context>(),
            NotificationDebugEntryPoint::class.java
        )

    @Before
    fun startSignedOut() {
        TestSession.signOut()
    }

    @After
    fun leaveTheDemo() {
        TestSession.signOut()
    }

    @Test
    fun fiveLogoTapsOpenTheDemoThatSurvivesRecreationAndEndsWithSignIn() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity { activity = it }
            eventually { assertEquals(ShellSurface.Auth, ShellProbe.current().surface) }
            eventually { assertTrue("The sign-in screen shows", AuthSemantics.isShown(activity)) }

            repeat(5) { AuthSemantics.tap(activity, AuthTestTags.LOGO) }

            awaitShown(AppTab.HOME)
            assertEquals(true, (dependencies.session().state.value as? SessionState.SignedIn)?.demo)
            assertTrue(runBlocking { dependencies.demoPreferences().getDemoActive() })

            scenario.recreate()
            scenario.onActivity { activity = it }
            awaitShown(AppTab.HOME)

            signInFromTheDemoBanner(activity)

            eventually { assertEquals(ShellSurface.Auth, ShellProbe.current().surface) }
            eventually { assertTrue("The sign-in screen shows", AuthSemantics.isShown(activity)) }
            // The sign-in screen already shows while `SigningOut` (docs/features/auth.md), and the Compose shell
            // shows it before the sign-out finishes.
            eventually { assertEquals(SessionState.SignedOut, dependencies.session().state.value) }
            assertFalse(runBlocking { dependencies.demoPreferences().getDemoActive() })
        }
    }

    @Test
    fun everyMainScreenOfTheDemoHasContentAndWritesAreRefused() {
        runBlocking { dependencies.session().startDemo() }
        val today = LocalDate.now(ZoneId.of("Europe/Moscow"))
        val period = DemoRecordbook.programs(today.toKotlinLocalDate()).single().periods.single { it.actual }
        val algorithms = DemoStudy.ALGORITHMS
        val scope = ResourceScope.periodKey(period.studyYear, period.semesterInCourse)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity { activity = it }
            awaitShown(AppTab.HOME)

            shot(activity, "home")

            open(scenario, legacy = { it.openRoot(AppRoot.SCHEDULE) }, nav3 = { it.select(AppTab.SCHEDULE) })
            awaitShown(AppTab.SCHEDULE)
            shot(activity, "schedule")
            scenario.onActivity(::openFirstLesson)
            settle()
            eventually { assertNotNull("the lesson sheet is not shown", ShellProbe.current().floating) }
            Screenshots.capture(DIRECTORY, "lesson") { settle() }
            pressBack()
            settle()
            awaitShown(AppTab.SCHEDULE)

            open(scenario, legacy = { it.openRoot(AppRoot.RECORDBOOK) }, nav3 = { it.select(AppTab.RECORDBOOK) })
            awaitShown(AppTab.RECORDBOOK)
            shot(activity, "recordbook")
            val subject = RecordbookSubjectArgs(
                algorithms.id * 10 + period.semester, DemoStudy.PROGRAM_ID, period.semester, period.studyYear
            )
            open(
                scenario,
                legacy = { it.openScreen(AppScreen.RECORDBOOK_SUBJECT, subject.toBundle()) },
                nav3 = { it.open(AppRoutes.RecordbookSubject(subject)) },
            )
            eventually {
                val overlays = ShellProbe.current().overlays
                assertEquals(listOf(AppRoutes.RecordbookSubject::class), overlays.map { it::class })
            }
            shot(activity, "subject")
            scenario.onActivity(::voteOnFirstLink)
            eventually { assertComposedText(activity, R.string.error_demo_unavailable) }
            val links = SubjectLinksArgs(algorithms.id, algorithms.name, scope)
            open(scenario, legacy = { it.openSubjectLinks(links) }, nav3 = { it.open(AppRoutes.SubjectLinks(links)) })
            eventually { assertEquals("SubjectLinks", ShellProbe.current().floatingName) }
            Screenshots.capture(DIRECTORY, "links") { settle() }
            assertLinksSheetShows("Баллы потока")
            pressBack()
            settle()
            eventually { assertNull(ShellProbe.current().floating) }

            open(scenario, legacy = { it.openRoot(AppRoot.SPORT) }, nav3 = { it.select(AppTab.SPORT) })
            awaitShown(AppTab.SPORT)
            shot(activity, "sport-my")
            open(
                scenario,
                legacy = { main -> main.sport().changeView(SportPage.SIGN.ordinal, animate = false) },
                nav3 = { clickComposedTag(activity, SportScreenTestTags.tab(SportPage.SIGN)) },
            )
            awaitShown(AppTab.SPORT)
            shot(activity, "sport-sign")

            open(scenario, legacy = { it.openRoot(AppRoot.ME) }, nav3 = { it.select(AppTab.ME) })
            awaitShown(AppTab.ME)
            shot(activity, "me")
            open(scenario, legacy = { it.openScreen(AppScreen.FRIENDS) }, nav3 = { it.open(AppRoutes.Friends) })
            awaitShown(AppTab.ME, AppRoutes.Friends)
            shot(activity, "friends")
            openProfile(scenario, DemoPeople.MATH_TEACHER.isu)
            shot(activity, "teacher")
            openProfile(scenario, DemoPeople.IVAN.isu)
            shot(activity, "friend")
        }
    }

    /**
     * Runs one navigation step and waits for the screen to settle: [legacy] through the Fragment shell's
     * `AppNavigator` calls on the activity, [nav3] through the Compose shell's navigator.
     */
    private fun open(
        scenario: ActivityScenario<MainActivity>,
        legacy: (MainActivity) -> Unit,
        nav3: (Nav3AppNavigator) -> Unit,
    ) {
        scenario.onActivity { activity ->
            when (shells.mode) {
                ShellModeRule.Mode.LEGACY -> legacy(activity)
                ShellModeRule.Mode.NAV3 -> nav3(activity.navigator())
            }
        }
        settle()
    }

    private fun openProfile(scenario: ActivityScenario<MainActivity>, isu: Int) {
        open(
            scenario,
            legacy = { it.openScreen(AppScreen.USER_PROFILE, Bundle().apply { putInt(UserScreenArgs.ISU, isu) }) },
            nav3 = { it.open(AppRoutes.UserProfile(isu)) },
        )
        eventually {
            val shown = ShellProbe.current()
            assertEquals(AppTab.ME, shown.tab)
            assertEquals(AppRoutes.UserProfile(isu), shown.overlays.lastOrNull())
        }
    }

    /** The demo session's tabs show [tab] with exactly [overlays] above it and no sheet or dialog. */
    private fun awaitShown(tab: AppTab, vararg overlays: AppRoute) = eventually {
        val shown = ShellProbe.current()
        assertEquals(ShellSurface.Tabs(demoBanner = true), shown.surface)
        assertEquals(tab, shown.tab)
        assertEquals(overlays.toList(), shown.overlays)
        assertNull(shown.floating)
    }

    /**
     * The Compose shell's navigator, the counterpart of the legacy `AppNavigator` calls. `ShellHost` keeps it private
     * (entries get it as callbacks), so the test reads the field.
     */
    private fun MainActivity.navigator(): Nav3AppNavigator {
        val host = checkNotNull(ShellHost.of(this)) { "MainActivity runs the legacy shell" }
        val field = ShellHost::class.java.getDeclaredField("navigator").apply { isAccessible = true }
        return checkNotNull(field.get(host) as Nav3AppNavigator?) { "the Compose shell has not composed yet" }
    }

    /** The demo banner's sign-in: the legacy banner's button or the Compose banner's, by its semantics click. */
    private fun signInFromTheDemoBanner(activity: MainActivity) {
        when (shells.mode) {
            ShellModeRule.Mode.LEGACY -> onView(withId(R.id.demo_banner_sign_in)).perform(click())
            ShellModeRule.Mode.NAV3 -> TestUi.instrumentation.runOnMainSync {
                val label = activity.getString(R.string.demo_banner_sign_in)
                val banner = composedNodes(activity)
                    .single { it.config.getOrNull(SemanticsProperties.TestTag) == ShellTags.DEMO_BANNER }
                val button = banner.descendants().first { node ->
                    SemanticsActions.OnClick in node.config &&
                        node.descendants().any { child ->
                            child.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == label }
                        }
                }
                checkNotNull(button.config[SemanticsActions.OnClick].action).invoke()
            }
        }
    }

    /** The placed Compose node tagged [tag] in [activity]'s window, clicked through its semantics; main thread. */
    private fun clickComposedTag(activity: MainActivity, tag: String) {
        val node = composedNodes(activity).first { node ->
            node.layoutInfo.isPlaced && node.config.getOrNull(SemanticsProperties.TestTag) == tag
        }
        checkNotNull(node.config[SemanticsActions.OnClick].action) { "$tag has no click" }.invoke()
    }

    /** The sport tab's host, whose page API replaces the View pager. */
    private fun MainActivity.sport(): SportFragment =
        (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment)
            .childFragmentManager.fragments.filterIsInstance<SportFragment>().single()

    /** The screen has content, no empty or error state, and the demo banner; then its screenshot. */
    private fun shot(activity: MainActivity, name: String) {
        eventually {
            TestUi.instrumentation.runOnMainSync {
                val views = activity.window.decorView.descendants().filter { it.isShown }.toList()
                val composed = views.filterIsInstance<ViewRootForTest>()
                    .flatMap { it.semanticsOwner.unmergedRootSemanticsNode.descendants() }
                val states = composed.filter { it.config.getOrNull(SemanticsProperties.TestTag) == CONTENT_STATE_TAG }
                assertTrue("$name shows a state instead of content", states.isEmpty())
                val texts = views.filterIsInstance<TextView>().map { it.text.toString() } +
                    composed.flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
                val shownTexts = texts.filter { it.isNotBlank() }
                assertTrue("$name has too little content", shownTexts.size >= MIN_TEXTS)
                val failures = shownTexts.filter { text -> errorTexts.any { it in text } }
                assertTrue("$name shows an error: $failures", failures.isEmpty())
            }
        }
        Screenshots.capture(DIRECTORY, name) { settle() }
    }

    /** The schedule is Compose (LS-6b): the first placed lesson row opens its sheet by its semantics click. */
    private fun openFirstLesson(activity: MainActivity) {
        val row = activity.window.decorView.descendants().filter { it.isShown }.filterIsInstance<ViewRootForTest>()
            .flatMap { it.semanticsOwner.unmergedRootSemanticsNode.descendants() }
            .first { node ->
                node.layoutInfo.isPlaced &&
                    node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(ScheduleListTestTags.LESSON_PREFIX) == true
            }
        checkNotNull(row.config[SemanticsActions.OnClick].action) { "the lesson row has no click" }.invoke()
    }

    /** The subject page is Compose (LR-4b): the first link's up arrow votes by its semantics click. */
    private fun voteOnFirstLink(activity: MainActivity) {
        val label = activity.getString(R.string.links_vote_up)
        val arrow = composedNodes(activity).first { node ->
            node.layoutInfo.isPlaced && label in node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        }
        checkNotNull(arrow.config[SemanticsActions.OnClick].action) { "the vote arrow has no click" }.invoke()
    }

    /** A Compose screen or snackbar on [activity] shows the text of [text]. */
    private fun assertComposedText(activity: MainActivity, text: Int) = TestUi.instrumentation.runOnMainSync {
        val expected = activity.getString(text)
        assertTrue(
            "nothing shows $expected",
            composedNodes(activity).any { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == expected } },
        )
    }

    private fun composedNodes(activity: MainActivity): List<SemanticsNode> =
        activity.window.decorView.descendants().filter { it.isShown }.filterIsInstance<ViewRootForTest>()
            .flatMap { it.semanticsOwner.unmergedRootSemanticsNode.descendants() }.toList()

    /**
     * The links sheet's Compose list, in the sheet's own dialog window (the legacy sheet Fragment's or the Compose
     * shell's sheet scene's), shows a row titled [title] on screen.
     */
    private fun assertLinksSheetShows(title: String) {
        eventually {
            onView(isRoot()).inRoot(isDialog()).check(ViewAssertion { root, _ ->
                val list = root.descendants().filter { it.isShown }.filterIsInstance<ViewRootForTest>()
                    .flatMap { it.semanticsOwner.unmergedRootSemanticsNode.descendants() }
                    .single { it.config.getOrNull(SemanticsProperties.TestTag) == SubjectLinksSheetTestTags.LIST }
                val row = list.descendants().firstOrNull { node ->
                    node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == title }
                }
                assertNotNull("the links sheet does not list $title", row)
                val bounds = row!!.boundsInWindow
                assertTrue("$title is not displayed: $bounds", row.layoutInfo.isPlaced && bounds.width > 0f && bounds.height > 0f)
            })
        }
    }

    private val errorTexts: List<String> by lazy {
        val context = ApplicationProvider.getApplicationContext<Context>()
        listOf(
            R.string.common_error_network, R.string.common_error_unknown, R.string.common_error_unauthorized,
            R.string.common_error_forbidden, R.string.common_error_not_found, R.string.common_error_services_disabled,
            R.string.error_demo_unavailable
        ).map(context::getString)
    }

    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) for (index in 0 until childCount) yieldAll(getChildAt(index).descendants())
    }

    /** A Compose screen's semantics nodes, the unmerged tree, so every text counts once. */
    private fun SemanticsNode.descendants(): List<SemanticsNode> = listOf(this) + children.flatMap { it.descendants() }

    private fun settle() = TestUi.settle(SETTLE_MILLIS)

    private fun eventually(assertion: () -> Unit) =
        TestUi.eventually(attempts = RETRY_COUNT, delayMillis = RETRY_DELAY_MILLIS, assertion = assertion)

    private companion object {
        const val RETRY_COUNT = 50
        const val RETRY_DELAY_MILLIS = 100L
        const val SETTLE_MILLIS = 700L
        const val MIN_TEXTS = 6
        const val DIRECTORY = "demo-check"

        /**
         * The test tag of DS-03a's `ContentState`, the Compose counterpart of the View screens' former
         * `state_container` (the tag itself is L08's hand-in to `ContentState`).
         */
        const val CONTENT_STATE_TAG = "ContentState"
    }
}
