package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicatorDefaults
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
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class, ExperimentalMaterial3ExpressiveApi::class)
@RunWith(RobolectricTestRunner::class)
class ItmoLoadingIndicatorTest {
    @Test
    fun expressiveMaterialDrawsTheMorphingIndicatorAtMaterialsSizeAsAnIndeterminateProgress() = runComposeUiTest {
        setContent {
            ItmoTheme(expressive = true) {
                Row {
                    ItmoLoadingIndicator(Modifier.testTag(PLAIN))
                    ItmoLoadingIndicator(Modifier.testTag(CONTAINED), contained = true)
                }
            }
        }

        listOf(PLAIN, CONTAINED).forEach {
            onNodeWithTag(it)
                .assert(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
                .assertWidthIsEqualTo(LoadingIndicatorDefaults.ContainerWidth)
                .assertHeightIsEqualTo(LoadingIndicatorDefaults.ContainerHeight)
        }
    }

    private companion object {
        const val PLAIN = "plain"
        const val CONTAINED = "contained"
    }
}
