package dev.alllexey.itmowidgets.designsystem.components.dialogs

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
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
import kotlin.test.assertTrue

/** Every behaviour holds in both platform styles: the iOS alerts keep the Material dialogs' contract. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class DialogsTest {
    @Test
    fun confirmReportsEitherChoiceWithoutClosingItself() = runComposeUiTest {
        var style by mutableStateOf(ItmoPlatformStyle.Material)
        val events = mutableListOf<String>()
        setContent {
            ItmoTheme(platformStyle = style) {
                ConfirmDialog(
                    title = TITLE,
                    text = TEXT,
                    confirmLabel = CONFIRM,
                    dismissLabel = CANCEL,
                    onConfirm = { events += CONFIRM },
                    onDismiss = { events += CANCEL },
                )
            }
        }

        ItmoPlatformStyle.entries.forEach {
            style = it
            onNodeWithText(TITLE).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            onNodeWithText(TEXT).assertIsDisplayed()
            onNodeWithText(CONFIRM).performClick()
            onNodeWithText(CANCEL).performClick()
            onNodeWithText(TITLE).assertIsDisplayed()
            assertTouchTargets(it.minTouchTarget)
        }

        assertEquals(listOf(CONFIRM, CANCEL, CONFIRM, CANCEL), events)
    }

    @Test
    fun aDestructiveConfirmWarnsOnlyOnIos() = runComposeUiTest {
        var style by mutableStateOf(ItmoPlatformStyle.Material)
        val haptics = RecordingHaptics()
        val heard = mutableMapOf<ItmoPlatformStyle, List<ItmoHapticEvent>>()
        var confirms = 0
        setContent {
            CompositionLocalProvider(LocalItmoHaptics provides haptics) {
                ItmoTheme(platformStyle = style) {
                    ConfirmDialog(
                        title = TITLE,
                        confirmLabel = CONFIRM,
                        dismissLabel = CANCEL,
                        onConfirm = { confirms++ },
                        onDismiss = {},
                        destructive = true,
                    )
                }
            }
        }

        ItmoPlatformStyle.entries.forEach {
            style = it
            haptics.events.clear()
            onNodeWithText(CANCEL).performClick()
            onNodeWithText(CONFIRM).performClick()
            heard[it] = haptics.events.toList()
        }

        assertEquals(2, confirms)
        assertEquals(emptyList(), heard.getValue(ItmoPlatformStyle.Material))
        assertEquals(listOf(ItmoHapticEvent.Warning), heard.getValue(ItmoPlatformStyle.Ios))
    }

    @Test
    fun anUntitledConfirmShowsItsTextAndContentWithoutAHeading() = runComposeUiTest {
        var style by mutableStateOf(ItmoPlatformStyle.Material)
        val events = mutableListOf<String>()
        setContent {
            ItmoTheme(platformStyle = style) {
                ConfirmDialog(
                    title = null,
                    text = TEXT,
                    confirmLabel = CONFIRM,
                    dismissLabel = CANCEL,
                    onConfirm = { events += CONFIRM },
                    onDismiss = { events += CANCEL },
                ) {
                    TextButton(onClick = { events += OPTION }) { Text(OPTION) }
                }
            }
        }

        ItmoPlatformStyle.entries.forEach {
            style = it
            onNodeWithText(TEXT).assertIsDisplayed()
                .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Heading))
            assertTrue(
                onNodeWithText(TEXT).getBoundsInRoot().bottom <= onNodeWithText(OPTION).getBoundsInRoot().top,
                "the content sits below the text",
            )
            onNodeWithText(OPTION).performClick()
            onNodeWithText(CONFIRM).performClick()
            assertTouchTargets(it.minTouchTarget)
        }

        assertEquals(listOf(OPTION, CONFIRM, OPTION, CONFIRM), events)
    }

    @Test
    fun infoHasOneButtonThatOnlyReportsTheDismissal() = runComposeUiTest {
        var style by mutableStateOf(ItmoPlatformStyle.Material)
        var title by mutableStateOf<String?>(TITLE)
        var dismissals = 0
        setContent {
            ItmoTheme(platformStyle = style) {
                InfoDialog(title = title, text = TEXT, buttonLabel = OK, onDismiss = { dismissals++ })
            }
        }

        ItmoPlatformStyle.entries.forEach {
            style = it
            title = TITLE
            onNodeWithText(TITLE).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            onNodeWithText(TEXT).assertIsDisplayed()
            onNodeWithText(OK).performClick()
            onNodeWithText(CANCEL).assertDoesNotExist()
            assertTouchTargets(it.minTouchTarget)
            title = null
            onNodeWithText(TITLE).assertDoesNotExist()
            onNodeWithText(TEXT).assertIsDisplayed()
        }

        assertEquals(2, dismissals)
    }

    @Test
    fun singleChoiceRowsAreRadioButtonsWithTheCurrentOneSelected() = runComposeUiTest {
        var style by mutableStateOf(ItmoPlatformStyle.Material)
        val picks = mutableListOf<Int>()
        setContent {
            ItmoTheme(platformStyle = style) {
                ChoiceDialog(TITLE, OPTIONS, selectedIndex = 1, onSelect = { picks += it }, onDismiss = {}, dismissLabel = CANCEL)
            }
        }

        ItmoPlatformStyle.entries.forEach {
            style = it
            onNodeWithText(OPTIONS[1]).assertIsSelected()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            onNodeWithText(OPTIONS[0]).assertIsNotSelected().performClick()
            onNodeWithText(OPTIONS[2]).performClick()
            onNodeWithText(CANCEL).assertIsDisplayed()
            assertTouchTargets(it.minTouchTarget)
        }

        assertEquals(listOf(0, 2, 0, 2), picks)
    }

    @Test
    fun plainItemsAreButtonsAndTheCancelButtonIsOptional() = runComposeUiTest {
        var style by mutableStateOf(ItmoPlatformStyle.Material)
        val picks = mutableListOf<Int>()
        setContent {
            ItmoTheme(platformStyle = style) {
                ChoiceDialog(TITLE, OPTIONS, selectedIndex = null, onSelect = { picks += it }, onDismiss = {})
            }
        }

        ItmoPlatformStyle.entries.forEach {
            style = it
            onNodeWithText(OPTIONS[2]).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .performClick()
            onNodeWithText(CANCEL).assertDoesNotExist()
            assertTouchTargets(it.minTouchTarget)
        }

        assertEquals(listOf(2, 2), picks)
    }

    @Test
    fun reportSendNeedsAReasonAndShowsTheError() = runComposeUiTest {
        var style by mutableStateOf(ItmoPlatformStyle.Material)
        var sends = 0
        setContent {
            ItmoTheme(platformStyle = style) {
                ReportDialog(
                    title = TITLE,
                    reasons = OPTIONS,
                    state = ReportDialogState(error = ERROR),
                    commentLabel = COMMENT,
                    sendLabel = SEND,
                    dismissLabel = CANCEL,
                    onReasonSelect = {},
                    onCommentChange = {},
                    onSend = { sends++ },
                    onDismiss = {},
                )
            }
        }

        ItmoPlatformStyle.entries.forEach {
            style = it
            onNodeWithText(SEND).assertIsNotEnabled()
            onNodeWithText(ERROR).assertIsDisplayed()
            assertTouchTargets(it.minTouchTarget)
        }

        assertEquals(0, sends)
    }

    @Test
    fun reportSendsOnceAndStaysOpenWhileSending() = runComposeUiTest {
        var style by mutableStateOf(ItmoPlatformStyle.Material)
        val events = mutableListOf<String>()
        setContent {
            ItmoTheme(platformStyle = style) {
                ReportDialog(
                    title = TITLE,
                    reasons = OPTIONS,
                    state = ReportDialogState(selectedReason = 0),
                    commentLabel = COMMENT,
                    sendLabel = SEND,
                    dismissLabel = CANCEL,
                    onReasonSelect = { events += "reason $it" },
                    onCommentChange = {},
                    onSend = { events += SEND },
                    onDismiss = { events += CANCEL },
                )
            }
        }

        ItmoPlatformStyle.entries.forEach {
            style = it
            onNodeWithText(OPTIONS[1]).performClick()
            onNodeWithText(SEND).performClick()
            onNodeWithText(TITLE).assertIsDisplayed()
        }

        assertEquals(listOf("reason 1", SEND, "reason 1", SEND), events)
    }

    @Test
    fun whileSendingTheReasonsAreLockedAndSendIgnoresTaps() = runComposeUiTest {
        var style by mutableStateOf(ItmoPlatformStyle.Material)
        val events = mutableListOf<String>()
        setContent {
            ItmoTheme(platformStyle = style) {
                ReportDialog(
                    title = TITLE,
                    reasons = OPTIONS,
                    state = ReportDialogState(selectedReason = 0, sending = true),
                    commentLabel = COMMENT,
                    sendLabel = SEND,
                    dismissLabel = CANCEL,
                    onReasonSelect = { events += "reason $it" },
                    onCommentChange = {},
                    onSend = { events += SEND },
                    onDismiss = { events += CANCEL },
                )
            }
        }

        ItmoPlatformStyle.entries.forEach {
            style = it
            onNodeWithText(OPTIONS[1]).assertIsNotEnabled().performClick()
            onNodeWithText(SEND).performClick()
            onNodeWithText(CANCEL).performClick()
            onNodeWithText(TITLE).assertIsDisplayed()
        }

        assertEquals(listOf(CANCEL, CANCEL), events)
    }

    private companion object {
        const val TITLE = "Пожаловаться на отзыв"
        const val TEXT = "Отзыв скроется до проверки."
        const val CONFIRM = "Удалить"
        const val CANCEL = "Отмена"
        const val SEND = "Отправить"
        const val COMMENT = "Комментарий"
        const val ERROR = "Нет связи"
        const val OK = "Понятно"
        const val OPTION = "Записать даже за час"
        val OPTIONS = listOf("Оскорбления", "Не о том преподавателе", "Спам")
    }
}
