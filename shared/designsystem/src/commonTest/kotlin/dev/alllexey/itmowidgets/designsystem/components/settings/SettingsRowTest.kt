package dev.alllexey.itmowidgets.designsystem.components.settings

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsRowTest {
    @Test
    fun compactRowsAreOneTouchTargetHigh() = runComposeUiTest {
        setContent {
            // Material's 48 dp rows; the iOS rows and switch come with DS-IOS-02 and DS-IOS-03.
            ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
                SettingsGroup {
                    row { SettingsNavigationRow(NAVIGATION, onClick = {}) }
                    row { SettingsToggleRow(TOGGLE, checked = true, onCheckedChange = {}) }
                }
            }
        }

        onNodeWithText(NAVIGATION).assertHeightIsEqualTo(48.dp)
        onNodeWithText(TOGGLE).assertHeightIsEqualTo(48.dp)
        assertTouchTargets()
    }

    @Test
    fun defaultRowsAreAtLeast56WithA16Inset() = runComposeUiTest {
        setContent {
            ItmoTheme {
                SettingsGroup(density = SettingsDensity.Default) {
                    row { SettingsNavigationRow(NAVIGATION, onClick = {}) }
                }
            }
        }

        onNodeWithText(NAVIGATION).assertHeightIsEqualTo(56.dp)
        val title = onNodeWithText(NAVIGATION, useUnmergedTree = true).getBoundsInRoot()
        assertEquals(16.dp, title.left)
    }

    @Test
    fun theWholeToggleRowIsOneSwitch() = runComposeUiTest {
        val changes = mutableListOf<Boolean>()
        setContent { ItmoTheme { SettingsToggleRow(TOGGLE, checked = false, onCheckedChange = { changes += it }) } }

        onNodeWithText(TOGGLE)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assertIsOff()
            .performClick()

        assertEquals(listOf(true), changes)
    }

    @Test
    fun aToggleWithAnUnknownValueShowsNoStateAndIgnoresTaps() = runComposeUiTest {
        val changes = mutableListOf<Boolean>()
        setContent { ItmoTheme { SettingsToggleRow(TOGGLE, checked = null, onCheckedChange = { changes += it }) } }

        onNodeWithText(TOGGLE)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ToggleableState))
            .assertIsNotEnabled()
            .assertHasNoClickAction()

        assertEquals(emptyList(), changes)
    }

    @Test
    fun aDisabledRowIsInert() = runComposeUiTest {
        var clicks = 0
        setContent { ItmoTheme { SettingsNavigationRow(NAVIGATION, onClick = { clicks++ }, enabled = false) } }

        onNodeWithText(NAVIGATION).assertIsNotEnabled().performClick()

        assertEquals(0, clicks)
    }

    @Test
    fun aSingleChoiceRowIsARadioButtonWithItsSelection() = runComposeUiTest {
        var picks = 0
        setContent {
            ItmoTheme {
                SettingsGroup {
                    row { SettingsSelectionRow(CHOSEN, selected = true, onSelect = { picks++ }) }
                    row { SettingsSelectionRow(OTHER, selected = false, onSelect = { picks++ }) }
                }
            }
        }

        onNodeWithText(CHOSEN)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsSelected()
        onNodeWithText(OTHER).assertIsNotSelected().performClick()

        assertEquals(1, picks)
        assertTouchTargets()
    }

    @Test
    fun aMultipleChoiceRowIsACheckbox() = runComposeUiTest {
        setContent {
            ItmoTheme {
                SettingsSelectionRow(CHOSEN, selected = true, onSelect = {}, mode = SelectionMode.Multiple)
            }
        }

        onNodeWithText(CHOSEN)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assertIsOn()
            .assertHasClickAction()
    }

    @Test
    fun aClosedRowNeverLooksSelectable() = runComposeUiTest {
        setContent {
            ItmoTheme {
                SettingsSelectionRow(CLOSED, selected = true, onSelect = null, Modifier, mode = SelectionMode.Multiple)
            }
        }

        onNodeWithText(CLOSED)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ToggleableState))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
            .assertHasNoClickAction()
    }

    @Test
    fun theGroupTitleIsAHeadingAndAnInfoRowIsOneItem() = runComposeUiTest {
        setContent {
            ItmoTheme {
                SettingsGroup(title = GROUP, footer = { SettingsGroupFooter(FOOTER) }) {
                    row { SettingsInfoRow(VERSION, value = VERSION_VALUE) }
                }
            }
        }

        onNodeWithText(GROUP).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        val info = onNodeWithText(VERSION).assertHasNoClickAction().fetchSemanticsNode()
        assertEquals(info.id, onNodeWithText(VERSION_VALUE).fetchSemanticsNode().id, "an info row is read once")
        onNodeWithText(FOOTER).assertHasNoClickAction()
    }

    private companion object {
        const val GROUP = "Расписание"
        const val FOOTER = "Настройки применяются ко всем виджетам расписания."
        const val NAVIGATION = "QR-код"
        const val TOGGLE = "Фильтр по преподавателю"
        const val CHOSEN = "Автоматически"
        const val OTHER = "Строка 14"
        const val CLOSED = "Иванов Иван"
        const val VERSION = "Версия"
        const val VERSION_VALUE = "2.3"
    }
}
