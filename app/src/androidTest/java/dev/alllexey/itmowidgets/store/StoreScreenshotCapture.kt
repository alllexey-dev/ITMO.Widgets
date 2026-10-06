package dev.alllexey.itmowidgets.store

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.withDecorView
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.viewpager2.widget.ViewPager2
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.MainActivity
import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.demo.DemoStudy
import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.navigation.toBundle
import dev.alllexey.itmowidgets.core.ui.navigation.AppRoot
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.feature.home.HomeSemantics
import dev.alllexey.itmowidgets.feature.home.ui.HomeTestTags
import dev.alllexey.itmowidgets.feature.recordbook.data.demo.DemoRecordbook
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestSession
import dev.alllexey.itmowidgets.testing.TestUi
import java.time.LocalDate
import java.time.ZoneId
import org.hamcrest.Matchers.`is`
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Not a test: with `captureScreenshots=true` it enters the demo through the real MainActivity (five taps on the
 * logo) and saves the Google Play frames `01-home` … `10-me` (and `11-qr` for the landing) to `externalCacheDir/store-screenshots`. The theme
 * follows the device (`cmd uimode night yes|no`); the device size, density and status bar are set by the caller.
 * Every frame is checked for content, error and empty states and the word «Тест» before it is saved; the demo strip
 * is hidden for the shot.
 */
@RunWith(AndroidJUnit4::class)
class StoreScreenshotCapture {

    @Before
    fun startSignedOut() {
        TestSession.signOut()
    }

    @After
    fun leaveTheDemo() {
        TestSession.signOut()
    }

