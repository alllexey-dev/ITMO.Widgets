package dev.alllexey.itmowidgets.app.shell

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
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
 * shows again without the sheet. The slide is sampled on every frame: the profile keeps the window's height and end,
 * so the overlay layer never shrinks it towards the centre. Only the Compose shell is read; the legacy shell is a
 * debug fallback with the Fragment host's own animation.
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

        val push = sampleOverlay { clickTeacher() }

        eventually {
            val shown = ShellProbe.current()
            assertEquals(listOf<AppRoute>(profile), shown.overlays)
            assertNull("the sheet dismisses as in 2.2", shown.floating)
        }
        TestUi.settle(SETTLE_MILLIS)

        val pop = sampleOverlay { activity -> activity.onBackPressedDispatcher.onBackPressed() }

        eventually {
            val shown = ShellProbe.current()
            assertEquals(AppTab.SCHEDULE, shown.tab)
            assertEquals(emptyList<AppRoute>(), shown.overlays)
            assertNull("the lesson sheet does not come back (2.2)", shown.floating)
        }
        Log.i(TAG, "push ${push.frames} in ${push.layer}")
        Log.i(TAG, "pop ${pop.frames} in ${pop.layer}")
        assumeTrue("animations are on", ValueAnimator.areAnimatorsEnabled())
        // The push composes the profile in its first frames, so an emulator may drop most of them; the pop is light.
        assertSlidesAtFullSize(push, towardsEnd = false, minMovingFrames = 1)
        assertSlidesAtFullSize(pop, towardsEnd = true, minMovingFrames = MIN_SLIDE_FRAMES)
    }

    /**
     * Runs [command] on the main thread and reads the profile overlay's bounds in the window on every frame for
     * [SAMPLE_MILLIS], with the overlay layer's bounds; frames where the overlay is not placed are dropped.
     */
    private fun sampleOverlay(command: (MainActivity) -> Unit): Samples {
        val frames = mutableListOf<Rect>()
        var layer: Rect? = null
        val done = CountDownLatch(1)
        onActivity { activity ->
            val choreographer = Choreographer.getInstance()
            val until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SAMPLE_MILLIS)
            val sample = object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    val nodes = composedNodes(activity.window.decorView)
                    nodes.firstOrNull { it.tag == ShellTags.OVERLAY_LAYER }?.let { layer = it.boundsInWindow }
                    nodes.firstOrNull { it.tag == ShellTags.overlay(profile.toString()) }?.boundsInWindow
                        ?.takeIf { it.width > 0f }
                        ?.let(frames::add)
                    if (frameTimeNanos < until) choreographer.postFrameCallback(this) else done.countDown()
                }
            }
            command(activity)
            choreographer.postFrameCallback(sample)
        }
        assertTrue("the frames were not sampled", done.await(SAMPLE_MILLIS * 4, TimeUnit.MILLISECONDS))
        return Samples(frames.toList(), checkNotNull(layer) { "no overlay layer" })
    }

    private class Samples(val frames: List<Rect>, val layer: Rect)

    /** Every sampled frame keeps the layer's height and end; the start moves one way over [minMovingFrames]. */
    private fun assertSlidesAtFullSize(samples: Samples, towardsEnd: Boolean, minMovingFrames: Int) {
        val frames = samples.frames
        val layer = samples.layer
        val moving = frames.filter { it.left > layer.left + EDGE_PX && it.left < layer.right - EDGE_PX }
        assertTrue("the profile slides over several frames: $frames", moving.size >= minMovingFrames)
        frames.forEach { frame ->
            assertEquals("the profile keeps the layer's top: $frames", layer.top, frame.top, EDGE_PX)
            assertEquals("the profile keeps the layer's bottom: $frames", layer.bottom, frame.bottom, EDGE_PX)
            assertEquals("the profile keeps the layer's end: $frames", layer.right, frame.right, EDGE_PX)
        }
        val lefts = frames.map { it.left }
        assertEquals("one direction: $lefts", if (towardsEnd) lefts.sorted() else lefts.sortedDescending(), lefts)
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

        /** Well past the 220 ms slide and a dropped frame or two. */
        const val SAMPLE_MILLIS = 600L
        const val MIN_SLIDE_FRAMES = 3
        const val EDGE_PX = 1.5f

        /** The teacher row's click label (`teacher_open_profile`). */
        const val OPEN_PROFILE = "Открыть профиль"
    }
}
