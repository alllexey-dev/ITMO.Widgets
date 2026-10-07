package dev.alllexey.itmowidgets.feature.auth.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.auth.presentation.AuthUiState
import dev.alllexey.itmowidgets.shared.feature.account.Res
import dev.alllexey.itmowidgets.shared.feature.account.auth_error_network
import dev.alllexey.itmowidgets.testkit.assertNoTextOverflow
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class AuthScreenTest {

    @Test
    fun theSpinnerShowsOnlyWhileTheSessionIsCheckedAndEveryStateKeeps48DpTargets() = runComposeUiTest {
        var state by mutableStateOf(AuthUiState())
        setContent { Screen(state) }

        onNodeWithTag(AuthTestTags.PROGRESS).assertExists()
        onNodeWithTag(AuthTestTags.CONTENT).assertDoesNotExist()
        for (value in States) {
            state = value
            waitForIdle()
            assertTouchTargets()
            onNodeWithTag(AuthTestTags.PROGRESS).assertDoesNotExist()
            onNodeWithTag(AuthTestTags.CONTENT).assertExists()
            onNodeWithTag(AuthTestTags.REAUTH_NOTICE).run {
                if (value.reauthenticationRequired) assertExists() else assertDoesNotExist()
            }
            onNodeWithTag(AuthTestTags.ERROR).run { if (value.error != null) assertExists() else assertDoesNotExist() }
        }
    }

    @Test
    fun theLogoTakesTapsButIsNoTargetForAccessibilityServices() = runComposeUiTest {
        var taps = 0
        setContent { Screen(SignedOut, onLogoTap = { taps++ }) }

        val logo = onNodeWithTag(AuthTestTags.LOGO).fetchSemanticsNode()
        assertFalse(SemanticsActions.OnClick in logo.config, "the logo has a click action")
        assertFalse(SemanticsProperties.ContentDescription in logo.config, "the logo has a description")
        repeat(5) { onNodeWithTag(AuthTestTags.LOGO).performTouchInput { click() } }

        assertEquals(5, taps)
    }

    @Test
    fun aSignInInProgressDisablesBothWaysInAndShowsTheSpinner() = runComposeUiTest {
        var state by mutableStateOf(SignedOut)
        setContent { Screen(state) }
        onNodeWithTag(AuthTestTags.ITMO_ID_LOGIN).assertIsEnabled()
        onNodeWithTag(AuthTestTags.MANUAL_PROGRESS).assertDoesNotExist()

        for (busy in listOf(SignedOut.copy(manualLoginInProgress = true), SignedOut.copy(sessionTransitionInProgress = true))) {
            state = busy
            waitForIdle()
            onNodeWithTag(AuthTestTags.ITMO_ID_LOGIN).assertIsNotEnabled()
            onNodeWithTag(AuthTestTags.REFRESH_TOKEN_LOGIN).assertIsNotEnabled()
            onNodeWithTag(AuthTestTags.MANUAL_PROGRESS).assertExists()
        }
    }

    @Test
    fun theNoticeSitsUnderTheSignInButtons() = runComposeUiTest {
        setContent { Screen(SignedOut) }

        onNodeWithTag(AuthTestTags.UNOFFICIAL_NOTICE).performScrollTo()
        val notice = onNodeWithTag(AuthTestTags.UNOFFICIAL_NOTICE).getBoundsInRoot()
        val signIn = onNodeWithTag(AuthTestTags.REFRESH_TOKEN_LOGIN).getBoundsInRoot()
        assertTrue(notice.top > signIn.bottom, "the notice starts at ${notice.top}, above ${signIn.bottom}")
        onNodeWithText("Неофициальное приложение. Не связано с Университетом ИТМО.").assertExists()
    }

    @Test
    fun nothingClipsAtFontScale13In320Dp() = runComposeUiTest {
        var state by mutableStateOf(SignedOut)
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, NARROW_FONT_SCALE)) {
                Box(Modifier.requiredSize(320.dp, 891.dp)) { Screen(state) }
            }
        }

        for (value in States) {
            state = value
            waitForIdle()
            assertNoTextOverflow()
        }
    }

    @Test
    fun anEmptyTokenKeepsTheDialogAndATypedOneSignsInOnce() = runComposeUiTest {
        val tokens = mutableListOf<String>()
        setContent { Screen(SignedOut, onSignInWithToken = { tokens += it }) }

        onNodeWithTag(AuthTestTags.REFRESH_TOKEN_LOGIN).performClick()
        onNodeWithText("Войти").performClick()
        onNodeWithText("Вставьте Refresh token").assertExists()
        assertEquals(emptyList(), tokens)

        onNodeWithTag(AuthTestTags.TOKEN_INPUT).performTextInput(SYNTHETIC_TOKEN)
        onNodeWithText("Войти").performClick()
        waitForIdle()

        assertEquals(listOf(SYNTHETIC_TOKEN), tokens)
        onNodeWithTag(AuthTestTags.TOKEN_INPUT).assertDoesNotExist()
    }

    @Test
    fun cancelDropsTheTypedToken() = runComposeUiTest {
        setContent { Screen(SignedOut) }

        onNodeWithTag(AuthTestTags.REFRESH_TOKEN_LOGIN).performClick()
        onNodeWithTag(AuthTestTags.TOKEN_INPUT).performTextInput(SYNTHETIC_TOKEN)
        onNodeWithText("Отмена").performClick()
        waitForIdle()
        onNodeWithTag(AuthTestTags.TOKEN_INPUT).assertDoesNotExist()

        onNodeWithTag(AuthTestTags.REFRESH_TOKEN_LOGIN).performClick()
        val text = onNodeWithTag(AuthTestTags.TOKEN_INPUT).fetchSemanticsNode().config[SemanticsProperties.EditableText]
        assertEquals("", text.text)
    }

    @Test
    fun theItmoIdButtonCallsItsCallback() = runComposeUiTest {
        var calls = 0
        setContent { Screen(SignedOut, onSignInWithItmoId = { calls++ }) }

        onNodeWithTag(AuthTestTags.ITMO_ID_LOGIN).performClick()

        assertEquals(1, calls)
    }

    @Composable
    private fun Screen(
        state: AuthUiState,
        onLogoTap: () -> Unit = {},
        onSignInWithItmoId: () -> Unit = {},
        onSignInWithToken: (String) -> Unit = {},
    ) = ItmoTheme { AuthScreen(state, onLogoTap, onSignInWithItmoId, onSignInWithToken) }

    private companion object {
        const val SYNTHETIC_TOKEN = "synthetic-refresh-token"
        const val NARROW_FONT_SCALE = 1.3f
        val SignedOut = AuthUiState(initializing = false)
        val States = listOf(
            SignedOut,
            SignedOut.copy(reauthenticationRequired = true),
            SignedOut.copy(manualLoginInProgress = true),
            SignedOut.copy(error = UiText.Res(Res.string.auth_error_network)),
        )
    }
}
