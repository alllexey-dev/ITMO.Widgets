package dev.alllexey.itmowidgets.app.shell

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.BackEventCompat
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dev.alllexey.itmowidgets.app.MainActivity
import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.ShellProbe
import dev.alllexey.itmowidgets.testing.TestGraph
import dev.alllexey.itmowidgets.testing.TestSession
import dev.alllexey.itmowidgets.testing.TestUi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Back and the predictive Back on the real `MainActivity` in the demo session, all through `NavDisplay`: the system
 * Back key through two pushed screens and off another tab's root, Back on a sheet, a tab switch that keeps each tab's
 * scroll, a pop that keeps every tab root's scroll, the predictive Back held half way, where the previous pushed
 * screen (not the tab root) is already composed and still under the shrunk top one, and a predictive Back released at
 * the end of the gesture, which still dissolves the top screen. With gesture navigation the half-way one is also a
 * real edge swipe from the system, and its frame is saved with `captureScreenshots=true`
 * (`shell/predictive-back-halfway.png`).
 */
@RunWith(AndroidJUnit4::class)
class ShellNavigationTest {

    @get:Rule
    val shells = ShellModeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation get() = TestUi.instrumentation
    private val settings = AppRoutes.Settings()
    private val privacy = AppRoutes.Settings("PRIVACY")

    @Before
    fun startTheDemo() {
        TestSession.signOut()
        runBlocking { TestGraph.get<SessionRepository>().startDemo() }
    }

    @After
    fun leaveTheDemo() {
        TestSession.signOut()
    }

    @Test
    fun theBackKeyUncoversEachPushedScreenThenTheRootWithItsBar() = onComposeShell {
        navigate {
            select(AppTab.ME)
            open(settings)
            open(privacy)
        }
        awaitShown(AppTab.ME, settings, privacy)
        assertTrue("the covered bar is hidden", !nodeShown(ShellTags.BAR))

        pressBackKey()
        awaitShown(AppTab.ME, settings)
        eventually { assertTrue(nodeShown(ShellTags.overlay(settings.toString()))) }

        pressBackKey()
        awaitShown(AppTab.ME)
        eventually { assertTrue("the bar shows again", nodeShown(ShellTags.BAR)) }
    }

    @Test
    fun theBackKeyOnAnotherRootGoesToTheStartTabAndOnTheStartRootLeaves() = onComposeShell {
        navigate { select(AppTab.SPORT) }
        awaitShown(AppTab.SPORT)

        pressBackKey()
        awaitShown(AppTab.HOME)

        pressBackKey()
        eventually { assertTrue("Back on the start root leaves the app", resumedShell() == null) }
    }

    @Test
    fun backOnASheetClosesItAndKeepsTheTab() = onComposeShell {
        navigate {
            select(AppTab.SCHEDULE)
            open(AppRoutes.LessonDetails(lesson))
        }
        eventually { assertEquals(AppRoutes.LessonDetails(lesson), ShellProbe.current().floating) }
        TestUi.settle(SETTLE_MILLIS)

        pressBackKey()

        awaitShown(AppTab.SCHEDULE)
        eventually { assertTrue(nodeShown(ShellTags.BAR)) }
    }

    @Test
    fun aTabSwitchKeepsTheScrollOfEachTab() = onComposeShell {
        navigate { select(AppTab.ME) }
        awaitShown(AppTab.ME)
        TestUi.settle(SETTLE_MILLIS)
        onActivity { activity -> scrollTabContent(activity, SCROLL_PX) }
        TestUi.settle(SETTLE_MILLIS)
        val scrolled = checkNotNull(tabScroll()) { "the profile root has no vertical scroller" }
        assertTrue("the profile root scrolled: $scrolled", scrolled > 0f)

        clickTab(AppTab.RECORDBOOK)
        awaitShown(AppTab.RECORDBOOK)
        TestUi.settle(SETTLE_MILLIS)
        clickTab(AppTab.ME)
        awaitShown(AppTab.ME)
        TestUi.settle(SETTLE_MILLIS)

        assertEquals("the profile root keeps its scroll", scrolled, checkNotNull(tabScroll()), 1f)
    }

