package dev.alllexey.itmowidgets.feature.home

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import dev.alllexey.itmowidgets.app.SettingsNavigationTestActivity
import dev.alllexey.itmowidgets.feature.home.ui.HomeTestTags
import org.junit.Assert.assertTrue

/**
 * The Compose feed of `HomeFragment` read through its `ComposeView`'s semantics owner: `:app`'s instrumented tests have
 * no `ui-test-junit4`, so they find nodes by `HomeTestTags` and act through semantics actions. Main thread only.
 */
object HomeSemantics {

    /** The home tab's root view, the Fragment's `ComposeView`. */
    fun root(activity: SettingsNavigationTestActivity): View =
        activity.host.childFragmentManager.primaryNavigationFragment!!.requireView()

    /** The `ComposeView` that holds the home feed in any activity, such as `MainActivity`. */
    fun feedRoot(activity: Activity): View {
        val views = generateSequence(listOf<View>(activity.window.decorView)) { level ->
            level.filterIsInstance<ViewGroup>().flatMap { group -> (0 until group.childCount).map(group::getChildAt) }
                .ifEmpty { null }
        }.flatten()
        return views.filterIsInstance<ComposeView>().first { node(it, HomeTestTags.FEED) != null }
    }

    /** The node tagged [tag] in [root]'s unmerged semantics tree, or null when the screen does not show it. */
    fun node(root: View, tag: String): SemanticsNode? = nodes(root).firstOrNull { it.tag == tag }

    fun node(activity: SettingsNavigationTestActivity, tag: String): SemanticsNode? = node(root(activity), tag)

    /** Every node of [root]'s unmerged tree, breadth first. */
    fun nodes(root: View): Sequence<SemanticsNode> {
        val owner = ((root as ViewGroup).getChildAt(0) as ViewRootForTest).semanticsOwner
        return generateSequence(listOf(owner.unmergedRootSemanticsNode)) { level ->
            level.flatMap { it.children }.ifEmpty { null }
        }.flatten()
    }

    fun click(root: View, tag: String) {
        val onClick = node(root, tag)!!.config.getOrNull(SemanticsActions.OnClick)
        assertTrue("$tag has no click action", onClick?.action?.invoke() == true)
    }

    fun contentDescription(node: SemanticsNode): String? =
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()

    /** A node's own description, or the one of the icon it holds (a FAB). */
    fun describedAs(node: SemanticsNode): String? =
        contentDescription(node) ?: node.children.firstNotNullOfOrNull(::contentDescription)

    /** The list's position as Compose reports it to accessibility: first item index and offset, as one value. */
    fun scrollPosition(root: View, tag: String): Float =
        node(root, tag)!!.config[SemanticsProperties.VerticalScrollAxisRange].value()

    fun scrollToIndex(root: View, tag: String, index: Int) {
        val action = node(root, tag)!!.config[SemanticsActions.ScrollToIndex].action
        assertTrue("$tag did not scroll to $index", action?.invoke(index) == true)
    }

    fun scrollBy(root: View, tag: String, pixels: Float) {
        val action = node(root, tag)!!.config[SemanticsActions.ScrollBy].action
        assertTrue("$tag did not scroll by $pixels", action?.invoke(0f, pixels) == true)
    }

    private val SemanticsNode.tag: String? get() = config.getOrNull(SemanticsProperties.TestTag)
}
