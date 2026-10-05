package dev.alllexey.itmowidgets.designsystem.components.avatar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import coil3.ColorImage
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import coil3.request.ErrorResult
import coil3.test.FakeImageLoaderEngine
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class AvatarTest {
    @Test
    fun initialsTakeTheFirstAndLastWords() = runComposeUiTest {
        setContent {
            ItmoTheme {
                Avatar(name = " Преображенская  Александра Вячеславовна ", pictureUrl = null)
                Avatar(name = "александра", pictureUrl = " ")
            }
        }

        onNodeWithText("ПВ", useUnmergedTree = true).assertExists()
        onNodeWithText("А", useUnmergedTree = true).assertExists()
    }

    @Test
    fun aPhotoReplacesTheInitialsAndAFailureKeepsThem() = runComposeUiTest {
        setContent {
            Loader(fakeLoader(LocalPlatformContext.current)) {
                Avatar(name = LOADED_NAME, pictureUrl = PHOTO_URL)
                Avatar(name = FAILED_NAME, pictureUrl = MISSING_URL)
            }
        }
        waitForIdle()

        onNodeWithText("ПВ", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText("ИИ", useUnmergedTree = true).assertExists()
    }

    @Test
    fun aLateFailureOfAnEarlierUrlKeepsTheNewerPhoto() = runComposeUiTest {
        val slow = CompletableDeferred<Unit>()
        var url by mutableStateOf(SLOW_URL)
        setContent {
            Loader(fakeLoader(LocalPlatformContext.current, slow)) {
                Avatar(name = LOADED_NAME, pictureUrl = url)
            }
        }
        waitForIdle()
        onNodeWithText("ПВ", useUnmergedTree = true).assertExists()

        url = PHOTO_URL
        waitForIdle()
        slow.complete(Unit)
        waitForIdle()

        onNodeWithText("ПВ", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun decorativeUnlessDescribed() = runComposeUiTest {
        setContent {
            ItmoTheme {
                Avatar(name = LOADED_NAME, pictureUrl = null)
                Avatar(name = FAILED_NAME, pictureUrl = null, contentDescription = FAILED_NAME)
            }
        }

        onNodeWithContentDescription(FAILED_NAME).assertExists()
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)).assertCountEquals(1)
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility), useUnmergedTree = true)
            .assertCountEquals(2)
    }

    @Composable
    private fun Loader(loader: ImageLoader, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalAvatarImageLoader provides loader) { ItmoTheme(content = content) }
    }

    /** [PHOTO_URL] loads, [SLOW_URL] fails once [slow] completes, anything else fails (no fetcher in the kit). */
    private fun fakeLoader(context: PlatformContext, slow: CompletableDeferred<Unit> = CompletableDeferred()): ImageLoader {
        val engine = FakeImageLoaderEngine.Builder()
            .intercept(PHOTO_URL, ColorImage())
            .intercept({ it == SLOW_URL }) { chain ->
                slow.await()
                ErrorResult(image = null, request = chain.request, throwable = IllegalStateException("late"))
            }
            .build()
        return ImageLoader.Builder(context).components { add(engine) }.coroutineContext(Dispatchers.Unconfined).build()
    }

    private companion object {
        const val LOADED_NAME = "Преображенская Александра Вячеславовна"
        const val FAILED_NAME = "Иванов Иван"
        const val PHOTO_URL = "https://example.invalid/photo.jpg"
        const val SLOW_URL = "https://example.invalid/slow.jpg"
        const val MISSING_URL = "https://example.invalid/missing.jpg"
    }
}
