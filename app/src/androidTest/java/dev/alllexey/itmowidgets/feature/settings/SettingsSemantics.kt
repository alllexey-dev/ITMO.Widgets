package dev.alllexey.itmowidgets.feature.settings

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextLayoutResult
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingRowId
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsTestTags
import org.junit.Assert.assertTrue

/**
 * The Compose settings page of `SettingsFragment` read through its `ComposeView`: rows by their `SettingRowId.key`
 * tags, in screen order, with the texts they show. `:app`'s instrumented tests have no `ui-test-junit4`, so this
 * walks the `ComposeView`'s semantics owner and acts through semantics actions. Main thread only.
 */
object SettingsSemantics {

    private val rowKeys = SettingRowId.entries.associateBy { it.key }

    /** Every node of [root]'s unmerged tree, breadth first; [root] is the Fragment's `ComposeView`. */
    fun nodes(root: View): Sequence<SemanticsNode> {
        val owner = ((root as ViewGroup).getChildAt(0) as ViewRootForTest).semanticsOwner
        return generateSequence(listOf(owner.unmergedRootSemanticsNode)) { level ->
            level.flatMap { it.children }.ifEmpty { null }
        }.flatten()
    }

    /** The node tagged [tag], or null when the page does not show it. */
    fun node(root: View, tag: String): SemanticsNode? = nodes(root).firstOrNull { it.tag == tag }

    fun click(root: View, tag: String) {
        val onClick = checkNotNull(node(root, tag)) { "No node tagged $tag" }.config.getOrNull(SemanticsActions.OnClick)
        assertTrue("$tag has no click action", onClick?.action?.invoke() == true)
    }

    /** Scrolls the node tagged [tag] by [pixels], as an accessibility service would. */
    fun scrollBy(root: View, tag: String, pixels: Float) {
        val action = checkNotNull(node(root, tag)) { "No node tagged $tag" }.config[SemanticsActions.ScrollBy].action
        assertTrue("$tag did not scroll by $pixels", action?.invoke(0f, pixels) == true)
    }

    /** Every settings row on the page, top to bottom. */
    fun rows(root: View): List<SemanticsNode> =
        nodes(root).filter { it.tag in rowKeys }.sortedBy { it.boundsInWindow.top }.toList()

    fun row(root: View, id: SettingRowId): SemanticsNode? = node(root, id.key)

    fun rowWithTitle(root: View, title: String): SemanticsNode? = rows(root).firstOrNull { title(it) == title }

    /** The rows' titles, top to bottom. */
    fun titles(root: View): List<String> = rows(root).map(::title)

    fun title(row: SemanticsNode): String = texts(row).first()

    /** The row's lines in order: the title, then the description and the value it shows. */
    fun texts(row: SemanticsNode): List<String> = row.depthFirst().mapNotNull { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
    }.toList()

    fun isOn(row: SemanticsNode): Boolean = row.config.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On

    fun isSwitch(row: SemanticsNode): Boolean = row.config.getOrNull(SemanticsProperties.ToggleableState) != null

    /** A target the user can act on: it has a click and is not disabled. */
    fun isActionable(row: SemanticsNode): Boolean =
        row.config.getOrNull(SemanticsActions.OnClick) != null &&
            row.config.getOrNull(SemanticsProperties.Disabled) == null

    fun click(root: View, id: SettingRowId) = click(root, id.key)

    fun clickTitle(root: View, title: String) {
        val row = checkNotNull(rowWithTitle(root, title)) { "No settings row titled $title" }
        assertTrue("$title has no click action", row.config[SemanticsActions.OnClick].action?.invoke() == true)
    }

    fun back(root: View) = click(root, SettingsTestTags.BACK)

    fun footer(root: View): String? =
        node(root, SettingsTestTags.FOOTER)?.let { texts(it).firstOrNull() }

    fun hasScroll(root: View): Boolean = node(root, SettingsTestTags.SCROLL) != null

    fun hasProgress(root: View): Boolean = node(root, SettingsTestTags.PROGRESS) != null

    /** Every row is at least the 48 dp touch target, one pixel of slack for rounding. */
    fun assertTouchTargets(root: View) {
        val min = 48 * root.resources.displayMetrics.density - 1
        rows(root).filter { it.config.getOrNull(SemanticsActions.OnClick) != null }.forEach { row ->
            assertTrue("${title(row)} is ${row.size.height} px high", row.size.height >= min)
        }
    }

    /** No text on the page is cut off or ellipsized. */
    fun assertTextFits(root: View) {
        nodes(root).forEach { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            layouts.forEach { layout ->
                val text = layout.layoutInput.text.text
                assertTrue("Text overflows its height: $text", !layout.didOverflowHeight)
                assertTrue("Text is ellipsized: $text", layout.lineCount == 0 || !layout.isLineEllipsized(layout.lineCount - 1))
            }
        }
    }

    private fun SemanticsNode.depthFirst(): Sequence<SemanticsNode> = sequence {
        yield(this@depthFirst)
        children.forEach { yieldAll(it.depthFirst()) }
    }

    private val SemanticsNode.tag: String? get() = config.getOrNull(SemanticsProperties.TestTag)
}
