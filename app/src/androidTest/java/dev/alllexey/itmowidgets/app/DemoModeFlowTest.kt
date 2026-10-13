package dev.alllexey.itmowidgets.app

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.feature.schedule.ui.feed.ScheduleFeedTestTags
import dev.alllexey.itmowidgets.app.shell.Nav3AppNavigator
import dev.alllexey.itmowidgets.app.shell.ShellHost
import dev.alllexey.itmowidgets.app.shell.ShellModeRule
import dev.alllexey.itmowidgets.app.shell.ShellTags
import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.demo.DemoStudy
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.navigation.ShellSurface
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.storage.DemoPreferences
import dev.alllexey.itmowidgets.feature.auth.AuthSemantics
import dev.alllexey.itmowidgets.feature.auth.ui.AuthTestTags
import dev.alllexey.itmowidgets.feature.recordbook.data.demo.DemoRecordbook
import dev.alllexey.itmowidgets.feature.resources.ui.SubjectLinksSheetTestTags
import dev.alllexey.itmowidgets.feature.sport.ui.SportPage
import dev.alllexey.itmowidgets.feature.sport.ui.SportScreenTestTags
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.ShellProbe
import dev.alllexey.itmowidgets.testing.TestGraph
import dev.alllexey.itmowidgets.testing.TestSession
import dev.alllexey.itmowidgets.testing.TestUi
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinLocalDate
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
 * demo. Navigation is read through `ShellProbe` and driven through the shell's navigator.
 */
@RunWith(AndroidJUnit4::class)
class DemoModeFlowTest {

    @get:Rule
    val shells = ShellModeRule()

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
            assertEquals(true, (TestGraph.get<SessionRepository>().state.value as? SessionState.SignedIn)?.demo)
            assertTrue(runBlocking { TestGraph.get<DemoPreferences>().getDemoActive() })

            scenario.recreate()
            scenario.onActivity { activity = it }
            awaitShown(AppTab.HOME)

            signInFromTheDemoBanner(activity)

