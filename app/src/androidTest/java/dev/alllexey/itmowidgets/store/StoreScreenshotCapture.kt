package dev.alllexey.itmowidgets.store

import android.content.Context
import android.view.View
import android.view.ViewGroup
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
import dev.alllexey.itmowidgets.app.MainActivity
import dev.alllexey.itmowidgets.app.shell.Nav3AppNavigator
import dev.alllexey.itmowidgets.app.shell.ShellHost
import dev.alllexey.itmowidgets.app.shell.StoreLook
import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.demo.DemoStudy
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.navigation.ShellSurface
import dev.alllexey.itmowidgets.feature.auth.AuthSemantics
import dev.alllexey.itmowidgets.feature.auth.ui.AuthTestTags
import dev.alllexey.itmowidgets.feature.home.HomeSemantics
import dev.alllexey.itmowidgets.feature.home.ui.HomeTestTags
import dev.alllexey.itmowidgets.feature.me.ui.MeFragment
import dev.alllexey.itmowidgets.feature.recordbook.data.demo.DemoRecordbook
import dev.alllexey.itmowidgets.feature.schedule.ui.list.ScheduleListTestTags
import dev.alllexey.itmowidgets.feature.social.ui.profile.UserProfileTestTags
import dev.alllexey.itmowidgets.feature.sport.ui.SportPage
import dev.alllexey.itmowidgets.feature.sport.ui.SportScreenTestTags
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.ShellProbe
import dev.alllexey.itmowidgets.testing.TestSession
import dev.alllexey.itmowidgets.testing.TestUi
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.datetime.toKotlinLocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Google Play frames `01-home` ... `10-me` (and `11-qr` for the landing) of the Compose shell. It enters the demo
 * through the real MainActivity (five taps on the logo), walks every frame and checks each for content, no error or
 * empty state and no word «Тест»; with `captureScreenshots=true` it also saves them to
 * `externalCacheDir/store-screenshots`, so without the argument it is a smoke test of the demo's main screens. The
 * landing images under `site/img/light` and `site/img/dark` are these frames at 540x960. The theme follows the device
 * (`cmd uimode night yes|no`); the device size, density and status bar are set by the caller. The frames show the
 * release look: no demo banner ([StoreLook]) and no developer tools row on Me.
 */
@RunWith(AndroidJUnit4::class)
class StoreScreenshotCapture {

    @Before
    fun startSignedOut() {
        TestSession.signOut()
        MeFragment.releaseLook = true
        StoreLook.hideDemoBanner = true
    }

    @After
    fun leaveTheDemo() {
        StoreLook.hideDemoBanner = false
        MeFragment.releaseLook = false
        TestSession.signOut()
    }

