package dev.alllexey.itmowidgets.testkit

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText

/** [ComposeUiTest.awaitText][awaitText] for a JUnit4 Compose rule. */
fun ComposeTestRule.awaitText(
    text: String,
    useUnmergedTree: Boolean = false,
    timeoutMillis: Long = RESOURCE_LOAD_TIMEOUT_MS,
): SemanticsNodeInteraction {
    waitUntil(textShown(text), timeoutMillis) { hasNodeWithText(text, useUnmergedTree) }
    return onNodeWithText(text, useUnmergedTree = useUnmergedTree)
}
