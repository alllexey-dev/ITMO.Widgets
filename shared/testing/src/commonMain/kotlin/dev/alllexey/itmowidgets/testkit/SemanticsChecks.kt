package dev.alllexey.itmowidgets.testkit

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Dp

/**
 * The minimum touch target of an actionable element under the platform's default style: Material's 48 dp on
 * Android, Apple's 44 pt on iOS (`ItmoPlatformStyle.minTouchTarget`, which `ItmoTheme` applies by default there).
 */
expect val MinTouchTarget: Dp

/**
 * Every clickable node is at least [minSize] square; the default is the platform style's minimum ([MinTouchTarget]).
 * It measures the layout node that carries the click, outer modifiers such as `minimumInteractiveComponentSize`
 * included; the node's touch bounds would not do, since Compose stretches them to 48 dp for any clickable. Replaces `ViewChecks.assertTouchTargets` for Compose screens.
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
 * No text is cut off: every text node lays out within its bounds and lines, and no line ends in an ellipsis. Nodes
 * that match [allowed] (deliberately shortened lines) are skipped.
 */
fun SemanticsNodeInteractionsProvider.assertNoTextOverflow(allowed: SemanticsMatcher? = null) {
    val failures = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .filterNot { allowed?.matches(it) == true }
        .filter { node -> node.textLayouts().any { it.overflows() } }
        .map { it.describe() }
    check(failures.isEmpty()) { "Text overflows its bounds:\n" + failures.joinToString("\n") }
}

/**
 * `hasVisualOverflow` would flag every short text narrower than its constraints: the layout that the semantics action
 * returns keeps the paragraph at the constraints' width while `size` is the text's own. So widths compare per line,
 * on the [drawn] layout against the node's `size`.
 */
private fun TextLayoutResult.overflows(): Boolean {
    val drawn = drawn().multiParagraph
    if (drawn.didExceedMaxLines || drawn.height > size.height) return true
    val lines = drawn.lineCount
    if (lines > 0 && drawn.isLineEllipsized(lines - 1)) return true
    return (0 until lines).any { line -> drawn.getLineRight(line) - drawn.getLineLeft(line) > size.width + LINE_SLACK_PX }
}

/**
 * The layout the text node draws. The semantics action lays the text out again from the unresolved style (androidx
 * `ParagraphLayoutCache.slowCreateTextLayoutResultOrNull` skips `resolveDefaults`). Android's paragraph falls back to
 * the same defaults; Skiko's does not, so on iOS a `BasicText` without its own font size and family came out wider and
 * taller than its node. [TextMeasurer] resolves the defaults as the node does.
 */
private fun TextLayoutResult.drawn(): TextLayoutResult = with(layoutInput) {
    TextMeasurer(fontFamilyResolver, density, layoutDirection, cacheSize = 0)
        .measure(text, style, overflow, softWrap, maxLines, placeholders, constraints)
}

/** Half a pixel for the rounding between a line's float width and the integer size. */
private const val LINE_SLACK_PX = 0.5f

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
