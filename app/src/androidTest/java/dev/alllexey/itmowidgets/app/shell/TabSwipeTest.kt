package dev.alllexey.itmowidgets.app.shell

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.CoordinatesProvider
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dagger.hilt.android.EntryPointAccessors
import dev.alllexey.itmowidgets.app.MainActivity
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.notification.NotificationDebugEntryPoint
import dev.alllexey.itmowidgets.feature.sport.ui.sign.SportWeekStripTestTags
import dev.alllexey.itmowidgets.testing.ShellProbe
import dev.alllexey.itmowidgets.testing.TestSession
import dev.alllexey.itmowidgets.testing.TestUi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The tab swipe on the real `MainActivity` in the demo session (design.md "Tab swipe"): through all five roots and
 * back, Back after swipes, an App Link while on another tab, and the sport segments handing the swipe over. Swipes
 * start inside the tab content, away from the system's back edges, at a height read from the screen's geometry, so no
 * status bar or cutout height moves them onto content that keeps drags from the tabs. Only the Compose shell has the
 * swipe: the legacy pass of [ShellModeRule] is skipped by an assumption, so these cases run from SH-1b9's switch on.
 */
@RunWith(AndroidJUnit4::class)
class TabSwipeTest {

    @get:Rule
    val shell = ShellModeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation get() = TestUi.instrumentation

    @Before
    fun startTheDemo() {
        TestSession.signOut()
        val dependencies = EntryPointAccessors.fromApplication(context, NotificationDebugEntryPoint::class.java)
        runBlocking { dependencies.session().startDemo() }
    }

    @After
    fun leaveTheDemo() {
        TestSession.signOut()
    }

    @Test
    fun swipesWalkThroughAllFiveRootsWithoutWrappingAround() = onComposeShell {
        awaitTab(AppTab.HOME)
        assertTrue(ShellProbe.current().swipeEnabled)

        // On sport the first swipe moves its own pager (`Мой спорт` -> `Запись`, and back), the next one the tab.
        listOf(AppTab.SPORT, AppTab.SPORT, AppTab.ME, AppTab.ME).forEach { tab ->
            swipe(towardsNext = true)
            awaitTab(tab)
            TestUi.settle(SETTLE_MILLIS)
        }
        listOf(AppTab.SPORT, AppTab.SPORT, AppTab.HOME, AppTab.SCHEDULE, AppTab.RECORDBOOK, AppTab.RECORDBOOK).forEach { tab ->
            swipe(towardsNext = false)
            awaitTab(tab)
            TestUi.settle(SETTLE_MILLIS)
        }
        swipe(towardsNext = true)
        swipe(towardsNext = true)
        awaitTab(AppTab.HOME)
    }

    @Test
    fun backFromSportAfterSwipesLeadsHome() = onComposeShell {
        awaitTab(AppTab.HOME)
        swipe(towardsNext = true)
        awaitTab(AppTab.SPORT)
        // The first swipe on sport moves its own pager to `Запись`; the next one reaches Me.
        swipe(towardsNext = true)
        TestUi.settle(SETTLE_MILLIS)
        swipe(towardsNext = true)
        awaitTab(AppTab.ME)
        swipe(towardsNext = false)
        awaitTab(AppTab.SPORT)

        onActivity { it.onBackPressedDispatcher.onBackPressed() }

        awaitTab(AppTab.HOME)
    }

    @Test
    fun aProfileLinkWhileOnTheRecordbookOpensTheProfileAboveItsTab() = onComposeShell {
        awaitTab(AppTab.HOME)
        swipe(towardsNext = false)
        swipe(towardsNext = false)
        awaitTab(AppTab.RECORDBOOK)

        // What a browser sends to the running task: the same instance gets onNewIntent.
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://dev.widgets.alllexey.dev/u/$ISU"))
                .setClass(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )

        eventually {
            val view = ShellProbe.current()
            assertEquals(AppTab.ME, view.tab)
            assertEquals(listOf<AppRoute>(AppRoutes.UserProfile(ISU)), view.overlays)
            assertFalse("no swipe under an overlay", view.swipeEnabled)
        }
        swipe(towardsNext = false)
        TestUi.settle(SETTLE_MILLIS)
        assertEquals(listOf<AppRoute>(AppRoutes.UserProfile(ISU)), ShellProbe.current().overlays)
    }

