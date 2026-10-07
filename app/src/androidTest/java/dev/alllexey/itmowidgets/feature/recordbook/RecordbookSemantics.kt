package dev.alllexey.itmowidgets.feature.recordbook

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ActivityScenario
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookPreviewActivity
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookTestTags
import org.junit.Assert.assertTrue
import org.junit.Assert.fail

/**
 * The Compose list of `RecordbookFragment` in the preview host, read through its `ComposeView`'s semantics owner:
 * `:app`'s instrumented tests have no `ui-test-junit4`, so the hub cases open a subject by its row's description
 * and a semantics click (LR-3). Main thread only, apart from [openSubject].
 */
object RecordbookSemantics {

    /** The list Fragment's `ComposeView`. */
    fun root(activity: RecordbookPreviewActivity): View =
        checkNotNull(activity.supportFragmentManager.findFragmentByTag(RecordbookPreviewActivity.ROOT_TAG)).requireView()

    /** Every node of [root]'s unmerged tree, breadth first. */
    fun nodes(root: View): Sequence<SemanticsNode> {
        val owner = ((root as ViewGroup).getChildAt(0) as ViewRootForTest).semanticsOwner
        return generateSequence(listOf(owner.unmergedRootSemanticsNode)) { level ->
            level.flatMap { it.children }.ifEmpty { null }
        }.flatten()
    }

    /** The composed subject row whose description holds [namePart], or null while it is off screen. */
    fun subjectRow(root: View, namePart: String): SemanticsNode? = nodes(root).firstOrNull { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(RecordbookTestTags.SUBJECT_PREFIX) == true &&
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { namePart in it }
    }

    /**
     * Scrolls the list half a screen at a time until the row of [namePart] is composed, clicks it and lets the
     * subject page open through [settle]; the row's own click, so the host's navigation runs as for a tap.
     */
    fun openSubject(scenario: ActivityScenario<RecordbookPreviewActivity>, namePart: String, settle: () -> Unit) {
        repeat(MAX_STEPS) {
            var opened = false
            scenario.onActivity { activity ->
                val root = root(activity)
                val row = subjectRow(root, namePart)
                if (row != null) {
                    val click = row.config.getOrNull(SemanticsActions.OnClick)
                    assertTrue("The $namePart row has no click action", click?.action?.invoke() == true)
                    opened = true
                } else {
                    val list = nodes(root).first { it.config.getOrNull(SemanticsProperties.TestTag) == RecordbookTestTags.LIST }
                    list.config[SemanticsActions.ScrollBy].action?.invoke(0f, list.boundsInRoot.height / 2)
                }
            }
            settle()
            if (opened) return
        }
        fail("No subject row shows $namePart")
    }

    private const val MAX_STEPS = 20
}
