package dev.alllexey.itmowidgets.testkit

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The minimum touch target of an actionable element (Material, `docs/design.md`). */
val MinTouchTarget: Dp = 48.dp

/**
 * Every clickable node is at least [minSize] square. It measures the layout node that carries the click, outer
 * modifiers such as `minimumInteractiveComponentSize` included; the node's touch bounds would not do, since Compose
 * stretches them to 48 dp for any clickable. Replaces `ViewChecks.assertTouchTargets` for Compose screens.
 */
fun SemanticsNodeInteractionsProvider.assertTouchTargets(minSize: Dp = MinTouchTarget) {
    val failures = onAllNodes(hasClickAction()).fetchSemanticsNodes().mapNotNull { node ->
        val width = node.layoutInfo.width
        val height = node.layoutInfo.height
        // One pixel of slack for rounding, as ViewChecks allowed.
        val min = with(node.layoutInfo.density) { minSize.toPx() } - 1f
        if (width >= min && height >= min) null else "${node.describe()}: $width x $height px, needs $min px"
    }
    check(failures.isEmpty()) { "Touch targets below $minSize:\n" + failures.joinToString("\n") }
}

/**
 * No text is cut off: every text node lays out within its bounds and lines (`TextLayoutResult.hasVisualOverflow`,
 * which an ellipsis also sets). Nodes that match [allowed] (deliberately shortened lines) are skipped.
 */
fun SemanticsNodeInteractionsProvider.assertNoTextOverflow(allowed: SemanticsMatcher? = null) {
    val failures = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .filterNot { allowed?.matches(it) == true }
        .filter { node -> node.textLayouts().any { it.hasVisualOverflow } }
        .map { it.describe() }
    check(failures.isEmpty()) { "Text overflows its bounds:\n" + failures.joinToString("\n") }
}

private fun SemanticsNode.textLayouts(): List<TextLayoutResult> {
    val results = mutableListOf<TextLayoutResult>()
    config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
    return results
}

private fun SemanticsNode.describe(): String {
    val text = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ")
    val label = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
    return "node #$id" + (text?.let { " \"$it\"" } ?: "") + (label?.let { " [$it]" } ?: "")
}
