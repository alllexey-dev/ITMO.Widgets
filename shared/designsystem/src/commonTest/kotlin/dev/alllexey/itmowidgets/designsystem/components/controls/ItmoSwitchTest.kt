package dev.alllexey.itmowidgets.designsystem.components.controls

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ItmoSwitchTest {
    @Test
    fun aSwitchTogglesAsASwitch() = runComposeUiTest {
        var checked by mutableStateOf(true)
        setContent {
            ItmoTheme {
                ItmoSwitch(checked, onCheckedChange = { checked = it }, Modifier.testTag(TAG))
            }
        }

        onNodeWithTag(TAG)
            .assert(isToggleable())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assertIsOn()
            .performClick()
            .assertIsOff()
        assertTouchTargets()
    }

    @Test
    fun aSwitchWithoutACallbackLeavesTheToggleToItsRow() = runComposeUiTest {
        setContent {
            ItmoTheme { ItmoSwitch(checked = true, onCheckedChange = null, Modifier.testTag(TAG)) }
        }

        onNodeWithTag(TAG).assert(isToggleable().not())
    }

    private companion object {
        const val TAG = "switch"
    }
}
