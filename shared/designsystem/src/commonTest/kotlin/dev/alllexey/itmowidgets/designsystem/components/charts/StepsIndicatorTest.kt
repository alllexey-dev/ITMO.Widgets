package dev.alllexey.itmowidgets.designsystem.components.charts

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.IosMetrics
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class StepsIndicatorTest {
    @Test
    fun iosIsOneNodeOfEqualPageControlDots() = runComposeUiTest {
        setContent {
            ItmoTheme(platformStyle = ItmoPlatformStyle.Ios) {
                StepsIndicator(count = 4, current = 1, contentDescription = STEP, Modifier.testTag(TAG))
            }
        }

        // Four equal dots and three gaps, each laid out in whole pixels: `UIPageControl.size(forNumberOfPages: 4)`
        // without its 14 pt side insets.
        val node = onNodeWithTag(TAG).assertContentDescriptionEquals(STEP).fetchSemanticsNode()
        with(node.layoutInfo.density) {
            val dot = IosMetrics.pageIndicatorDotSize.roundToPx()
            assertEquals(dot * 4 + IosMetrics.pageIndicatorGap.roundToPx() * 3, node.size.width)
            assertEquals(dot, node.size.height)
        }
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)).assertCountEquals(1)
    }

    private companion object {
        const val TAG = "steps"
        const val STEP = "Шаг 2 из 4"
    }
}