    @Test
    fun captureStoreScreenshots() {
        val today = LocalDate.now(ZoneId.of("Europe/Moscow"))
        val period = DemoRecordbook.programs(today.toKotlinLocalDate()).single().periods.single { it.actual }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity { activity = it }
            enterDemo(activity)

            frame(activity, "01-home")
            // Scrolled to the end, the feed's FAB clearance keeps the last cards clear of the quick actions.
            scenario.onActivity { main -> HomeSemantics.scrollBy(HomeSemantics.feedRoot(main), HomeTestTags.FEED, FEED_END_PX) }
            settle()
            frame(activity, "01-home-end")

            open(scenario, AppTab.SCHEDULE) { it.select(AppTab.SCHEDULE) }
            frame(activity, "02-schedule")
            scenario.onActivity(::openAlgorithmsLesson)
            settle()
            eventually { assertNotNull("the lesson sheet is not shown", ShellProbe.current().floating) }
            frame(activity, "03-lesson", sheet = true)
            pressBack()
            settle()
            awaitShown(AppTab.SCHEDULE)

            open(scenario, AppTab.RECORDBOOK) { it.select(AppTab.RECORDBOOK) }
            frame(activity, "04-recordbook")
            val algorithms = DemoStudy.ALGORITHMS
            val subject = AppRoutes.RecordbookSubject(
                RecordbookSubjectArgs(algorithms.id * 10 + period.semester, DemoStudy.PROGRAM_ID, period.semester, period.studyYear),
            )
            open(scenario, AppTab.RECORDBOOK, subject) { it.open(subject) }
            frame(activity, "05-subject")

            open(scenario, AppTab.SPORT) { it.select(AppTab.SPORT) }
            showSportPage(scenario, SportPage.SIGN)
            frame(activity, "06-sport")
            showSportPage(scenario, SportPage.MY)
            frame(activity, "07-sport-mine")

            val teacher = AppRoutes.UserProfile(DemoPeople.MATH_TEACHER.isu)
            open(scenario, AppTab.SPORT, teacher) { it.open(teacher) }
            // The reviews block with the summary, under the profile header.
            scenario.onActivity { main ->
                val pixels = REVIEWS_SCROLL_DP * main.resources.displayMetrics.density
                HomeSemantics.scrollBy(main.composeRoot(), UserProfileTestTags.LIST, pixels)
            }
            settle()
            frame(activity, "08-teacher")

            open(scenario, AppTab.ME) { it.select(AppTab.ME) }
            open(scenario, AppTab.ME, AppRoutes.Friends) { it.open(AppRoutes.Friends) }
            frame(activity, "09-friends")

            open(scenario, AppTab.ME) { it.select(AppTab.ME) }
            frame(activity, "10-me")

            // Not one of the ten Play frames: the landing's «QR-пропуск» section.
            open(scenario, AppTab.ME, AppRoutes.QrPass) { it.open(AppRoutes.QrPass) }
            frame(activity, "11-qr", minTexts = QR_MIN_TEXTS)
        }
    }

    private fun enterDemo(activity: MainActivity) {
        assertNotNull("MainActivity runs the Compose shell", onMain { ShellHost.of(activity) })
        eventually { assertTrue("The sign-in screen shows", AuthSemantics.isShown(activity)) }
        repeat(DEMO_TAPS) { AuthSemantics.tap(activity, AuthTestTags.LOGO) }
        awaitShown(AppTab.HOME)
        // The «Демо-режим» toast must be gone before the first frame.
        TestUi.settle(TOAST_MILLIS)
    }

    /** Runs one step on the Compose shell's navigator and waits until it shows [tab] with exactly [overlays]. */
    private fun open(
        scenario: ActivityScenario<MainActivity>,
        tab: AppTab,
        vararg overlays: AppRoute,
        step: (Nav3AppNavigator) -> Unit,
    ) {
        scenario.onActivity { step(it.navigator()) }
        settle()
        awaitShown(tab, *overlays)
    }

    /** The sport pager's segment [page], by its tab's semantics click. */
    private fun showSportPage(scenario: ActivityScenario<MainActivity>, page: SportPage) {
        scenario.onActivity { main ->
            val tag = SportScreenTestTags.tab(page)
            val segment = main.composedNodes().first { it.tag == tag }
            checkNotNull(segment.config[SemanticsActions.OnClick].action) { "$tag has no click" }.invoke()
        }
        settle()
        awaitShown(AppTab.SPORT)
    }

    /** The schedule's first placed Algorithms row opens its lesson sheet by its semantics click. */
    private fun openAlgorithmsLesson(activity: MainActivity) {
        val row = activity.composedNodes().first { node ->
            node.tag.orEmpty().startsWith(ScheduleListTestTags.LESSON_PREFIX) &&
                node.texts().any { DemoStudy.ALGORITHMS.name in it }
        }
        checkNotNull(row.config[SemanticsActions.OnClick].action) { "the lesson row has no click" }.invoke()
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
     * The frame has content, no `ContentState` (an empty or error state) and no error or test wording; then the
     * device screenshot. A [sheet] frame is read in the sheet's own dialog window.
     */
    private fun frame(activity: MainActivity, name: String, sheet: Boolean = false, minTexts: Int = MIN_TEXTS) {
        eventually {
            val nodes = if (sheet) sheetNodes() else onMain { activity.composedNodes() }
            val states = nodes.filter { it.tag == CONTENT_STATE_TAG }
            assertTrue("$name shows a state instead of content", states.isEmpty())
            val texts = nodes.flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
                .filter { it.isNotBlank() }
            assertTrue("$name has too little content: $texts", texts.size >= minTexts)
            val failures = texts.filter { text -> forbiddenTexts.any { it in text } }
            assertTrue("$name shows $failures", failures.isEmpty())
        }
        Screenshots.capture(DIRECTORY, name) { settle() }
    }

    /** The placed semantics nodes of the top dialog window, the sheet scene's. */
    private fun sheetNodes(): List<SemanticsNode> {
        var nodes = emptyList<SemanticsNode>()
        onView(isRoot()).inRoot(isDialog()).check(ViewAssertion { root, _ -> nodes = root.composedNodes() })
        return nodes
    }

    private val forbiddenTexts: List<String> by lazy {
        val context = ApplicationProvider.getApplicationContext<Context>()
        listOf(
            R.string.common_error_network, R.string.common_error_unknown, R.string.common_error_unauthorized,
            R.string.common_error_forbidden, R.string.common_error_not_found, R.string.common_error_services_disabled,
            R.string.error_demo_unavailable,
        ).map(context::getString) + TEST_WORDING
    }

    /**
     * The Compose shell's navigator. `ShellHost` keeps it private (entries get it as callbacks), so the capture reads
     * the field, as `DemoModeFlowTest` does.
     */
    private fun MainActivity.navigator(): Nav3AppNavigator {
        val host = checkNotNull(ShellHost.of(this)) { "MainActivity runs the legacy shell" }
        val field = ShellHost::class.java.getDeclaredField("navigator").apply { isAccessible = true }
        return checkNotNull(field.get(host) as Nav3AppNavigator?) { "the Compose shell has not composed yet" }
    }

    /** The shell's one Compose root, for the semantics helpers that take a `ComposeView`. */
    private fun MainActivity.composeRoot(): View =
        window.decorView.descendants().first { view -> view is ViewGroup && view.childCount > 0 && view.getChildAt(0) is ViewRootForTest }

    /** The activity window's placed semantics nodes; main thread. */
    private fun MainActivity.composedNodes(): List<SemanticsNode> = window.decorView.composedNodes()

    /**
     * The placed, on-screen semantics nodes under this view, the unmerged tree, so every text counts once. The tab
     * pager's neighbouring pages do not count, nor does a tab layer an overlay hides from accessibility.
     */
    private fun View.composedNodes(): List<SemanticsNode> {
        val width = rootView.width.toFloat()
        val height = rootView.height.toFloat()
        return descendants().filter { it.isShown }.filterIsInstance<ViewRootForTest>()
            .flatMap { it.semanticsOwner.unmergedRootSemanticsNode.descendants() }
            .filter { node ->
                val bounds = node.boundsInWindow
                node.layoutInfo.isPlaced && bounds.right > 0f && bounds.left < width && bounds.bottom > 0f && bounds.top < height
            }
            .toList()
    }

    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) for (index in 0 until childCount) yieldAll(getChildAt(index).descendants())
    }

    private fun SemanticsNode.descendants(): Sequence<SemanticsNode> = sequence {
        yield(this@descendants)
        children.forEach { yieldAll(it.descendants()) }
    }

    /** The texts of a node and everything under it. */
    private fun SemanticsNode.texts(): List<String> = descendants()
        .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }.toList()

    private val SemanticsNode.tag: String? get() = config.getOrNull(SemanticsProperties.TestTag)

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        TestUi.instrumentation.runOnMainSync { result = runCatching(block) }
        return checkNotNull(result).getOrThrow()
    }

    private fun settle() = TestUi.settle(SETTLE_MILLIS)

    private fun eventually(assertion: () -> Unit) =
        TestUi.eventually(attempts = RETRY_COUNT, delayMillis = RETRY_DELAY_MILLIS, assertion = assertion)

    private companion object {
        const val DIRECTORY = "store-screenshots"
        const val DEMO_TAPS = 5
        const val RETRY_COUNT = 50
        const val RETRY_DELAY_MILLIS = 100L
        const val SETTLE_MILLIS = 1_000L
        const val TOAST_MILLIS = 4_000L
        const val MIN_TEXTS = 6
        const val QR_MIN_TEXTS = 1
        const val REVIEWS_SCROLL_DP = 420
        const val FEED_END_PX = 10_000f
        const val TEST_WORDING = "Тест"

        /** The test tag of DS-03a's `ContentState`, as `DemoModeFlowTest` reads it. */
        const val CONTENT_STATE_TAG = "ContentState"
    }
}
