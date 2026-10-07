package dev.alllexey.itmowidgets.feature.sport.ui.sign

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignCommand
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignEvent
import dev.alllexey.itmowidgets.shared.feature.sport.Res
import dev.alllexey.itmowidgets.shared.feature.sport.sport_auto_sign_existing_entry
import dev.alllexey.itmowidgets.shared.feature.sport.sport_auto_sign_free_description
import dev.alllexey.itmowidgets.shared.feature.sport.sport_auto_sign_title
import dev.alllexey.itmowidgets.shared.feature.sport.sport_community_services_disabled
import dev.alllexey.itmowidgets.shared.feature.sport.sport_sign_success
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import dev.alllexey.itmowidgets.testkit.awaitResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class SportSignDialogsTest {

    private val executed = mutableListOf<Pair<SportSignCommand, Boolean>>()

    @Test
    fun confirmWithoutTheForceSignSwitchRunsTheCommandOnce() = runComposeUiTest {
        val state = showDialogs()

        state.show(confirmEvent(showForceSign = true))
        waitForIdle()
        onNodeWithTag(SportSignDialogsTestTags.FORCE_SIGN).assertExists()
        button(CONFIRM).performClick()
        waitForIdle()

        assertEquals(listOf(FreeSign to false), executed)
        assertNull(state.dialog)
    }

    @Test
    fun confirmWithTheForceSignSwitchRunsTheCommandOnceWithForceSignInEveryStyle() = runComposeUiTest {
        var style by mutableStateOf(ItmoPlatformStyle.Material)
        val state = showDialogs { style }

        ItmoPlatformStyle.entries.forEach {
            style = it
            state.show(confirmEvent(showForceSign = true))
            waitForIdle()
            assertTouchTargets(it.minTouchTarget)
            onNodeWithTag(SportSignDialogsTestTags.FORCE_SIGN).performClick()
            button(CONFIRM).performClick()
            waitForIdle()
        }

        assertEquals(listOf(FreeSign to true, FreeSign to true), executed)
    }

    @Test
    fun aFutureLessonHasNoSwitchAndNeverForcesTheSign() = runComposeUiTest {
        val state = showDialogs()

        state.show(confirmEvent(showForceSign = false, command = AutoSign))
        waitForIdle()
        onNodeWithTag(SportSignDialogsTestTags.FORCE_SIGN).assertDoesNotExist()
        button(CONFIRM).performClick()
        waitForIdle()

        assertEquals(listOf(AutoSign to false), executed)
    }

    @Test
    fun theSwitchStartsOffInEveryNewDialog() = runComposeUiTest {
        val state = showDialogs()

        state.show(confirmEvent(showForceSign = true))
        waitForIdle()
        onNodeWithTag(SportSignDialogsTestTags.FORCE_SIGN).performClick()
        button(BACK).performClick()
        waitForIdle()
        state.show(confirmEvent(showForceSign = true))
        waitForIdle()
        button(CONFIRM).performClick()
        waitForIdle()

        assertEquals(listOf(FreeSign to false), executed)
    }

    @Test
    fun leavingTheQueueRunsItsCommandOnce() = runComposeUiTest {
        val state = showDialogs()

        state.show(
            SportSignEvent.ShowAutoSignDeleteDialog(
                message = UiText.Res(Res.string.sport_auto_sign_existing_entry, listOf(2, 5)),
                command = LeaveQueue,
            ),
        )
        waitForIdle()
        onNodeWithText("Автозапись на это занятие уже есть. Место в очереди: 2 из 5").assertExists()
        button("Отписаться").performClick()
        waitForIdle()

        assertEquals(listOf(LeaveQueue to false), executed)
    }

    @Test
    fun dismissingRunsNoCommand() = runComposeUiTest {
        val state = showDialogs()

        state.show(confirmEvent(showForceSign = true))
        waitForIdle()
        button(BACK).performClick()
        waitForIdle()
        assertNull(state.dialog)

        state.show(SportSignEvent.ShowAutoSignDeleteDialog(UiText.Dynamic("Выйти из очереди?"), LeaveQueue))
        waitForIdle()
        button(BACK).performClick()
        waitForIdle()
        assertNull(state.dialog)

        state.show(SportSignEvent.ShowInfoDialog(message = UiText.Res(Res.string.sport_community_services_disabled)))
        waitForIdle()
        button("Хорошо").performClick()
        waitForIdle()
        assertNull(state.dialog)

        state.show(SportSignEvent.ShowLinkUnavailable)
        waitForIdle()
        onNodeWithText("Занятие недоступно").assertExists()
        button("Понятно").performClick()
        waitForIdle()
        assertNull(state.dialog)

        assertEquals(emptyList<Pair<SportSignCommand, Boolean>>(), executed)
    }

    @Test
    fun aSecondConfirmOfTheSameDialogGetsNoCommand() {
        val state = SportSignDialogState()
        state.show(confirmEvent(showForceSign = true))

        assertEquals(FreeSign, state.confirm())
        assertNull(state.confirm())
    }

    @Test
    fun onlyDialogEventsOpenADialog() {
        val state = SportSignDialogState()

        assertFalse(state.show(SportSignEvent.ShowToast(UiText.Dynamic("Записали"))))
        assertFalse(state.show(SportSignEvent.ShowError(AppError.Network)))
        assertNull(state.dialog)
        assertTrue(state.show(SportSignEvent.ShowLinkUnavailable))
        assertEquals(SportSignDialog.LinkUnavailable, state.dialog)
    }

    @Test
    fun aSuccessMessageBecomesASnackbarWithTheSameText() = runComposeUiTest {
        val snackbars = SnackbarHostState()
        setContent {
            LaunchedEffect(Unit) {
                snackbars.showSportSignMessage(SportSignEvent.ShowToast(UiText.Res(Res.string.sport_sign_success)))
            }
        }
        val message = awaitResource("a snackbar is shown") { snackbars.currentSnackbarData?.visuals?.message }

        assertEquals("Записали", message)
    }

    private fun ComposeUiTest.showDialogs(
        style: () -> ItmoPlatformStyle = { ItmoPlatformStyle.Material },
    ): SportSignDialogState {
        val state = SportSignDialogState()
        setContent {
            ItmoTheme(platformStyle = style()) {
                SportSignDialogs(state, onExecute = { command, forceSign -> executed += command to forceSign })
            }
        }
        return state
    }

    private fun ComposeUiTest.button(label: String) = onAllNodesWithText(label).filterToOne(hasClickAction())

    private fun confirmEvent(showForceSign: Boolean, command: SportSignCommand = FreeSign) =
        SportSignEvent.ShowAutoSignConfirmDialog(
            title = UiText.Res(Res.string.sport_auto_sign_title),
            message = UiText.Res(Res.string.sport_auto_sign_free_description),
            showForceSignButton = showForceSign,
            command = command,
        )

    private companion object {
        const val CONFIRM = "Автозапись"
        const val BACK = "Назад"
        val FreeSign: SportSignCommand = SportSignCommand.CreateFreeSign(7)
        val AutoSign: SportSignCommand = SportSignCommand.CreateAutoSign(8)
        val LeaveQueue: SportSignCommand = SportSignCommand.CancelAutoSign(9)
    }
}
