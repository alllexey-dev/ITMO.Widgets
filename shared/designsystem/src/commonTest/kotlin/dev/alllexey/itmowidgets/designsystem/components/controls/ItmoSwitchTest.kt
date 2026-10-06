package dev.alllexey.itmowidgets.designsystem.components.controls

import androidx.compose.runtime.CompositionLocalProvider
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
import dev.alllexey.itmowidgets.designsystem.platform.ItmoHapticEvent
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.platform.LocalItmoHaptics
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.IosMetrics
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ItmoSwitchTest {
    @Test
    fun aSwitchTogglesAsASwitchInBothStylesAndOnlyIosPlaysTheToggleHaptic() = runComposeUiTest {
        var style by mutableStateOf(ItmoPlatformStyle.Material)
        var checked by mutableStateOf(true)
        val haptics = RecordingHaptics()
        val heard = mutableMapOf<ItmoPlatformStyle, List<ItmoHapticEvent>>()
        setContent {
            CompositionLocalProvider(LocalItmoHaptics provides haptics) {
                ItmoTheme(platformStyle = style) {
                    ItmoSwitch(checked, onCheckedChange = { checked = it }, Modifier.testTag(TAG))
                }
            }
        }

        ItmoPlatformStyle.entries.forEach {
            style = it
            checked = true
            haptics.events.clear()
            onNodeWithTag(TAG)
                .assert(isToggleable())
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
                .assertIsOn()
                .performClick()
                .assertIsOff()
            assertTouchTargets(it.minTouchTarget)
            heard[it] = haptics.events.toList()
        }

        assertEquals(emptyList(), heard.getValue(ItmoPlatformStyle.Material))
        assertEquals(listOf(ItmoHapticEvent.Toggle), heard.getValue(ItmoPlatformStyle.Ios))
    }

    @Test
    fun aSwitchWithoutACallbackLeavesTheToggleToItsRow() = runComposeUiTest {
        var style by mutableStateOf(ItmoPlatformStyle.Material)
        setContent {
            ItmoTheme(platformStyle = style) { ItmoSwitch(checked = true, onCheckedChange = null, Modifier.testTag(TAG)) }
        }

        ItmoPlatformStyle.entries.forEach {
            style = it
            onNodeWithTag(TAG).assert(isToggleable().not())
        }
    }

    @Test
    fun theIosSwitchHasUiKitsSize() = runComposeUiTest {
        setContent {
            ItmoTheme(platformStyle = ItmoPlatformStyle.Ios) {
                ItmoSwitch(checked = false, onCheckedChange = null, Modifier.testTag(TAG))
            }
        }

        val node = onNodeWithTag(TAG).fetchSemanticsNode()
        with(node.layoutInfo.density) {
            assertEquals(IosMetrics.switchWidth.roundToPx(), node.size.width)
            assertEquals(IosMetrics.switchHeight.roundToPx(), node.size.height)
        }
    }

    private companion object {
        const val TAG = "switch"
    }
}
