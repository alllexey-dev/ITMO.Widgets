package dev.alllexey.itmowidgets.feature.update.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.update.FakeAppUpdateRepository
import dev.alllexey.itmowidgets.feature.update.domain.AppVersionName
import dev.alllexey.itmowidgets.feature.update.presentation.AppUpdateArgs
import dev.alllexey.itmowidgets.feature.update.presentation.AppUpdateUiState
import dev.alllexey.itmowidgets.feature.update.presentation.AppUpdateViewModel
import dev.alllexey.itmowidgets.feature.update.ui.preview.AppUpdatePreviewData
import dev.alllexey.itmowidgets.testkit.assertNoTextOverflow
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import dev.alllexey.itmowidgets.testkit.awaitText
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class AppUpdateScreenTest {

    @Test
    fun aSupportedOfferReadsTheReasonThenTheNoteAndKeepsEveryWayOut() = runComposeUiTest {
        setContent { Screen(Supported) }

        onNodeWithTag(AppUpdateTestTags.TITLE).assertTextEquals("Вышла новая версия")
        onNodeWithTag(AppUpdateTestTags.VERSIONS).assertTextEquals("2.1 → 2.2")
        onNodeWithTag(AppUpdateTestTags.DESCRIPTION).assertTextEquals(
            "Обновление уже можно скачать. В нём новые возможности и исправления.\n\n$NOTE"
        )
        // A release the user can postpone keeps every way out visible.
        val ways = listOf(AppUpdateTestTags.CLOSE, AppUpdateTestTags.UPDATE, AppUpdateTestTags.LATER, AppUpdateTestTags.SKIP)
        for (tag in ways) {
            onNodeWithTag(tag).assertExists()
        }
        assertTouchTargets()
    }

    @Test
    fun anUnsupportedBuildExplainsItselfAndDropsTheSkip() = runComposeUiTest {
        setContent { Screen(Unsupported) }

        onNodeWithTag(AppUpdateTestTags.TITLE).assertTextEquals("Эта версия устарела")
        onNodeWithTag(AppUpdateTestTags.DESCRIPTION).assertTextEquals(
            "ITMO.Widgets больше не поддерживает установленную версию — часть возможностей может не работать. " +
                "Обновитесь, и всё снова заработает."
        )
        // Skipping an unsupported build would leave nothing that works.
        onNodeWithTag(AppUpdateTestTags.SKIP).assertDoesNotExist()
        onNodeWithTag(AppUpdateTestTags.LATER).assertExists()
        assertTouchTargets()
    }

    @Test
    fun eachActionCallsItsOwnCallback() = runComposeUiTest {
        val calls = mutableListOf<String>()
        setContent {
            ItmoTheme {
                AppUpdateScreen(
                    Supported,
                    onUpdate = { calls += "update" },
                    onLater = { calls += "later" },
                    onClose = { calls += "close" },
                    onSkip = { calls += "skip" },
                )
            }
        }

        onNodeWithTag(AppUpdateTestTags.UPDATE).performScrollTo().performClick()
        onNodeWithTag(AppUpdateTestTags.LATER).performScrollTo().performClick()
        onNodeWithTag(AppUpdateTestTags.SKIP).performScrollTo().performClick()
        onNodeWithTag(AppUpdateTestTags.CLOSE).performClick()

        assertEquals(listOf("update", "later", "skip", "close"), calls)
    }

    @Test
    fun nothingClipsAtFontScale13In320Dp() = runComposeUiTest {
        var state by mutableStateOf(Supported)
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, NARROW_FONT_SCALE)) {
                Box(Modifier.requiredSize(NarrowWidth, WindowHeight)) { Screen(state) }
            }
        }

        for (value in listOf(Supported, Unsupported, AppUpdatePreviewData.LongNote)) {
            state = value
            waitForIdle()
            assertNoTextOverflow()
            assertTouchTargets()
            // The close button keeps its 48 dp at a large font instead of a fixed box.
            assertTrue(touchHeight(AppUpdateTestTags.CLOSE) >= 48.dp, "the close button is too low")
            val last = if (value.unsupported) AppUpdateTestTags.LATER else AppUpdateTestTags.SKIP
            // The offer scrolls, so the last action is reachable under the longest note.
            val bounds = onNodeWithTag(last).performScrollTo().getBoundsInRoot()
            assertTrue(bounds.right <= NarrowWidth && bounds.bottom <= WindowHeight, "$last ends at $bounds")
        }
    }

    @Test
    fun skippingStoresTheReleaseBeforeTheRouteCloses() = runComposeUiTest {
        val repository = FakeAppUpdateRepository()
        val skippedWhenClosed = mutableListOf<AppVersionName?>()
        setContent {
            ItmoTheme {
                AppUpdateRoute(
                    onUpdate = { _, _ -> },
                    onClose = { skippedWhenClosed += repository.skippedVersion },
                    viewModel = viewModel(repository),
                )
            }
        }

        onNodeWithTag(AppUpdateTestTags.SKIP).performScrollTo().performClick()
        waitUntil { skippedWhenClosed.isNotEmpty() }

        assertEquals(listOf<AppVersionName?>(AppVersionName("2.2")), skippedWhenClosed)
    }

    @Test
    fun theRouteHandsTheUpdateToTheHostAndReportsWhenNothingOpened() = runComposeUiTest {
        val requests = mutableListOf<Boolean>()
        setContent {
            ItmoTheme {
                AppUpdateRoute(
                    onUpdate = { unsupported, onFailed ->
                        requests += unsupported
                        onFailed()
                    },
                    onClose = {},
                    viewModel = viewModel(FakeAppUpdateRepository(), unsupported = true),
                )
            }
        }

        onNodeWithTag(AppUpdateTestTags.UPDATE).performScrollTo().performClick()
        val snackbar = awaitText(OPEN_FAILED)

        assertEquals(listOf(true), requests)
        snackbar.assertExists()
    }

    private companion object {
        const val NOTE = "Виджет расписания обновляется быстрее, зачётка помнит выбранный семестр."
        const val OPEN_FAILED = "Не удалось открыть страницу с релизом"

        val Supported = AppUpdateUiState(installed = "2.1", latest = "2.2", note = NOTE, unsupported = false)
        val Unsupported = AppUpdateUiState(installed = "2.1", latest = "2.2", note = "", unsupported = true)

        val NarrowWidth: Dp = 320.dp
        val WindowHeight: Dp = 891.dp
        const val NARROW_FONT_SCALE = 1.3f
    }
}

@Composable
private fun Screen(state: AppUpdateUiState) = ItmoTheme {
    AppUpdateScreen(state, onUpdate = {}, onLater = {}, onClose = {}, onSkip = {})
}

private fun viewModel(repository: FakeAppUpdateRepository, unsupported: Boolean = false) = AppUpdateViewModel(
    repository = repository,
    savedStateHandle = SavedStateHandle(
        mapOf(
            AppUpdateArgs.KEY_INSTALLED_VERSION to "2.1",
            AppUpdateArgs.KEY_LATEST_VERSION to "2.2",
            AppUpdateArgs.KEY_UNSUPPORTED to unsupported,
        )
    ),
)

/** The height of the layout node that carries the click, `minimumInteractiveComponentSize` included. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.touchHeight(tag: String): Dp {
    val layout = onNodeWithTag(tag).fetchSemanticsNode().layoutInfo
    return with(layout.density) { layout.height.toDp() }
}
