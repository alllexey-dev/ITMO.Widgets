package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.IosMetrics
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ItmoWavyProgressTest {
    @Test
    fun iosIsAPlainBarOfUiKitsHeightAndARingThatReportTheirValueInBothSwitchStates() = runComposeUiTest {
        var expressive by mutableStateOf(false)
        var progress by mutableStateOf(0.6f)
        setContent {
            ItmoTheme(expressive = expressive, platformStyle = ItmoPlatformStyle.Ios) {
                Column {
                    ItmoWavyProgress({ progress }, Modifier.fillMaxWidth().testTag(LINEAR))
                    ItmoWavyProgress({ progress }, Modifier.testTag(CIRCULAR), ItmoWavyProgressShape.Circular)
                }
            }
        }

        listOf(false, true).forEach { on ->
            expressive = on
            onNodeWithTag(LINEAR)
                .assert(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.6f, 0f..1f)))
                .assertHeightIsEqualTo(IosMetrics.progressBarHeight)
            onNodeWithTag(CIRCULAR)
                .assert(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.6f, 0f..1f)))
                .assertHeightIsEqualTo(40.dp)
        }

        progress = 1.4f

        onNodeWithTag(LINEAR).assert(hasProgressBarRangeInfo(ProgressBarRangeInfo(1f, 0f..1f)))
    }

    private companion object {
        const val LINEAR = "linear"
        const val CIRCULAR = "circular"
    }
}