    @Test
    fun aSwipeOnMySportStaysInSportAndTheNextOneAtTheEdgeSwitchesTheTab() = onComposeShell {
        awaitTab(AppTab.HOME)
        swipe(towardsNext = true)
        awaitTab(AppTab.SPORT)
        TestUi.settle(SETTLE_MILLIS)
        assumeTrue("the sport root is registered in the Compose shell (SH-1b9)", showsText(MY_SPORT))

        swipe(towardsNext = true)
        TestUi.settle(SETTLE_MILLIS)
        assertEquals(AppTab.SPORT, ShellProbe.current().tab)
        eventually { assertTrue("the sign-up page shows", showsText(SIGN_UP)) }

        swipe(towardsNext = true)
        awaitTab(AppTab.ME)
    }

    /** Starts `MainActivity` and runs [body] when it hosts the Compose shell; the activity finishes itself after. */
    private fun onComposeShell(body: () -> Unit) {
        instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        try {
            var compose = false
            onActivity { compose = ShellHost.of(it) != null }
            assumeTrue("only the Compose shell has the tab swipe", compose)
            body()
        } finally {
            runCatching { onActivity { it.finish() } }
        }
    }

    /** A quick horizontal swipe across the middle 60% of the window, in from the system's back edges, at [swipeY]. */
    private fun swipe(towardsNext: Boolean) {
        val start = if (towardsNext) SWIPE_FAR else SWIPE_NEAR
        val end = if (towardsNext) SWIPE_NEAR else SWIPE_FAR
        val y = swipeY()
        onView(isRoot()).perform(GeneralSwipeAction(Swipe.FAST, at(start, y), at(end, y), Press.FINGER))
        TestUi.idle()
    }

    private fun at(fraction: Float, y: Float) = CoordinatesProvider { view: View ->
        val location = IntArray(2).also(view::getLocationOnScreen)
        floatArrayOf(location[0] + view.width * fraction, location[1] + y)
    }

    /**
     * The height in the window a swipe runs at: the middle of the shown tab content, or below the sport week strip
     * when that reaches lower, since the strip keeps every drag from the tabs (design.md "Tab swipe", rule 4). Under
     * an overlay the tab content has no semantics and the swipe runs at [SWIPE_HEIGHT] of the window.
     */
    private fun swipeY(): Float {
        var y = 0f
        onActivity { activity ->
            val decor = activity.window.decorView
            val shown = composeRoots(decor).flatMap { nodes(it.semanticsOwner.unmergedRootSemanticsNode).toList() }
                .filter { it.boundsInWindow.width > 0f && it.boundsInWindow.height > 0f }
            val content = shown.firstOrNull { it.tag == ShellTags.TAB_CONTENT }?.boundsInWindow
            if (content == null) {
                y = decor.height * SWIPE_HEIGHT
                return@onActivity
            }
            val margin = SWIPE_MARGIN_DP * activity.resources.displayMetrics.density
            val strip = shown.filter { it.tag == SportWeekStripTestTags.STRIP }.maxOfOrNull { it.boundsInWindow.bottom }
            y = maxOf(content.center.y, strip?.plus(margin) ?: 0f)
            check(y < content.bottom - margin) { "no room for a swipe between $y and the tab content's end $content" }
        }
        return y
    }

    private val SemanticsNode.tag: String? get() = config.getOrNull(SemanticsProperties.TestTag)

    private fun awaitTab(tab: AppTab) = eventually { assertEquals(tab, ShellProbe.current().tab) }

    private fun eventually(assertion: () -> Unit) =
        TestUi.eventually(attempts = 80, delayMillis = 100, message = "The shell did not settle", assertion = assertion)

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

    /** Whether the Compose content of `MainActivity` shows [text] now. */
    private fun showsText(text: String): Boolean {
        var found = false
        onActivity { activity ->
            found = composeRoots(activity.window.decorView).any { root ->
                nodes(root.semanticsOwner.unmergedRootSemanticsNode).any { node ->
                    node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text }
                }
            }
        }
        return found
    }

    private fun composeRoots(view: View): List<ViewRootForTest> = when (view) {
        is ViewRootForTest -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { composeRoots(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun nodes(root: SemanticsNode): Sequence<SemanticsNode> =
        generateSequence(listOf(root)) { level -> level.flatMap { it.children }.ifEmpty { null } }.flatten()

    private companion object {
        const val ISU = 100001
        const val SETTLE_MILLIS = 500L
        const val SWIPE_NEAR = 0.2f
        const val SWIPE_FAR = 0.8f
        const val SWIPE_HEIGHT = 0.45f

        /** Clear of the strip's blocked bottom padding (8 dp) and of the content's end. */
        const val SWIPE_MARGIN_DP = 24

        /** The sport pager's two segments (`title_sport_my`, `title_sport_sign`). */
        const val MY_SPORT = "Мой спорт"
        const val SIGN_UP = "Запись"
    }
}
