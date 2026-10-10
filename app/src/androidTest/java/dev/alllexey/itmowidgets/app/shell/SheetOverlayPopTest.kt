package dev.alllexey.itmowidgets.app.shell

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import androidx.activity.BackEventCompat
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dagger.hilt.android.EntryPointAccessors
import dev.alllexey.itmowidgets.app.MainActivity
import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.notification.NotificationDebugEntryPoint
import dev.alllexey.itmowidgets.testing.ShellProbe
import dev.alllexey.itmowidgets.testing.TestSession
import dev.alllexey.itmowidgets.testing.TestUi
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A profile opened from the lesson sheet on the real `MainActivity` in the demo session (SH-1c-FIX4): the sheet
 * dismisses as in 2.2, the profile slides in from the end, and Back slides it out to the end over the schedule, which
 * shows again without the sheet. Every frame the emulator draws keeps the window's height and end, so the overlay layer
 * never shrinks the profile towards the centre; a predictive Back held at half way shows the horizontal move on a
 * frame that does not depend on timing. How many frames a real slide gets depends on the emulator's load, so the
 * frame-by-frame slide is counted on a paused clock in `AppShellTest` instead. Only the Compose shell is read; the
 * legacy shell is a debug fallback with the Fragment host's own animation.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q) // WindowInspector reads the sheet's window
class SheetOverlayPopTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation get() = TestUi.instrumentation
    private val teacher = DemoPeople.MATH_TEACHER.isu
    private val profile = AppRoutes.UserProfile(teacher)

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
    fun theTeacherFromTheLessonSheetSlidesInAndBackOutOverTheSchedule() = onComposeShell {
        eventually { assertEquals(AppTab.HOME, ShellProbe.current().tab) }
        onActivity { activity ->
            activity.navigator().select(AppTab.SCHEDULE)
            activity.navigator().open(AppRoutes.LessonDetails(lesson))
        }
        eventually { assertEquals(AppRoutes.LessonDetails(lesson), ShellProbe.current().floating) }
        TestUi.settle(SETTLE_MILLIS)

        val push = sampleOverlay(settled = { layer, overlay -> layer != null && overlay?.atRestIn(layer) == true }) {
            clickTeacher()
        }

        eventually {
            val shown = ShellProbe.current()
            assertEquals(listOf<AppRoute>(profile), shown.overlays)
            assertNull("the sheet dismisses as in 2.2", shown.floating)
        }
        TestUi.settle(SETTLE_MILLIS)
        val layer = checkNotNull(push.layer) { "no overlay layer" }
        assertTrue("the profile comes to rest at the layer's start: ${push.frames}", push.frames.last().atRestIn(layer))

        onActivity { it.dragBack(HALF) }
        eventually {
            val held = checkNotNull(overlayBounds()) { "the profile is not placed" }
            assertEquals("the profile follows the finger: $held", layer.left + layer.width * HALF, held.left, EDGE_PX)
            assertAtFullSize(held, layer)
        }
        onActivity { it.onBackPressedDispatcher.dispatchOnBackCancelled() }
        eventually { assertTrue("the cancel puts the profile back", checkNotNull(overlayBounds()).atRestIn(layer)) }
        TestUi.settle(SETTLE_MILLIS)

        val pop = sampleOverlay(settled = { _, overlay -> overlay == null }) { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }

        eventually {
            val shown = ShellProbe.current()
            assertEquals(AppTab.SCHEDULE, shown.tab)
            assertEquals(emptyList<AppRoute>(), shown.overlays)
            assertNull("the lesson sheet does not come back (2.2)", shown.floating)
        }
        Log.i(TAG, "push ${push.frames} in $layer")
        Log.i(TAG, "pop ${pop.frames} in ${pop.layer}")
        assertSlidesAtFullSize(push.frames, layer, towardsEnd = false)
        assertSlidesAtFullSize(pop.frames, layer, towardsEnd = true)
    }

    /**
     * Runs [command] on the main thread and reads the profile overlay's bounds in the window on every frame until
     * [settled] holds for the overlay layer and the profile (null while not seen or not placed), with the layer's
     * bounds.
     */
    private fun sampleOverlay(
        settled: (layer: Rect?, overlay: Rect?) -> Boolean,
        command: (MainActivity) -> Unit,
    ): Samples {
        val frames = mutableListOf<Rect>()
        var layer: Rect? = null
        val done = CountDownLatch(1)
        onActivity { activity ->
            val choreographer = Choreographer.getInstance()
            val sample = object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    val nodes = composedNodes(activity.window.decorView)
                    nodes.firstOrNull { it.tag == ShellTags.OVERLAY_LAYER }?.let { layer = it.boundsInWindow }
                    val overlay = nodes.placedOverlay()?.also(frames::add)
                    if (settled(layer, overlay)) done.countDown() else choreographer.postFrameCallback(this)
                }
            }
            command(activity)
            choreographer.postFrameCallback(sample)
        }
        assertTrue("the overlay did not settle: $frames", done.await(SETTLE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
        return Samples(frames.toList(), layer)
    }

    private class Samples(val frames: List<Rect>, val layer: Rect?)

    /** Every sampled frame keeps the layer's height and end, and the start moves one way. */
    private fun assertSlidesAtFullSize(frames: List<Rect>, layer: Rect, towardsEnd: Boolean) {
        frames.forEach { assertAtFullSize(it, layer, "$frames") }
        val lefts = frames.map { it.left }
        assertEquals("one direction: $lefts", if (towardsEnd) lefts.sorted() else lefts.sortedDescending(), lefts)
    }

    private fun assertAtFullSize(frame: Rect, layer: Rect, frames: String = "$frame") {
        assertEquals("the profile keeps the layer's top: $frames", layer.top, frame.top, EDGE_PX)
        assertEquals("the profile keeps the layer's bottom: $frames", layer.bottom, frame.bottom, EDGE_PX)
        assertEquals("the profile keeps the layer's end: $frames", layer.right, frame.right, EDGE_PX)
    }

    private fun Rect.atRestIn(layer: Rect) = abs(left - layer.left) <= EDGE_PX

    private fun List<SemanticsNode>.placedOverlay(): Rect? =
        firstOrNull { it.tag == ShellTags.overlay(profile.toString()) }?.boundsInWindow?.takeIf { it.width > 0f }

    private fun overlayBounds(): Rect? {
        var bounds: Rect? = null
        onActivity { bounds = composedNodes(it.window.decorView).placedOverlay() }
        return bounds
    }

    /** A predictive Back from the start edge, held at [progress]. */
    private fun MainActivity.dragBack(progress: Float) {
        onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
        onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(0f, 0f, progress, BackEventCompat.EDGE_LEFT))
    }

    /** The sheet's teacher row, in the sheet's own window, by its semantics click. */
    private fun clickTeacher() {
        val row = WindowInspector.getGlobalWindowViews().flatMap(::composedNodes).firstOrNull { node ->
            node.config.getOrNull(SemanticsActions.OnClick)?.label == OPEN_PROFILE
        }
        checkNotNull(checkNotNull(row) { "the sheet has no teacher row" }.config[SemanticsActions.OnClick].action)
            .invoke()
    }

    private fun onComposeShell(body: () -> Unit) {
        instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        try {
            var compose = false
            onActivity { compose = ShellHost.of(it) != null }
            assumeTrue("the Compose shell runs", compose)
            body()
        } finally {
            runCatching { onActivity { it.finish() } }
        }
    }

    /** The Compose shell's navigator; `ShellHost` keeps it private, so the test reads the field. */
    private fun MainActivity.navigator(): Nav3AppNavigator {
        val host = checkNotNull(ShellHost.of(this)) { "MainActivity runs the legacy shell" }
        val field = ShellHost::class.java.getDeclaredField("navigator").apply { isAccessible = true }
        return checkNotNull(field.get(host) as Nav3AppNavigator?) { "the Compose shell has not composed yet" }
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

    private fun composedNodes(view: View): List<SemanticsNode> = composeRoots(view).flatMap { root ->
        generateSequence(listOf(root.semanticsOwner.unmergedRootSemanticsNode)) { level ->
            level.flatMap { it.children }.ifEmpty { null }
        }.flatten().toList()
    }

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
        teacherIsu = teacher.toLong(),
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
        const val TAG = "SheetOverlayPopTest"
        const val SETTLE_MILLIS = 500L

        /** Far past the 220 ms slide on a loaded emulator; the sampling stops as soon as the profile settles. */
        const val SETTLE_TIMEOUT_MILLIS = 10_000L
        const val HALF = 0.5f
        const val EDGE_PX = 1.5f

        /** The teacher row's click label (`teacher_open_profile`). */
        const val OPEN_PROFILE = "Открыть профиль"
    }
}
