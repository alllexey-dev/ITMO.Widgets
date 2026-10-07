package dev.alllexey.itmowidgets.feature.auth

import android.app.Activity
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import dev.alllexey.itmowidgets.feature.auth.ui.AuthTestTags
import dev.alllexey.itmowidgets.testing.TestUi
import org.junit.Assert.assertNotNull

/**
 * The Compose sign-in screen of `AuthFragment` read through its `ComposeView`'s semantics owner (`:app`'s
 * instrumented tests have no `ui-test-junit4`), found by `AuthTestTags`. The logo has no click action, by design, so
 * [tap] sends a real down and up to the window at the node's centre.
 */
object AuthSemantics {

    /** The node tagged [tag] on the sign-in screen of [activity], or null while the screen does not show it. */
    fun node(activity: Activity, tag: String): SemanticsNode? {
        var found: SemanticsNode? = null
        TestUi.instrumentation.runOnMainSync {
            found = composeViews(activity.window.decorView).filter { it.isShown }.firstNotNullOfOrNull { root ->
                nodes(root).firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
            }
        }
        return found
    }

    /** Whether the sign-in screen shows its content, the logo included. */
    fun isShown(activity: Activity): Boolean = node(activity, AuthTestTags.LOGO) != null

    /** Window bounds of the node tagged [tag]; fails when it is not shown. */
    fun bounds(activity: Activity, tag: String): RectF {
        val node = node(activity, tag)
        assertNotNull("$tag is not shown", node)
        val rect = node!!.boundsInWindow
        return RectF(rect.left, rect.top, rect.right, rect.bottom)
    }

    /** A tap at the centre of the node tagged [tag], as a finger gives it. */
    fun tap(activity: Activity, tag: String) {
        val bounds = bounds(activity, tag)
        TestUi.instrumentation.runOnMainSync {
            val time = SystemClock.uptimeMillis()
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
                val event = MotionEvent.obtain(time, time, action, bounds.centerX(), bounds.centerY(), 0)
                activity.dispatchTouchEvent(event)
                event.recycle()
            }
        }
    }

    private fun composeViews(view: View): Sequence<ComposeView> = sequence {
        if (view is ComposeView && view.childCount > 0) yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(composeViews(view.getChildAt(index)))
    }

    private fun nodes(root: ComposeView): Sequence<SemanticsNode> {
        val owner = (root.getChildAt(0) as ViewRootForTest).semanticsOwner
        return generateSequence(listOf(owner.unmergedRootSemanticsNode)) { level ->
            level.flatMap { it.children }.ifEmpty { null }
        }.flatten()
    }
}
