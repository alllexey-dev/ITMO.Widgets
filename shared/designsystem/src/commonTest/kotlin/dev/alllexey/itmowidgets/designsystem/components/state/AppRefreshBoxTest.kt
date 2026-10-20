package dev.alllexey.itmowidgets.designsystem.components.state

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class AppRefreshBoxTest {
    @Test
    fun pullAsksForARefreshOnce() = runComposeUiTest {
        var refreshes = 0
        setContent {
            ItmoTheme {
                AppRefreshBox(refreshing = false, onRefresh = { refreshes++ }, Modifier.testTag(TAG)) {
                    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
                }
            }
        }

        onNodeWithTag(TAG).performTouchInput { swipeDown() }
        waitForIdle()

        assertEquals(1, refreshes)
    }

    /** `refreshing` is true only for a refresh the user asked for; an automatic one updates the content silently. */
    @Test
    fun indicatorShowsOnlyForAUserRefreshInBothSwitchStates() {
        listOf(false, true).forEach { expressive ->
            runComposeUiTest {
                var refreshing by mutableStateOf(false)
                var content by mutableStateOf("old")
                setContent {
                    ItmoTheme(expressive = expressive) {
                        AppRefreshBox(refreshing = refreshing, onRefresh = {}) {
                            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { Text(content) }
                        }
                    }
                }
                val indicator = hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)
                val case = "expressive = $expressive"
                assertEquals(0, onAllNodes(indicator).fetchSemanticsNodes().size, case)

                content = "new"
                onNodeWithText("new").assertExists(case)
                assertEquals(0, onAllNodes(indicator).fetchSemanticsNodes().size, case)

                refreshing = true
                assertEquals(1, onAllNodes(indicator).fetchSemanticsNodes().size, case)

                refreshing = false
                assertEquals(0, onAllNodes(indicator).fetchSemanticsNodes().size, case)
            }
        }
    }

    @Test
    fun theContentStaysInPlaceWhileRefreshing() = runComposeUiTest {
        var refreshing by mutableStateOf(true)
        setContent {
            ItmoTheme {
                AppRefreshBox(refreshing = refreshing, onRefresh = {}, Modifier.testTag(TAG)) {
                    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag(CONTENT))
                }
            }
        }
        val contentOffset: () -> Dp = {
            val box = onNodeWithTag(TAG).getUnclippedBoundsInRoot()
            onNodeWithTag(CONTENT).getUnclippedBoundsInRoot().top - box.top
        }

        assertEquals(0f, contentOffset().value, PIXEL)

        refreshing = false
        waitForIdle()

        assertEquals(0f, contentOffset().value, PIXEL)
    }

    private companion object {
        const val TAG = "refresh"
        const val CONTENT = "content"

        /** The offset is laid out in whole pixels. */
        const val PIXEL = 1f
    }
}
