package dev.alllexey.itmowidgets.feature.auth.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.auth.presentation.InteractiveLoginUiState
import dev.alllexey.itmowidgets.feature.auth.presentation.LoginPage
import dev.alllexey.itmowidgets.shared.feature.account.Res
import dev.alllexey.itmowidgets.shared.feature.account.auth_error_network
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class LoginScreenTest {

    @Test
    fun theBrowserStaysComposedAtOneSizeWhileTheErrorAndTheSpinnerComeAndGo() = runComposeUiTest {
        var state by mutableStateOf(InteractiveLoginUiState())
        var created = 0
        var released = 0
        setContent {
            ItmoTheme {
                LoginScreen(state, {}, {}, browser = { modifier ->
                    DisposableEffect(Unit) {
                        created++
                        onDispose { released++ }
                    }
                    Box(modifier.testTag(BROWSER))
                })
            }
        }

        val bounds = onNodeWithTag(BROWSER).getBoundsInRoot()
        for (value in States) {
            state = value
            waitForIdle()
            assertTouchTargets()
            assertEquals(bounds, onNodeWithTag(BROWSER).getBoundsInRoot(), "$value moved the browser")
            onNodeWithTag(LoginTestTags.ERROR_CONTAINER).run {
                if (value.showsError) assertExists() else assertDoesNotExist()
            }
            onNodeWithTag(LoginTestTags.COMPLETING_PROGRESS).run {
                if (value.completingLogin) assertExists() else assertDoesNotExist()
            }
        }
        assertEquals(1, created)
        assertEquals(0, released)
    }

    @Test
    fun aFailedPageReadsTheXmlTextAndAFailedSignInItsOwn() = runComposeUiTest {
        var state by mutableStateOf(InteractiveLoginUiState(page = LoginPage.Failed))
        setContent { ItmoTheme { LoginScreen(state, {}, {}, browser = { modifier -> Box(modifier) }) } }

        onNodeWithText("Вход через ITMO.ID").assertExists()
        onNodeWithText("Страница ITMO.ID не открылась.").assertExists()

        state = InteractiveLoginUiState(page = LoginPage.Shown, error = UiText.Res(Res.string.auth_error_network))
        waitForIdle()
        onNodeWithText("Не удалось связаться с ITMO.ID. Проверьте интернет.").assertExists()
    }

    @Test
    fun closeAndRetryCallTheirCallbacks() = runComposeUiTest {
        val calls = mutableListOf<String>()
        setContent {
            ItmoTheme {
                LoginScreen(
                    InteractiveLoginUiState(page = LoginPage.Failed),
                    onClose = { calls += "close" },
                    onRetry = { calls += "retry" },
                    browser = { modifier -> Box(modifier) },
                )
            }
        }

        onNodeWithTag(LoginTestTags.CLOSE).performClick()
        onNodeWithTag(LoginTestTags.RETRY).performClick()

        assertEquals(listOf("close", "retry"), calls)
    }

    private companion object {
        const val BROWSER = "browser"
        val States = listOf(
            InteractiveLoginUiState(page = LoginPage.Shown),
            InteractiveLoginUiState(page = LoginPage.Shown, completingLogin = true),
            InteractiveLoginUiState(page = LoginPage.Failed),
            InteractiveLoginUiState(page = LoginPage.Shown, error = UiText.Res(Res.string.auth_error_network)),
            InteractiveLoginUiState(),
        )
    }
}
