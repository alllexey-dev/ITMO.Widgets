package dev.alllexey.itmowidgets.designsystem.components.rows

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.components.controls.RecordingHaptics
import dev.alllexey.itmowidgets.designsystem.components.groups.GroupPosition
import dev.alllexey.itmowidgets.designsystem.components.groups.connectedGroupItem
import dev.alllexey.itmowidgets.designsystem.components.settings.SelectionMode
import dev.alllexey.itmowidgets.designsystem.platform.ItmoHapticEvent
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.platform.LocalItmoHaptics
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertNoTextOverflow
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class UserRowTest {
    @Test
    fun aRowThatOpensIsOneTargetReadOnce() = runComposeUiTest {
        var opened = 0
        setContent {
            ItmoTheme { UserRow(NAME, pictureUrl = null, subtitle = GROUP, status = STATUS, onClick = { opened++ }) }
        }

        val row = onNodeWithText(NAME).assertHasClickAction()
        val id = row.fetchSemanticsNode().id
        assertEquals(id, onNodeWithText(GROUP).fetchSemanticsNode().id)
        assertEquals(id, onNodeWithText(STATUS).fetchSemanticsNode().id)
        row.performClick()

        assertEquals(1, opened)
        assertTouchTargets()
    }

    @Test
    fun actionsAreOwnTargetsAndInertWhileBusy() = runComposeUiTest {
        val taps = mutableListOf<String>()
        setContent {
            ItmoTheme {
                UserRow(
                    NAME,
                    pictureUrl = null,
                    primaryAction = UserRowAction(ACCEPT) { taps += ACCEPT },
                    secondaryAction = UserRowAction(REJECT) { taps += REJECT },
                )
            }
        }

        onNodeWithText(REJECT).performClick()
        onNodeWithText(ACCEPT).performClick()

        assertEquals(listOf(REJECT, ACCEPT), taps)
        onNodeWithText(NAME).assertHasNoClickAction()
        assertTouchTargets()
    }

    @Test
    fun aBusyRowIgnoresItsActions() = runComposeUiTest {
        var taps = 0
        setContent {
            ItmoTheme {
                UserRow(NAME, pictureUrl = null, primaryAction = UserRowAction(ACCEPT) { taps++ }, busy = true)
            }
        }

        onNodeWithText(ACCEPT).assertIsNotEnabled().performClick()

        assertEquals(0, taps)
    }

    @Test
    fun selectionRowsReadTheirStateAndAClosedOneIsInert() = runComposeUiTest {
        var picks = 0
        setContent {
            ItmoTheme {
                Column {
                    UserSelectionRow(NAME, pictureUrl = null, selected = true, onSelect = { picks++ })
                    UserSelectionRow(
                        OTHER,
                        pictureUrl = null,
                        selected = false,
                        onSelect = { picks++ },
                        mode = SelectionMode.Single,
                    )
                    UserSelectionRow(CLOSED, pictureUrl = null, selected = true, onSelect = null)
                }
            }
        }

        onNodeWithText(NAME).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)).assertIsOn()
        onNodeWithText(OTHER)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsNotSelected()
            .performClick()
        onNodeWithText(CLOSED)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ToggleableState))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
            .assertHasNoClickAction()

        assertEquals(1, picks)
        assertTouchTargets()
    }

    @Test
    fun rowsThatOpenThePersonPickOnTapOpenOnLongPressAndAClosedOneOpensOnTap() = runComposeUiTest {
        val events = mutableListOf<String>()
        setContent {
            ItmoTheme {
                Column {
                    UserSelectionRow(
                        NAME,
                        pictureUrl = null,
                        selected = true,
                        onSelect = { events += "pick $NAME" },
                        mode = SelectionMode.Single,
                        onOpen = { events += "open $NAME" },
                    )
                    UserSelectionRow(
                        OTHER,
                        pictureUrl = null,
                        selected = false,
                        onSelect = { events += "pick $OTHER" },
                        onOpen = { events += "open $OTHER" },
                    )
                    UserSelectionRow(
                        CLOSED,
                        pictureUrl = null,
                        selected = true,
                        onSelect = null,
                        status = GROUP,
                        onOpen = { events += "open $CLOSED" },
                    )
                }
            }
        }

        onNodeWithText(NAME)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsSelected()
            .performClick()
            .performTouchInput { longClick() }
        onNodeWithText(OTHER)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
            .performClick()
        onNodeWithText(CLOSED)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ToggleableState))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
            .assertHasClickAction()
            .performClick()
            .performTouchInput { longClick() }

        assertEquals(
            listOf("pick $NAME", "open $NAME", "pick $OTHER", "open $CLOSED", "open $CLOSED"),
            events,
        )
        assertTouchTargets()
    }

    @Test
    fun aLongNameWrapsAndOnlyTheSubtitleIsShortened() = runComposeUiTest {
        setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, LARGE_FONT)) {
                ItmoTheme {
                    UserRow(
                        LONG_NAME,
                        pictureUrl = null,
                        modifier = Modifier.width(NARROW_CARD_DP.dp),
                        subtitle = LONG_SUBTITLE,
                        onClick = {},
                        primaryAction = UserRowAction(ACCEPT) {},
                    )
                }
            }
        }

        assertNoTextOverflow(allowed = hasText(LONG_SUBTITLE))
        assertTouchTargets()
    }

    @Test
    fun iosRowsKeepTheirSemanticsAndPlayThePickHaptics() = runComposeUiTest {
        val haptics = RecordingHaptics()
        var opened = 0
        var picks = 0
        setContent {
            CompositionLocalProvider(LocalItmoHaptics provides haptics) {
                ItmoTheme(platformStyle = ItmoPlatformStyle.Ios) {
                    Column {
                        UserRow(
                            NAME,
                            pictureUrl = null,
                            Modifier.connectedGroupItem(GroupPosition.First),
                            subtitle = GROUP,
                            onClick = { opened++ },
                        )
                        UserSelectionRow(
                            OTHER,
                            pictureUrl = null,
                            selected = false,
                            onSelect = { picks++ },
                            Modifier.connectedGroupItem(GroupPosition.Middle),
                        )
                        UserSelectionRow(
                            CLOSED,
                            pictureUrl = null,
                            selected = true,
                            onSelect = null,
                            Modifier.connectedGroupItem(GroupPosition.Last),
                        )
                    }
                }
            }
        }

        onNodeWithText(NAME).assertHasClickAction().performClick()
        onNodeWithText(OTHER)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .performClick()
        onNodeWithText(CLOSED).assertHasNoClickAction()

        assertEquals(1, opened)
        assertEquals(1, picks)
        assertEquals(listOf(ItmoHapticEvent.Toggle), haptics.events)
        assertTouchTargets(ItmoPlatformStyle.Ios.minTouchTarget)
    }

    private companion object {
        const val NAME = "Иванов Иван"
        const val OTHER = "Петров Алексей"
        const val CLOSED = "Смирнова Анна"
        const val GROUP = "P3119"
        const val STATUS = "Заявка отправлена"
        const val ACCEPT = "Принять"
        const val REJECT = "Отклонить"
        const val LONG_NAME = "Преображенская Александра Вячеславовна"
        const val LONG_SUBTITLE = "Математический анализ и дифференциальные уравнения в частных производных"
        const val LARGE_FONT = 1.3f

        /** A 320 dp screen less its 16 dp margins: a card of people. */
        const val NARROW_CARD_DP = 288
    }
}
