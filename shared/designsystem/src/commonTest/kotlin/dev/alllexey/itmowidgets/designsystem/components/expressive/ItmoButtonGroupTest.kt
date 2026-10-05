package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
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
