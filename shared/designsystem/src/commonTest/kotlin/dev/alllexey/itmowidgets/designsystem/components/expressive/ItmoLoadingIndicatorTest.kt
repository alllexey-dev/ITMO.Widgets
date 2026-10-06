package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.IosMetrics
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ItmoLoadingIndicatorTest {
    @Test
    fun iosDrawsTheLargeActivityIndicatorInBothFormsAndBothSwitchStates() = runComposeUiTest {
        var expressive by mutableStateOf(false)
        setContent {
            ItmoTheme(expressive = expressive, platformStyle = ItmoPlatformStyle.Ios) {
                Row {
                    ItmoLoadingIndicator(Modifier.testTag(PLAIN))
                    ItmoLoadingIndicator(Modifier.testTag(CONTAINED), contained = true)
                }
            }
        }

        listOf(false, true).forEach { on ->
            expressive = on
            listOf(PLAIN, CONTAINED).forEach {
                onNodeWithTag(it)
                    .assert(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
                    .assertWidthIsEqualTo(IosMetrics.activityIndicatorLarge)
                    .assertHeightIsEqualTo(IosMetrics.activityIndicatorLarge)
            }
        }
    }

    private companion object {
        const val PLAIN = "plain"
        const val CONTAINED = "contained"
    }
}