    @Test
    fun captureStoreScreenshots() {
        if (!Screenshots.enabled) return
        val today = LocalDate.now(ZoneId.of("Europe/Moscow"))
        val period = DemoRecordbook.programs(today).single().periods.single { it.actual }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity { activity = it }
            enterDemo(activity)

            frame(activity, "01-home")
            // Scrolled to the end, the feed's FAB clearance keeps the last cards clear of the quick actions.
            open(scenario) { main -> HomeSemantics.scrollBy(HomeSemantics.feedRoot(main), HomeTestTags.FEED, FEED_END_PX) }
            frame(activity, "01-home-end")

            open(scenario) { it.openRoot(AppRoot.SCHEDULE) }
            frame(activity, "02-schedule")
            scenario.onActivity { main ->
                main.window.decorView.descendants().filter { it.id == R.id.card_container && it.isShown }
                    .first { card -> card.texts().any { DemoStudy.ALGORITHMS.name in it } }
                    .performClick()
            }
            settle()
            frame(activity, "03-lesson", sheet = true)
            pressBack()
            settle()

            open(scenario) { it.openRoot(AppRoot.RECORDBOOK) }
            frame(activity, "04-recordbook")
            open(scenario) {
                val algorithms = DemoStudy.ALGORITHMS
                val args = RecordbookSubjectArgs(algorithms.id * 10 + period.semester, DemoStudy.PROGRAM_ID, period.semester, period.studyYear)
                it.openScreen(AppScreen.RECORDBOOK_SUBJECT, args.toBundle())
            }
            frame(activity, "05-subject")

            open(scenario) { it.openRoot(AppRoot.SPORT) }
            open(scenario) { main -> main.findViewById<ViewPager2>(R.id.sport_view_pager).currentItem = SPORT_SIGN_PAGE }
            frame(activity, "06-sport")
            open(scenario) { main -> main.findViewById<ViewPager2>(R.id.sport_view_pager).currentItem = SPORT_MY_PAGE }
            frame(activity, "07-sport-mine")

            open(scenario) {
                it.openScreen(AppScreen.USER_PROFILE, Bundle().apply { putInt(UserScreenArgs.ISU, DemoPeople.MATH_TEACHER.isu) })
            }
            // The reviews block with the summary, under the profile header.
            open(scenario) { main ->
                main.findViewById<RecyclerView>(R.id.profile_list).scrollBy(0, (REVIEWS_SCROLL_DP * main.resources.displayMetrics.density).toInt())
            }
            frame(activity, "08-teacher")

            open(scenario) { it.openRoot(AppRoot.ME) }
            open(scenario) { it.openScreen(AppScreen.FRIENDS) }
            frame(activity, "09-friends")

            open(scenario) { it.openRoot(AppRoot.ME) }
            open(scenario) { main ->
                // Release builds hide the developer tools row; the store shows the release screen.
                main.findViewById<View>(R.id.debug_tools_row).visibility = View.GONE
                main.findViewById<View>(R.id.debug_divider).visibility = View.GONE
            }
            frame(activity, "10-me")

            // Not one of the ten Play frames: the landing's «QR-пропуск» section.
            open(scenario) { it.openScreen(AppScreen.QR_PASS) }
            frame(activity, "11-qr", sheet = true)
        }
    }

    private fun enterDemo(activity: MainActivity) {
        val decorView = activity.window.decorView
        eventually { onView(withId(R.id.auth_logo)).inRoot(withDecorView(`is`(decorView))).check(matches(isDisplayed())) }
        repeat(DEMO_TAPS) { onView(withId(R.id.auth_logo)).perform(click()) }
        eventually { onView(withId(R.id.demo_banner)).inRoot(withDecorView(`is`(decorView))).check(matches(isDisplayed())) }
        // The «Демо-режим» toast must be gone before the first frame.
        TestUi.settle(TOAST_MILLIS)
    }

    private fun open(scenario: ActivityScenario<MainActivity>, action: (MainActivity) -> Unit) {
        scenario.onActivity(action)
        settle()
    }

    /** The screen has content, no state placeholder, no error and no test wording; then the device screenshot. */
    private fun frame(activity: MainActivity, name: String, sheet: Boolean = false) {
        eventually {
            TestUi.instrumentation.runOnMainSync {
                val views = activity.window.decorView.descendants().filter { it.isShown }.toList()
                assertTrue("$name shows a state instead of content", views.none { it.id == R.id.state_container })
                val texts = views.filterIsInstance<TextView>().map { it.text.toString() }.filter { it.isNotBlank() }
                assertTrue("$name has too little content", sheet || texts.size >= MIN_TEXTS)
                val failures = texts.filter { text -> forbiddenTexts.any { it in text } }
                assertTrue("$name shows $failures", failures.isEmpty())
            }
        }
        // The store frames show the app as a signed-in session sees it: without the demo strip above the bottom bar.
        TestUi.instrumentation.runOnMainSync { activity.findViewById<View>(R.id.demo_banner).visibility = View.GONE }
        Screenshots.capture(DIRECTORY, name) { settle() }
    }

    private val forbiddenTexts: List<String> by lazy {
        val context = ApplicationProvider.getApplicationContext<Context>()
        listOf(
            R.string.common_error_network, R.string.common_error_unknown, R.string.common_error_unauthorized,
            R.string.common_error_forbidden, R.string.common_error_not_found, R.string.common_error_services_disabled,
            R.string.error_demo_unavailable
        ).map(context::getString) + TEST_WORDING
    }

    private fun View.texts(): List<String> = descendants().filterIsInstance<TextView>().map { it.text.toString() }.toList()

    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) for (index in 0 until childCount) yieldAll(getChildAt(index).descendants())
    }

    private fun settle() = TestUi.settle(SETTLE_MILLIS)

    private fun eventually(assertion: () -> Unit) =
        TestUi.eventually(attempts = RETRY_COUNT, delayMillis = RETRY_DELAY_MILLIS, assertion = assertion)

    private companion object {
        const val DIRECTORY = "store-screenshots"
        const val DEMO_TAPS = 5
        const val SPORT_MY_PAGE = 0
        const val SPORT_SIGN_PAGE = 1
        const val RETRY_COUNT = 50
        const val RETRY_DELAY_MILLIS = 100L
        const val SETTLE_MILLIS = 1_000L
        const val TOAST_MILLIS = 4_000L
        const val MIN_TEXTS = 6
        const val REVIEWS_SCROLL_DP = 420
        const val FEED_END_PX = 10_000f
        const val TEST_WORDING = "Тест"
    }
}