    @Test
    fun aPopKeepsTheScrollOfEveryTabRoot() = onComposeShell {
        eventually { assertEquals(AppTab.HOME, ShellProbe.current().tab) }
        val checked = mutableListOf<AppTab>()
        for (tab in AppTab.entries) {
            clickTab(tab)
            awaitShown(tab)
            TestUi.settle(SETTLE_MILLIS)
            // A root that fits the window has nothing to keep: the demo's Сегодня or Зачётка at some hours and sizes.
            if (checkNotNull(tabScrollMax()) { "the $tab root has no vertical scroller" } == 0f) continue
            onActivity { activity -> scrollTabContent(activity, SCROLL_PX) }
            TestUi.settle(SETTLE_MILLIS)
            val scrolled = checkNotNull(tabScroll())
            assertTrue("the $tab root scrolled: $scrolled", scrolled > 0f)
            checked += tab

            // NavDisplay disposes the root under the pushed screen; staying past the view models' 5 s
            // WhileSubscribed lets their flows stop, as when the user reads the pushed screen.
            onActivity { it.navigator().open(settings) }
            awaitShown(tab, settings)
            SystemClock.sleep(PUSHED_MILLIS)
            pressBackKey()
            awaitShown(tab)
            TestUi.settle(SETTLE_MILLIS)

            assertEquals("the $tab root keeps its scroll after a pop", scrolled, checkNotNull(tabScroll()), 1f)
        }
        assertTrue("no tab root could scroll", checked.isNotEmpty())
    }

    @Test
    fun aPredictiveBackHeldHalfwayShowsThePreviousScreenUnderTheTopOne() = onComposeShell {
        navigate {
            select(AppTab.ME)
            open(settings)
            open(privacy)
        }
        awaitShown(AppTab.ME, settings, privacy)
        TestUi.settle(SETTLE_MILLIS)
        val window = checkNotNull(bounds(ShellTags.overlay(privacy.toString())))
        assertNull("the screen under the top one is not composed at rest", bounds(ShellTags.overlay(settings.toString())))

        onActivity { it.dragBack(HALF) }
        eventually { assertPeek(window) }
        onActivity { it.onBackPressedDispatcher.dispatchOnBackCancelled() }
        eventually { assertNull(bounds(ShellTags.overlay(settings.toString()))) }
        awaitShown(AppTab.ME, settings, privacy)

        onActivity { it.dragBack(HALF) }
        eventually { assertPeek(window) }
        onActivity { it.onBackPressedDispatcher.onBackPressed() }

        awaitShown(AppTab.ME, settings)
        eventually { assertEquals(window, bounds(ShellTags.overlay(settings.toString()))) }
    }

    /** Released at the very end (a long drag or a fling), the top screen still stays for its dissolve, then pops. */
    @Test
    fun aPredictiveBackReleasedAtTheEndStillDissolvesTheTopScreen() = onComposeShell {
        assumeTrue("needs animations", ValueAnimator.areAnimatorsEnabled())
        navigate {
            select(AppTab.ME)
            open(settings)
            open(privacy)
        }
        awaitShown(AppTab.ME, settings, privacy)
        TestUi.settle(SETTLE_MILLIS)

        onActivity { it.dragBack(1f) }
        eventually { assertTrue(checkNotNull(bounds(ShellTags.overlay(privacy.toString()))).width > 0f) }
        var releasedAt = 0L
        onActivity {
            it.onBackPressedDispatcher.onBackPressed()
            releasedAt = SystemClock.uptimeMillis()
        }
        eventually { assertNull(bounds(ShellTags.overlay(privacy.toString()))) }
        val shown = SystemClock.uptimeMillis() - releasedAt
        assertTrue("the top screen dissolves for a while: $shown ms", shown >= MIN_DISSOLVE_MILLIS)
        awaitShown(AppTab.ME, settings)
    }

