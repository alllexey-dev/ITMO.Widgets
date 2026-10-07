package dev.alllexey.itmowidgets.testkit

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText

/*
 * Compose resources' suspend `getString` (and `UiText.resolve()` over it) loads through an `AsyncCache` on the
 * resources' own `CoroutineScope(SupervisorJob())`, i.e. `Dispatchers.Default`, which Compose idling does not track.
 * So `waitForIdle()` and the implicit sync of `onNode*` can return before a snackbar or label built from such a
 * string exists, and a test that asserts on it right after an action fails under load. These helpers wait for the
 * text with a bound sized for a loaded machine; the assertions that follow stay exact. `stringResource` in
 * composition loads blocking on the host and needs none of this.
 */

/** How long a host test waits for a string that Compose resources load off the tracked dispatchers. */
const val RESOURCE_LOAD_TIMEOUT_MS: Long = 5_000L

/**
 * Waits until a node with exactly [text] exists, for at most [timeoutMillis], and returns it for the assertion; a
 * text that never appears fails with Compose's timeout naming it.
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.awaitText(
    text: String,
    useUnmergedTree: Boolean = false,
    timeoutMillis: Long = RESOURCE_LOAD_TIMEOUT_MS,
): SemanticsNodeInteraction {
    waitUntil(textShown(text), timeoutMillis) { hasNodeWithText(text, useUnmergedTree) }
    return onNodeWithText(text, useUnmergedTree = useUnmergedTree)
}

/**
 * Waits until [value] is non-null, for at most [timeoutMillis], and returns it: for a resource-loaded value read from
 * state rather than from the semantics tree, such as `SnackbarHostState.currentSnackbarData`.
 */
@OptIn(ExperimentalTestApi::class)
fun <T : Any> ComposeUiTest.awaitResource(
    description: String,
    timeoutMillis: Long = RESOURCE_LOAD_TIMEOUT_MS,
    value: () -> T?,
): T {
    var loaded: T? = null
    waitUntil(description, timeoutMillis) {
        loaded = value()
        loaded != null
    }
    return checkNotNull(loaded) { description }
}

internal fun textShown(text: String): String = "\"$text\" is shown"

internal fun SemanticsNodeInteractionsProvider.hasNodeWithText(text: String, useUnmergedTree: Boolean): Boolean =
    onAllNodesWithText(text, useUnmergedTree = useUnmergedTree).fetchSemanticsNodes().isNotEmpty()
