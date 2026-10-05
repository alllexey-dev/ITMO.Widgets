package dev.alllexey.itmowidgets.designsystem.components.state

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class AppRefreshBoxTest {
    @Test
    fun pullAsksForARefresh() = runComposeUiTest {
        var refreshes = 0
        setContent {
            ItmoTheme {
                AppRefreshBox(refreshing = false, onRefresh = { refreshes++ }, Modifier.testTag(TAG)) {
                    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
                }
            }
        }

        onNodeWithTag(TAG).performTouchInput { swipeDown() }

        assertEquals(1, refreshes)
    }

    @Test
    fun indicatorShowsOnlyWhileRefreshing() = runComposeUiTest {
        var refreshing by mutableStateOf(false)
        setContent {
            ItmoTheme {
                AppRefreshBox(refreshing = refreshing, onRefresh = {}) {
                    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
                }
            }
        }
        onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertDoesNotExist()

        refreshing = true

        onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
    }

    private companion object {
        const val TAG = "refresh"
    }
}