            eventually { assertEquals(ShellSurface.Auth, ShellProbe.current().surface) }
            eventually { assertTrue("The sign-in screen shows", AuthSemantics.isShown(activity)) }
            // The sign-in screen already shows while `SigningOut` (docs/features/auth.md), and the Compose shell
            // shows it before the sign-out finishes.
            eventually { assertEquals(SessionState.SignedOut, TestGraph.get<SessionRepository>().state.value) }
            assertFalse(runBlocking { TestGraph.get<DemoPreferences>().getDemoActive() })
        }
    }

    @Test
    fun everyMainScreenOfTheDemoHasContentAndWritesAreRefused() {
        runBlocking { TestGraph.get<SessionRepository>().startDemo() }
        val today = LocalDate.now(ZoneId.of("Europe/Moscow"))
        val period = DemoRecordbook.programs(today.toKotlinLocalDate()).single().periods.single { it.actual }
        val algorithms = DemoStudy.ALGORITHMS
        val scope = ResourceScope.periodKey(period.studyYear, period.semesterInCourse)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity { activity = it }
            awaitShown(AppTab.HOME)

            shot(activity, "home")

            open(scenario) { it.select(AppTab.SCHEDULE) }
            awaitShown(AppTab.SCHEDULE)
            shot(activity, "schedule")
            scenario.onActivity(::openFirstLesson)
            settle()
            eventually { assertNotNull("the lesson sheet is not shown", ShellProbe.current().floating) }
            Screenshots.capture(DIRECTORY, "lesson") { settle() }
            pressBack()
            settle()
            awaitShown(AppTab.SCHEDULE)

            open(scenario) { it.select(AppTab.RECORDBOOK) }
            awaitShown(AppTab.RECORDBOOK)
            shot(activity, "recordbook")
            val subject = RecordbookSubjectArgs(
                algorithms.id * 10 + period.semester, DemoStudy.PROGRAM_ID, period.semester, period.studyYear
            )
            open(scenario) { it.open(AppRoutes.RecordbookSubject(subject)) }
            eventually {
                val overlays = ShellProbe.current().overlays
                assertEquals(listOf(AppRoutes.RecordbookSubject::class), overlays.map { it::class })
            }
            shot(activity, "subject")
            scenario.onActivity(::voteOnFirstLink)
            eventually { assertComposedText(activity, R.string.error_demo_unavailable) }
            val links = SubjectLinksArgs(algorithms.id, algorithms.name, scope)
            open(scenario) { it.open(AppRoutes.SubjectLinks(links)) }
            eventually { assertEquals("SubjectLinks", ShellProbe.current().floatingName) }
            Screenshots.capture(DIRECTORY, "links") { settle() }
            assertLinksSheetShows("Баллы потока")
            pressBack()
            settle()
            eventually { assertNull(ShellProbe.current().floating) }

            open(scenario) { it.select(AppTab.SPORT) }
            awaitShown(AppTab.SPORT)
            shot(activity, "sport-my")
            open(scenario) { clickComposedTag(activity, SportScreenTestTags.tab(SportPage.SIGN)) }
            awaitShown(AppTab.SPORT)
            shot(activity, "sport-sign")

            open(scenario) { it.select(AppTab.ME) }
            awaitShown(AppTab.ME)
            shot(activity, "me")
            open(scenario) { it.open(AppRoutes.Friends) }
            awaitShown(AppTab.ME, AppRoutes.Friends)
            shot(activity, "friends")
            openProfile(scenario, DemoPeople.MATH_TEACHER.isu)
            shot(activity, "teacher")
            openProfile(scenario, DemoPeople.IVAN.isu)
            shot(activity, "friend")
        }
    }

    /** Runs one navigation step through the shell's navigator and waits for the screen to settle. */
    private fun open(scenario: ActivityScenario<MainActivity>, step: (Nav3AppNavigator) -> Unit) {
        scenario.onActivity { activity -> step(activity.navigator()) }
        settle()
    }

    private fun openProfile(scenario: ActivityScenario<MainActivity>, isu: Int) {
        open(scenario) { it.open(AppRoutes.UserProfile(isu)) }
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

    /** The shell's navigator. `ShellHost` keeps it private (entries get it as callbacks), so the test reads the field. */
    private fun MainActivity.navigator(): Nav3AppNavigator {
        val host = checkNotNull(ShellHost.of(this)) { "MainActivity has no shell yet" }
        val field = ShellHost::class.java.getDeclaredField("navigator").apply { isAccessible = true }
        return checkNotNull(field.get(host) as Nav3AppNavigator?) { "the Compose shell has not composed yet" }
    }

    /** The demo banner's sign-in, by its semantics click. */
    private fun signInFromTheDemoBanner(activity: MainActivity) = TestUi.instrumentation.runOnMainSync {
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

    /** The placed Compose node tagged [tag] in [activity]'s window, clicked through its semantics; main thread. */
    private fun clickComposedTag(activity: MainActivity, tag: String) {
        val node = composedNodes(activity).first { node ->
            node.layoutInfo.isPlaced && node.config.getOrNull(SemanticsProperties.TestTag) == tag
        }
        checkNotNull(node.config[SemanticsActions.OnClick].action) { "$tag has no click" }.invoke()
    }

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

    /** The first placed lesson row opens its sheet by its semantics click. */
    private fun openFirstLesson(activity: MainActivity) {
        val row = activity.window.decorView.descendants().filter { it.isShown }.filterIsInstance<ViewRootForTest>()
            .flatMap { it.semanticsOwner.unmergedRootSemanticsNode.descendants() }
            .first { node ->
                node.layoutInfo.isPlaced &&
                    node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(ScheduleFeedTestTags.LESSON_PREFIX) == true
            }
        checkNotNull(row.config[SemanticsActions.OnClick].action) { "the lesson row has no click" }.invoke()
    }

    /** The first link's up arrow votes by its semantics click. */
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
     * The links sheet's Compose list, in the sheet's own dialog window (the shell's sheet scene), shows a row titled
     * [title] on screen.
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

        /** The test tag of the design system's `ContentState`. */
        const val CONTENT_STATE_TAG = "ContentState"
    }
}
