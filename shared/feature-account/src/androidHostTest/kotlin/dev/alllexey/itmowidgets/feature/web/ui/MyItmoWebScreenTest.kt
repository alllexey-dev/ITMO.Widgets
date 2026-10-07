package dev.alllexey.itmowidgets.feature.web.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class MyItmoWebScreenTest {

    @Test
    fun theBrowserStaysComposedAtOneSizeWhileOnlyLoadingShowsTheLineAndOnlyFailureTheErrorPage() = runComposeUiTest {
        var state by mutableStateOf(MyItmoWebState.Loading)
        var created = 0
        var released = 0
        setContent {
            ItmoTheme {
                MyItmoWebScreen(state, HOST, {}, {}, {}, {}, browser = { modifier ->
                    DisposableEffect(Unit) {
                        created++
                        onDispose { released++ }
                    }
                    Box(modifier.testTag(BROWSER))
                })
            }
        }

        val bounds = onNodeWithTag(BROWSER).getBoundsInRoot()
        for (value in MyItmoWebState.entries + MyItmoWebState.Loading) {
            state = value
            waitForIdle()
            assertTouchTargets()
            assertEquals(bounds, onNodeWithTag(BROWSER).getBoundsInRoot(), "$value moved the browser")
            onNodeWithTag(MyItmoWebTestTags.LOADING).run {
                if (value == MyItmoWebState.Loading) assertExists() else assertDoesNotExist()
            }
            onNodeWithTag(MyItmoWebTestTags.STATE_CONTAINER).run {
                if (value == MyItmoWebState.Failed) assertExists() else assertDoesNotExist()
            }
        }
        assertEquals(1, created)
        assertEquals(0, released)
    }

    @Test
    fun everyControlCallsItsCallback() = runComposeUiTest {
        val calls = mutableListOf<String>()
        setContent {
            ItmoTheme {
                MyItmoWebScreen(
                    MyItmoWebState.Failed,
                    host = HOST,
                    onClose = { calls += "close" },
                    onReload = { calls += "reload" },
                    onOpenExternal = { calls += "external" },
                    onRetry = { calls += "retry" },
                    browser = { modifier -> Box(modifier) },
                )
            }
        }

        onNodeWithTag(MyItmoWebTestTags.CLOSE).performClick()
        onNodeWithTag(MyItmoWebTestTags.RELOAD).performClick()
        onNodeWithTag(MyItmoWebTestTags.STATE_ACTION).assert(hasText("Повторить")).performClick()
        onNodeWithText("Открыть в браузере").assertDoesNotExist()
        onNodeWithTag(MyItmoWebTestTags.MORE).performClick()
        onNodeWithText("Открыть в браузере").performClick()
        waitForIdle()

        assertEquals(listOf("close", "reload", "retry", "external"), calls)
        onNodeWithText("Открыть в браузере").assertDoesNotExist()
    }

    @Test
    fun withoutAHostTheBarShowsTheTitleAlone() = runComposeUiTest {
        setContent {
            ItmoTheme {
                MyItmoWebScreen(MyItmoWebState.Shown, null, {}, {}, {}, {}, browser = { modifier -> Box(modifier) })
            }
        }

        onNodeWithText("My ITMO").assertExists()
        onNodeWithText(HOST).assertDoesNotExist()
    }

    @Test
    fun theErrorPageReadsTheXmlTexts() = runComposeUiTest {
        setContent {
            ItmoTheme {
                MyItmoWebScreen(MyItmoWebState.Failed, HOST, {}, {}, {}, {}, browser = { modifier -> Box(modifier) })
            }
        }

        onNodeWithText("My ITMO").assertExists()
        onNodeWithText(HOST).assertExists()
        onNodeWithText("Не удалось открыть My ITMO").assertExists()
        onNodeWithText("Проверьте подключение к интернету и попробуйте ещё раз").assertExists()
    }

    private companion object {
        const val BROWSER = "browser"
        const val HOST = "id.itmo.ru"
    }
}
