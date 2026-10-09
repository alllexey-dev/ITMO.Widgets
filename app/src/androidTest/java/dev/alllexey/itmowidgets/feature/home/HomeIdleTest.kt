package dev.alllexey.itmowidgets.feature.home

import android.os.SystemClock
import android.view.ViewTreeObserver
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.alllexey.itmowidgets.app.HomeFixture
import dev.alllexey.itmowidgets.app.SettingsNavigationTestActivity
import dev.alllexey.itmowidgets.testing.TestUi
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A feed at rest draws nothing. The lesson in progress is a hero with a wavy bar: Material's default slides the wave
 * forever, which drew every frame, kept the main thread from idling (the UI run hung in `waitForIdleSync`) and on a
 * loaded device delayed input into ANRs. Its progress changes once a minute, so the window must stop drawing.
 */
@RunWith(AndroidJUnit4::class)
class HomeIdleTest {
    @Test
    fun feedWithALessonInProgressStopsDrawing() {
        SettingsNavigationTestActivity.homeFixture = HomeFixture()
        ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
            TestUi.settle(SETTLE_MILLIS)
            scenario.onActivity { activity ->
                val bar = HomeSemantics.nodes(HomeSemantics.root(activity)).firstOrNull {
                    it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.current == LESSON_PROGRESS
                }
                assertNotNull("The fixture's lesson in progress must be on screen", bar)
            }
            val draws = AtomicInteger()
            val counter = ViewTreeObserver.OnDrawListener { draws.incrementAndGet() }
            scenario.onActivity { it.window.decorView.viewTreeObserver.addOnDrawListener(counter) }
            SystemClock.sleep(WATCH_MILLIS)
            scenario.onActivity { it.window.decorView.viewTreeObserver.removeOnDrawListener(counter) }
            assertTrue("The feed at rest drew ${draws.get()} frames in $WATCH_MILLIS ms", draws.get() <= MAX_DRAWS)
        }
    }

    private companion object {
        const val SETTLE_MILLIS = 650L
        const val WATCH_MILLIS = 1_000L

        /** A stray frame (a ripple end, a late layout) is fine; an animation draws about 60. */
        const val MAX_DRAWS = 3

        /** `HomeFixture.schedule()`'s lesson in progress. */
        const val LESSON_PROGRESS = 0.55f
    }
}
