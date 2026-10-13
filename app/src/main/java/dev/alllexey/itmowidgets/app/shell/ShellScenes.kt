package dev.alllexey.itmowidgets.app.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.min
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import dev.alllexey.itmowidgets.core.navigation.SheetPolicy

/** A sheet entry's [SheetPolicy]; [BottomSheetSceneStrategy] shows such an entry in a modal bottom sheet. */
object SheetPolicyKey : NavMetadataKey<SheetPolicy>

fun bottomSheet(policy: SheetPolicy): Map<String, Any> = metadata { put(SheetPolicyKey, policy) }

/**
 * Navigation 3 1.2 has no first-party sheet scene: an entry with [bottomSheet] metadata becomes an
 * [OverlayScene] in a material3 [ModalBottomSheet] above the entries below it, which stay composed. The sheet is the
 * container: expanded without a collapsed step, in one of the three sizes of [SheetPolicy.Height] (as tall as its
 * content, [SheetPolicy.TALL_FRACTION] of the window, or the whole height below the status bar), never under the
 * status bar; the body (a `SheetScaffold`) draws its own handle.
 */
class BottomSheetSceneStrategy<T : Any> : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val last = entries.lastOrNull() ?: return null
        val policy = last.metadata[SheetPolicyKey] ?: return null
        val below = entries.dropLast(1)
        return BottomSheetScene(last.contentKey, last, below, policy, onBack)
    }

    companion object {
        /** The test tag of the sheet holding the entry with [contentKey]. */
        fun tag(contentKey: Any): String = "sheet:$contentKey"
    }
}

/**
 * A [SheetPolicy.Dismissal.FORM] sheet ignores drag, Back and a tap outside: Back reaches the entry's own handler on
 * the sheet window, which asks before discarding. Equality over key, entry, policy and the entries below, because
 * `NavDisplay` keeps the first overlay instance per key.
 */
@OptIn(ExperimentalMaterial3Api::class)
private class BottomSheetScene<T : Any>(
    override val key: Any,
    private val entry: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
    private val policy: SheetPolicy,
    private val onBack: () -> Unit,
) : OverlayScene<T> {
    override val entries: List<NavEntry<T>> = listOf(entry)
    override val overlaidEntries: List<NavEntry<T>> get() = previousEntries

    /** Set by [content]; [onRemove] animates the sheet out when the back stack drops the entry. */
    private var sheetState: SheetState? = null

    override val content: @Composable () -> Unit = {
        val free = policy.dismissal == SheetPolicy.Dismissal.FREE
        val state = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            confirmValueChange = { free || it != SheetValue.Hidden },
        )
        sheetState = state
        // Read here: the sheet's own window may not get the activity's insets.
        val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val window = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
        val belowStatusBar = window - statusBar
        val height = when (policy.height) {
            SheetPolicy.Height.FIT_CONTENT -> Modifier.heightIn(max = belowStatusBar)
            SheetPolicy.Height.TALL -> Modifier.height(min(window * SheetPolicy.TALL_FRACTION, belowStatusBar))
            SheetPolicy.Height.FULL -> Modifier.height(belowStatusBar)
        }
        ModalBottomSheet(
            onDismissRequest = onBack,
            modifier = Modifier.testTag(BottomSheetSceneStrategy.tag(key)),
            sheetState = state,
            sheetGesturesEnabled = free,
            dragHandle = null,
            properties = ModalBottomSheetProperties(
                shouldDismissOnBackPress = free,
                shouldDismissOnClickOutside = free,
            ),
        ) {
            Box(height.fillMaxWidth()) { entry.Content() }
        }
    }

    override suspend fun onRemove() {
        sheetState?.hide()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BottomSheetScene<*>) return false
        return key == other.key && entry == other.entry && policy == other.policy &&
            previousEntries == other.previousEntries
    }

    override fun hashCode(): Int = listOf(key, entry, policy, previousEntries).hashCode()

    override fun toString(): String = "BottomSheetScene(key=$key, policy=$policy)"
}