    @Test
    fun theSystemBackSwipeHeldHalfwayPeeksAtThePreviousScreen() = onComposeShell {
        assumeTrue("needs gesture navigation", gestureNavigation())
        navigate {
            select(AppTab.ME)
            open(settings)
            open(privacy)
        }
        awaitShown(AppTab.ME, settings, privacy)
        TestUi.settle(SETTLE_MILLIS)
        val window = checkNotNull(bounds(ShellTags.overlay(privacy.toString())))

        val swipe = EdgeSwipe(window)
        swipe.down()
        swipe.moveTo(HALF)
        eventually { assertPeek(window) }
        Screenshots.capture(DIRECTORY, "predictive-back-halfway")
        swipe.up()

        awaitShown(AppTab.ME, settings)
    }

    /** The previous screen is composed and still at the window's size under the top one, which shrank; the tab root is not. */
    private fun assertPeek(window: Rect) {
        val under = checkNotNull(bounds(ShellTags.overlay(settings.toString()))) { "the previous screen is not composed" }
        val top = checkNotNull(bounds(ShellTags.overlay(privacy.toString())))
        assertTrue("the top screen shrinks: $top in $window", top.width < window.width - 1f)
        assertEquals("the previous screen stands still", window, under)
        assertNull("the tab root stays out of the peek", bounds(ShellTags.TAB_CONTENT))
    }

