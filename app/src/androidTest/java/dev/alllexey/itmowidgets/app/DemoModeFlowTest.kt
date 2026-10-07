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
import androidx.fragment.app.DialogFragment
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.viewpager2.widget.ViewPager2
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
import dev.alllexey.itmowidgets.feature.resources.ui.SubjectLinksBottomSheet
import dev.alllexey.itmowidgets.feature.resources.ui.SubjectLinksSheetTestTags
import dev.alllexey.itmowidgets.feature.schedule.ui.list.ScheduleListTestTags
import dev.alllexey.itmowidgets.testing.Screenshots
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.datetime.toKotlinLocalDate
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.withDecorView
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.EntryPointAccessors
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.notification.NotificationDebugEntryPoint
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.testing.TestSession
import dev.alllexey.itmowidgets.testing.TestUi
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.`is`
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The hidden demo entry through the real MainActivity, from the sign-in screen and back. */
@RunWith(AndroidJUnit4::class)
class DemoModeFlowTest {

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
            lateinit var decorView: View
            scenario.onActivity {
                activity = it
                decorView = it.window.decorView
            }
            eventually { assertTrue("The sign-in screen shows", AuthSemantics.isShown(activity)) }

            repeat(5) { AuthSemantics.tap(activity, AuthTestTags.LOGO) }

            eventually {
                onView(withId(R.id.bottom_nav_view)).inRoot(withDecorView(`is`(decorView))).check(matches(isDisplayed()))
                onView(withId(R.id.demo_banner)).inRoot(withDecorView(`is`(decorView))).check(matches(isDisplayed()))
            }
            assertEquals(true, (dependencies.session().state.value as? SessionState.SignedIn)?.demo)
            assertTrue(runBlocking { dependencies.demoPreferences().getDemoActive() })

            scenario.recreate()
            scenario.onActivity {
                activity = it
                decorView = it.window.decorView
            }
            eventually {
                onView(withId(R.id.demo_banner)).inRoot(withDecorView(`is`(decorView))).check(matches(isDisplayed()))
                onView(withId(R.id.navigation_schedule)).inRoot(withDecorView(`is`(decorView))).check(matches(isDisplayed()))
            }

            onView(withId(R.id.demo_banner_sign_in)).perform(click())

            eventually { assertTrue("The sign-in screen shows", AuthSemantics.isShown(activity)) }
            assertEquals(SessionState.SignedOut, dependencies.session().state.value)
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
            eventually { onView(withId(R.id.demo_banner)).check(matches(isDisplayed())) }

            shot(activity, "home")

            open(scenario) { it.openRoot(AppRoot.SCHEDULE) }
            shot(activity, "schedule")
            scenario.onActivity(::openFirstLesson)
            settle()
            Screenshots.capture(DIRECTORY, "lesson") { settle() }
            pressBack()
            settle()

            open(scenario) { it.openRoot(AppRoot.RECORDBOOK) }
            shot(activity, "recordbook")
            open(scenario) {
                val args = RecordbookSubjectArgs(algorithms.id * 10 + period.semester, DemoStudy.PROGRAM_ID, period.semester, period.studyYear)
                it.openScreen(AppScreen.RECORDBOOK_SUBJECT, args.toBundle())
            }
            shot(activity, "subject")
            scenario.onActivity { main ->
                main.window.decorView.descendants().first { it.id == R.id.vote_up && it.isShown }.performClick()
            }
            eventually { onView(withText(R.string.error_demo_unavailable)).check(matches(isDisplayed())) }
            open(scenario) { it.openSubjectLinks(SubjectLinksArgs(algorithms.id, algorithms.name, scope)) }
            Screenshots.capture(DIRECTORY, "links") { settle() }
            assertLinksSheetShows(activity, "Баллы потока")
            pressBack()
            settle()

            open(scenario) { it.openRoot(AppRoot.SPORT) }
            shot(activity, "sport-my")
            open(scenario) { main -> main.findViewById<ViewPager2>(R.id.sport_view_pager).currentItem = 1 }
            shot(activity, "sport-sign")

            open(scenario) { it.openRoot(AppRoot.ME) }
            shot(activity, "me")
            open(scenario) { it.openScreen(AppScreen.FRIENDS) }
            shot(activity, "friends")
            open(scenario) {
                it.openScreen(AppScreen.USER_PROFILE, Bundle().apply { putInt(UserScreenArgs.ISU, DemoPeople.MATH_TEACHER.isu) })
            }
            shot(activity, "teacher")
            open(scenario) {
                it.openScreen(AppScreen.USER_PROFILE, Bundle().apply { putInt(UserScreenArgs.ISU, DemoPeople.IVAN.isu) })
            }
            shot(activity, "friend")
        }
    }

    /** Runs [action] on the activity and waits for the screen to settle. */
    private fun open(scenario: ActivityScenario<MainActivity>, action: (MainActivity) -> Unit) {
        scenario.onActivity(action)
        settle()
    }

    /** The screen has content, no empty or error state, and the demo banner; then its screenshot. */
    private fun shot(activity: MainActivity, name: String) {
        eventually {
            TestUi.instrumentation.runOnMainSync {
                val views = activity.window.decorView.descendants().filter { it.isShown }.toList()
                val composed = views.filterIsInstance<ViewRootForTest>()
                    .flatMap { it.semanticsOwner.unmergedRootSemanticsNode.descendants() }
                val states = views.filter { it.id == R.id.state_container } +
                    composed.filter { it.config.getOrNull(SemanticsProperties.TestTag) == CONTENT_STATE_TAG }
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

    /** The links sheet's Compose list, in the sheet's own dialog window, shows a row titled [title] on screen. */
    private fun assertLinksSheetShows(activity: MainActivity, title: String) {
        eventually {
            TestUi.instrumentation.runOnMainSync {
                val sheet = activity.supportFragmentManager.findFragmentByTag(SubjectLinksBottomSheet.TAG) as DialogFragment
                val window = checkNotNull(sheet.dialog?.takeIf { it.isShowing }?.window) { "the links sheet is not shown" }
                val list = window.decorView.descendants().filter { it.isShown }.filterIsInstance<ViewRootForTest>()
                    .flatMap { it.semanticsOwner.unmergedRootSemanticsNode.descendants() }
                    .single { it.config.getOrNull(SemanticsProperties.TestTag) == SubjectLinksSheetTestTags.LIST }
                val row = list.descendants().firstOrNull { node ->
                    node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == title }
                }
                assertNotNull("the links sheet does not list $title", row)
                val bounds = row!!.boundsInWindow
                assertTrue("$title is not displayed: $bounds", row.layoutInfo.isPlaced && bounds.width > 0f && bounds.height > 0f)
            }
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
         * The test tag of DS-03a's `ContentState`, the Compose counterpart of `R.id.state_container` (the tag itself
         * is L08's hand-in to `ContentState`).
         */
        const val CONTENT_STATE_TAG = "ContentState"
    }
}
