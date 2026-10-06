package dev.alllexey.itmowidgets.designsystem.components.controls

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ItmoActivityIndicatorTest {
    @Test
    fun bothSizesAreIndeterminateProgressOfTheirSizeInBothStyles() = runComposeUiTest {
        var style by mutableStateOf(ItmoPlatformStyle.Material)
        setContent {
            ItmoTheme(platformStyle = style) {
                Row {
                    ItmoActivityIndicatorSize.entries.forEach {
                        ItmoActivityIndicator(Modifier.testTag(it.name), size = it)
                    }
                }
            }
        }

        ItmoPlatformStyle.entries.forEach {
            style = it
            ItmoActivityIndicatorSize.entries.forEach { size ->
                val node = onNodeWithTag(size.name)
                    .assert(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
                    .fetchSemanticsNode()
                with(node.layoutInfo.density) {
                    assertEquals(size.size.roundToPx(), node.size.width, "$it $size")
                    assertEquals(size.size.roundToPx(), node.size.height, "$it $size")
                }
            }
        }
    }

    @Test
    fun theSpinnerHasUiKitsSizes() {
        assertEquals(20f, ItmoActivityIndicatorSize.Medium.size.value)
        assertEquals(37f, ItmoActivityIndicatorSize.Large.size.value)
    }
}
