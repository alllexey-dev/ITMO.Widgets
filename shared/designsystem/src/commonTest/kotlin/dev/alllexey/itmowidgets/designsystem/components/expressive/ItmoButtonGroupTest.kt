package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.designsystem.components.controls.RecordingHaptics
import dev.alllexey.itmowidgets.designsystem.platform.ItmoHapticEvent
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.platform.LocalItmoHaptics
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ItmoButtonGroupTest {
    @Test
    fun choosingAnotherOptionReportsItAndTheSelectedOneStaysInBothStates() = runComposeUiTest {
        var on by mutableStateOf(false)
        val chosen = mutableListOf<Int>()
        setContent {
            ItmoTheme(expressive = on) {
                ItmoButtonGroup(OPTIONS, selectedIndex = 0, onSelect = { chosen += it })
            }
        }

        listOf(false, true).forEach {
            on = it
            onNodeWithText(OPTIONS[0]).performClick()
            onNodeWithText(OPTIONS[2]).performClick()
            assertTouchTargets()
        }

        assertEquals(listOf(2, 2), chosen)
    }

    @Test
    fun theIosSegmentedControlKeepsTheSelectionSemanticsAndPlaysASelectionHaptic() = runComposeUiTest {
        var style by mutableStateOf(ItmoPlatformStyle.Material)
        var selected by mutableStateOf(0)
        val haptics = RecordingHaptics()
        val heard = mutableMapOf<ItmoPlatformStyle, List<ItmoHapticEvent>>()
        val roles = mutableMapOf<ItmoPlatformStyle, Role?>()
        setContent {
            CompositionLocalProvider(LocalItmoHaptics provides haptics) {
                ItmoTheme(platformStyle = style) {
                    ItmoButtonGroup(OPTIONS, selectedIndex = selected, onSelect = { selected = it })
                }
            }
        }

        ItmoPlatformStyle.entries.forEach {
            style = it
            selected = 0
            haptics.events.clear()
            onNodeWithText(OPTIONS[0]).assertIsSelected()
            onNodeWithText(OPTIONS[0]).performClick()
            onNodeWithText(OPTIONS[2]).assertIsNotSelected().performClick().assertIsSelected()
            onNodeWithText(OPTIONS[0]).assertIsNotSelected()
            assertTouchTargets(it.minTouchTarget)
            roles[it] = onNodeWithText(OPTIONS[1]).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role)
            heard[it] = haptics.events.toList()
        }

        assertEquals(2, selected)
        assertEquals(roles.getValue(ItmoPlatformStyle.Material), roles.getValue(ItmoPlatformStyle.Ios))
        assertEquals(emptyList(), heard.getValue(ItmoPlatformStyle.Material))
        assertEquals(listOf(ItmoHapticEvent.Selection), heard.getValue(ItmoPlatformStyle.Ios))
    }

    @Test
    fun groupHoldsTwoToFourOptionsWithOneSelected() {
        assertFailsWith<IllegalArgumentException> {
            runComposeUiTest {
                setContent { ItmoTheme { ItmoButtonGroup(listOf("Одна"), selectedIndex = 0, onSelect = {}) } }
            }
        }
        assertFailsWith<IllegalArgumentException> {
            runComposeUiTest {
                setContent { ItmoTheme { ItmoButtonGroup(OPTIONS, selectedIndex = 3, onSelect = {}) } }
            }
        }
    }

    private companion object {
        val OPTIONS = listOf("Записи", "Баллы", "Посещения")
    }
}