    /** A finger from the window's left edge, injected through UiAutomation so the system's back gesture sees it. */
    private inner class EdgeSwipe(private val window: Rect) {
        private val downTime = SystemClock.uptimeMillis()
        private val y = window.center.y
        private var x = EDGE_X

        fun down() = inject(MotionEvent.ACTION_DOWN)

        fun moveTo(fraction: Float) {
            val target = window.width * fraction
            repeat(MOVE_STEPS) { step ->
                x = EDGE_X + (target - EDGE_X) * (step + 1) / MOVE_STEPS
                inject(MotionEvent.ACTION_MOVE)
                SystemClock.sleep(MOVE_STEP_MILLIS)
            }
        }

        fun up() = inject(MotionEvent.ACTION_UP)

        private fun inject(action: Int) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
                .apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            assertTrue("injected $action", instrumentation.uiAutomation.injectInputEvent(event, true))
            event.recycle()
        }
    }

    private fun gestureNavigation(): Boolean =
        Settings.Secure.getInt(context.contentResolver, NAVIGATION_MODE, 0) == GESTURE_NAVIGATION

    private fun pressBackKey() {
        TestUi.idle()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        TestUi.settle(SETTLE_MILLIS)
    }

    /** A tab of the bar, by its semantics click. */
    private fun clickTab(tab: AppTab) = onActivity { activity ->
        val item = composedNodes(activity.window.decorView).first { it.tag == ShellTags.tab(tab) }
        checkNotNull(item.config[SemanticsActions.OnClick].action).invoke()
    }

    /** Scrolls the first vertical scroller of the shown tab root by [pixels]. */
    private fun scrollTabContent(activity: MainActivity, pixels: Float) {
        val scroller = checkNotNull(tabScroller(activity)) { "the tab root has no vertical scroller" }
        checkNotNull(scroller.config[SemanticsActions.ScrollBy].action).invoke(0f, pixels)
    }

    /** The scroll value of the shown tab root's first vertical scroller. */
    private fun tabScroll(): Float? {
        var value: Float? = null
        onActivity { activity ->
            value = tabScroller(activity)?.config?.get(SemanticsProperties.VerticalScrollAxisRange)?.value?.invoke()
        }
        return value
    }

    private fun tabScrollMax(): Float? {
        var value: Float? = null
        onActivity { activity ->
            value = tabScroller(activity)?.config?.get(SemanticsProperties.VerticalScrollAxisRange)?.maxValue?.invoke()
        }
        return value
    }

    private fun tabScroller(activity: MainActivity): SemanticsNode? {
        val content = composedNodes(activity.window.decorView).firstOrNull { it.tag == ShellTags.TAB_CONTENT }
        return content?.subtree()?.firstOrNull { node ->
            SemanticsProperties.VerticalScrollAxisRange in node.config && SemanticsActions.ScrollBy in node.config
        }
    }

    /** Whether a placed node tagged [tag] shows in the merged tree (TalkBack's view). */
    private fun nodeShown(tag: String): Boolean {
        var shown = false
        onActivity { activity ->
            shown = composedNodes(activity.window.decorView, merged = true)
                .any { it.tag == tag && it.layoutInfo.isPlaced }
        }
        return shown
    }

    private fun bounds(tag: String): Rect? {
        var bounds: Rect? = null
        onActivity { activity ->
            bounds = composedNodes(activity.window.decorView)
                .firstOrNull { it.tag == tag && it.layoutInfo.isPlaced }?.boundsInWindow?.takeIf { it.width > 0f }
        }
        return bounds
    }

    private fun awaitShown(tab: AppTab, vararg overlays: AppRoute) = eventually {
        val shown = ShellProbe.current()
        assertEquals(tab, shown.tab)
        assertEquals(overlays.toList(), shown.overlays)
        assertNull(shown.floating)
    }

    private fun navigate(step: Nav3AppNavigator.() -> Unit) {
        eventually { assertEquals(AppTab.HOME, ShellProbe.current().tab) }
        onActivity { it.navigator().step() }
        TestUi.settle(SETTLE_MILLIS)
    }

    private fun onComposeShell(body: () -> Unit) {
        instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        try {
            body()
        } finally {
            runCatching { onActivity { it.finish() } }
        }
    }

    /** A predictive Back from the start edge, held at [progress]. */
    private fun MainActivity.dragBack(progress: Float) {
        onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
        onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(0f, 0f, progress, BackEventCompat.EDGE_LEFT))
    }

    /** The Compose shell's navigator; `ShellHost` keeps it private, so the test reads the field. */
    private fun MainActivity.navigator(): Nav3AppNavigator {
        val host = checkNotNull(ShellHost.of(this)) { "MainActivity has no shell yet" }
        val field = ShellHost::class.java.getDeclaredField("navigator").apply { isAccessible = true }
        return checkNotNull(field.get(host) as Nav3AppNavigator?) { "the Compose shell has not composed yet" }
    }

    private fun resumedShell(): MainActivity? {
        var activity: MainActivity? = null
        instrumentation.runOnMainSync {
            activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().singleOrNull()
        }
        return activity
    }

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

    private fun eventually(assertion: () -> Unit) =
        TestUi.eventually(attempts = 80, delayMillis = 100, message = "The shell did not settle", assertion = assertion)

    private fun composedNodes(view: View, merged: Boolean = false): List<SemanticsNode> =
        composeRoots(view).flatMap { root ->
            val owner = root.semanticsOwner
            (if (merged) owner.rootSemanticsNode else owner.unmergedRootSemanticsNode).subtree()
        }

    private fun SemanticsNode.subtree(): List<SemanticsNode> = listOf(this) + children.flatMap { it.subtree() }

    private fun composeRoots(view: View): List<ViewRootForTest> = when (view) {
        is ViewRootForTest -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { composeRoots(view.getChildAt(it)) }
        else -> emptyList()
    }

    private val SemanticsNode.tag: String? get() = config.getOrNull(SemanticsProperties.TestTag)

    private val lesson = LessonDetailsArgs(
        pairId = 1,
        date = "2026-10-05",
        subjectName = "Линейная алгебра",
        typeId = 1,
        format = "Очно",
        start = "10:00",
        end = "11:30",
        teacherFio = DemoPeople.MATH_TEACHER.name,
        teacherIsu = DemoPeople.MATH_TEACHER.isu.toLong(),
        room = "1404",
        building = "Кронверкский пр., д.49, лит.А",
        buildingId = null,
        mainBuildingId = null,
        note = null,
        zoomUrl = null,
        zoomPassword = null,
        zoomInfo = null,
    )

    private companion object {
        const val SETTLE_MILLIS = 700L
        const val MIN_DISSOLVE_MILLIS = 200L
        const val HALF = 0.5f
        const val SCROLL_PX = 600f
        const val PUSHED_MILLIS = 5_500L
        const val EDGE_X = 2f
        const val MOVE_STEPS = 12
        const val MOVE_STEP_MILLIS = 16L
        const val DIRECTORY = "shell"

        /** `Settings.Secure.NAVIGATION_MODE`, hidden in the SDK; 2 is gesture navigation. */
        const val NAVIGATION_MODE = "navigation_mode"
        const val GESTURE_NAVIGATION = 2
    }
}
