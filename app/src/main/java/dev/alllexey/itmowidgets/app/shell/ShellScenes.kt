package dev.alllexey.itmowidgets.app.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
 * Navigation 3 1.2 has no first-party sheet scene (SP-14): an entry with [bottomSheet] metadata becomes an
 * [OverlayScene] in a material3 [ModalBottomSheet] above the entries below it, which stay composed. The sheet is the
 * container (`ItmoBottomSheetFragment`'s): expanded without a collapsed step, as tall as its content up to
 * [MAX_HEIGHT_FRACTION] of the window or always that tall ([SheetPolicy.Height.TALL]); the body (a `SheetScaffold`)
 * draws its own handle.
 */
class BottomSheetSceneStrategy<T : Any> : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val last = entries.lastOrNull() ?: return null
        val policy = last.metadata[SheetPolicyKey] ?: return null
        val below = entries.dropLast(1)
        return BottomSheetScene(last.contentKey, last, below, policy, onBack)
    }

    companion object {
        const val MAX_HEIGHT_FRACTION = 0.9f

        /** The test tag of the sheet holding the entry with [contentKey]. */
        fun tag(contentKey: Any): String = "sheet:$contentKey"
    }
}

/**
 * A [SheetPolicy.Dismissal.FORM] sheet ignores drag, Back and a tap outside: Back reaches the entry's own handler on
 * the sheet window, which asks before discarding. Equality over key, entry, policy and the entries below, because
 * `NavDisplay` keeps the first overlay instance per key (SP-14).
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
        val maxHeight = with(LocalDensity.current) {
            (LocalWindowInfo.current.containerSize.height * BottomSheetSceneStrategy.MAX_HEIGHT_FRACTION).toDp()
        }
        val height = when (policy.height) {
            SheetPolicy.Height.FIT_CONTENT -> Modifier.heightIn(max = maxHeight)
            SheetPolicy.Height.TALL -> Modifier.heightIn(min = maxHeight, max = maxHeight)
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

/**
 * Everything that is not a sheet or a dialog, the selected tab's root and the overlay screens above it, is one scene
 * with a fixed key: the shell lays the layers out itself ([ShellChrome]), so `NavDisplay` never swaps scenes or
 * animates between them, and the tab content stays composed with its bounds under an overlay. The scene has no
 * previous entries, so `NavDisplay`'s own Back handler stays off and the shell's handles Back.
 */
class ShellSceneStrategy<T : Any>(
    private val layers: @Composable (List<NavEntry<T>>) -> Unit,
) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T> =
        ShellScene(entries, layers)
}

private class ShellScene<T : Any>(
    override val entries: List<NavEntry<T>>,
    private val layers: @Composable (List<NavEntry<T>>) -> Unit,
) : Scene<T> {
    override val key: Any get() = ShellScene::class
    override val previousEntries: List<NavEntry<T>> get() = emptyList()
    override val content: @Composable () -> Unit = { layers(entries) }

    override fun equals(other: Any?): Boolean =
        this === other || (other is ShellScene<*> && entries == other.entries && layers == other.layers)

    override fun hashCode(): Int = 31 * entries.hashCode() + layers.hashCode()
}
