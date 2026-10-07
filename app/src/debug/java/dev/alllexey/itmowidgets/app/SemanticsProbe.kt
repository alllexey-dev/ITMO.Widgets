package dev.alllexey.itmowidgets.app

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull

/**
 * Whether the Compose screen this Fragment root holds shows a node tagged [tag]: a ported page is a `ComposeView`,
 * so the debug hosts' frame probes read its semantics instead of view visibility. Main thread only.
 */
internal fun View.hasSemanticsTag(tag: String): Boolean {
    val root = (this as? ViewGroup)?.getChildAt(0) as? ViewRootForTest ?: return false
    return generateSequence(listOf(root.semanticsOwner.unmergedRootSemanticsNode)) { level ->
        level.flatMap { it.children }.ifEmpty { null }
    }.flatten().any { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
}
