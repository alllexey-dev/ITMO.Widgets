package dev.alllexey.itmowidgets.testing

import android.app.Activity
import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue

/** Waiting helpers shared by the instrumented tests. */
object TestUi {
    val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    fun idle() = instrumentation.waitForIdleSync()

    /** Idle sync, a fixed sleep for animations and the compositor, idle sync again. */
    fun settle(milliseconds: Long) {
        idle()
        SystemClock.sleep(milliseconds)
        idle()
    }

    /**
     * Re-runs [assertion] until it passes or [attempts] are exhausted, sleeping [delayMillis]
     * between attempts. With [idleBetween] the main looper is drained before every attempt.
     */
    fun eventually(
        attempts: Int = 40,
        delayMillis: Long = 50,
        message: String = "Condition was not met in time",
        idleBetween: Boolean = false,
        assertion: () -> Unit
    ) {
        var failure: Throwable? = null
        repeat(attempts) {
            if (idleBetween) idle()
            try {
                assertion()
                return
            } catch (error: Throwable) {
                failure = error
                Thread.sleep(delayMillis)
            }
        }
        throw AssertionError(message, failure)
    }

    /** Invalidates the window and waits until the next frame has been committed. */
    fun awaitFrameCommit(activity: Activity, message: String = "Frame must be submitted") {
        val committed = CountDownLatch(1)
        instrumentation.runOnMainSync {
            val decor = activity.window.decorView
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                decor.viewTreeObserver.registerFrameCommitCallback(committed::countDown)
            } else {
                decor.postOnAnimation { decor.postOnAnimation(committed::countDown) }
            }
            decor.invalidate()
        }
        assertTrue(message, committed.await(5, TimeUnit.SECONDS))
    }
}
