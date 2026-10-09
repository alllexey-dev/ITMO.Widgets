package dev.alllexey.itmowidgets.app.shell

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dagger.hilt.android.EntryPointAccessors
import dev.alllexey.itmowidgets.app.MainActivity
import dev.alllexey.itmowidgets.core.navigation.AppEntryIntents
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.notification.NotificationDebugEntryPoint
import dev.alllexey.itmowidgets.testing.ShellProbe
import dev.alllexey.itmowidgets.testing.TestSession
import dev.alllexey.itmowidgets.testing.TestUi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Nothing the activity's window shows draws under the status bar or into a side's system bar or cutout: every text of
 * a tab root and of an overlay screen lies inside the safe area. The Compose shell keeps clear of the system bars and
 * the display cutout; the legacy pass checks only the system bars, which is all `MainActivity`'s legacy root pads
 * (2.2 parity, a debug fallback since release builds run the Compose shell). Sheets and dialogs are windows of their
 * own and are not read. Runs in the demo session in every shell [ShellModeRule] knows.
 */
@RunWith(AndroidJUnit4::class)
class ShellInsetsTest {

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
    fun everyTabRootStartsBelowTheStatusBar() {
        listOf(
            null to AppTab.HOME,
            AppEntryIntents.ACTION_OPEN_SCHEDULE to AppTab.SCHEDULE,
            AppEntryIntents.ACTION_OPEN_RECORDBOOK to AppTab.RECORDBOOK,
            AppEntryIntents.ACTION_OPEN_SPORT to AppTab.SPORT,
        ).forEach { (action, tab) ->
            launch(Intent(context, MainActivity::class.java).setAction(action)) {
                awaitRoute(tab, overlay = null)
                assertTextInsideTheSafeArea("the ${tab.name} tab")
            }
        }
    }

    @Test
    fun overlayScreensStartBelowTheStatusBar() {
        launch(Intent(context, MainActivity::class.java).setAction(AppEntryIntents.ACTION_OPEN_QR_PASS)) {
            awaitRoute(AppTab.HOME, AppRoutes.QrPass)
            assertTextInsideTheSafeArea("the QR pass")
        }
        launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://dev.widgets.alllexey.dev/u/$ISU"))) {
            awaitRoute(AppTab.ME, AppRoutes.UserProfile(ISU))
            assertTextInsideTheSafeArea("the user profile")
        }
    }

    /** Starts `MainActivity` with [intent] in a fresh task, runs [body] and finishes the activity. */
    private fun launch(intent: Intent, body: () -> Unit) {
        instrumentation.startActivitySync(
            intent.setClass(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        try {
            body()
        } finally {
            runCatching { onActivity { it.finish() } }
            TestUi.idle()
        }
    }

    private fun awaitRoute(tab: AppTab, overlay: AppRoute?) {
        eventually {
            val shown = ShellProbe.current()
            assertEquals(tab, shown.tab)
            assertEquals(listOfNotNull(overlay), shown.overlays)
        }
        TestUi.settle(SETTLE_MILLIS)
    }

    /** Every visible text of the activity's window lies below the status bar and between the side insets. */
    private fun assertTextInsideTheSafeArea(what: String) = eventually {
        onActivity { activity ->
            val decor = activity.window.decorView
            val insets = checkNotNull(ViewCompat.getRootWindowInsets(decor)).getInsets(safeAreaTypes())
            assertTrue("the emulator shows a status bar", insets.top > 0)
            val texts = visibleTexts(decor)
            assertTrue("$what shows text", texts.isNotEmpty())
            val outside = texts.filter { (_, bounds) ->
                bounds.top < insets.top || bounds.left < insets.left || bounds.right > decor.width - insets.right
            }
            assertTrue(
                "$what draws text outside the safe area $insets: " +
                    outside.joinToString { (text, bounds) -> "\"$text\" at $bounds" },
                outside.isEmpty(),
            )
        }
    }

    /** The legacy root pads by the system bars only (as in 2.2); the Compose shell also by the cutout. */
    private fun safeAreaTypes(): Int = when (shell.mode) {
        ShellModeRule.Mode.LEGACY -> WindowInsetsCompat.Type.systemBars()
        ShellModeRule.Mode.NAV3 -> WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    }

    /** The texts of the shown Compose roots with their bounds in the window, clipped by their ancestors. */
    private fun visibleTexts(decor: View): List<Pair<String, Rect>> =
        decor.descendants().filter { it.isShown }.filterIsInstance<ViewRootForTest>().flatMap { root ->
            root.semanticsOwner.unmergedRootSemanticsNode.subtree().mapNotNull { node ->
                val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
                val bounds = node.boundsInWindow
                if (text.isNullOrBlank() || bounds.width <= 0f || bounds.height <= 0f) null else text to bounds
            }
        }.toList()

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

    private fun View.descendants(): Sequence<View> {
        val group = this as? ViewGroup ?: return sequenceOf(this)
        return sequenceOf(this) + (0 until group.childCount).asSequence().flatMap { group.getChildAt(it).descendants() }
    }

    private fun SemanticsNode.subtree(): Sequence<SemanticsNode> =
        sequenceOf(this) + children.asSequence().flatMap { it.subtree() }

    private companion object {
        const val ISU = 100001
        const val SETTLE_MILLIS = 500L
    }
}
