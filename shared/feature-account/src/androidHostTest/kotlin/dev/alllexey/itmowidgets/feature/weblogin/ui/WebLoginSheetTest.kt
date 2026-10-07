package dev.alllexey.itmowidgets.feature.weblogin.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.weblogin.WebLoginPreview
import dev.alllexey.itmowidgets.core.weblogin.WebLoginRepository
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.weblogin.presentation.WebLoginUiState
import dev.alllexey.itmowidgets.feature.weblogin.presentation.WebLoginViewModel
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertNoTextOverflow
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid
import kotlinx.coroutines.CompletableDeferred

/**
 * Every case of the deleted `WebLoginVisualTest` on the Compose sheet over the real `WebLoginViewModel` and a
 * synthetic repository, at 320 dp and font scale 1.3: the approve flow, wrong and expired codes, the longest browser
 * name, the scanner that cannot start. The Play services scanner itself and the sheet's height stay in
 * `WebLoginBottomSheet`; the looks live in the `WebLoginSheetContent_*` goldens.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class WebLoginSheetTest {

    private val repository = SyntheticRepository()
    private val model = WebLoginViewModel(SavedStateHandle(), repository, FixedAcademicTime())
    private var closed = 0

    @Test
    fun aTypedCodeIsCheckedThenApprovedThenTheSheetCloses() = runComposeUiTest {
        repository.previews[CODE] = AppResult.Success(WebLoginSamples.preview)
        setContent { Sheet() }
        tagged(WebLoginSheetTestTags.CONTINUE).assertIsNotEnabled()
        assertTouchTargets()
        assertNoTextOverflow()

        val gate = CompletableDeferred<Unit>().also { repository.gate = it }
        tagged(WebLoginSheetTestTags.CODE).performTextInput("abcd-2345")
        tagged(WebLoginSheetTestTags.CONTINUE).assertIsEnabled().performClick()
        waitForIdle()
        tagged(WebLoginSheetTestTags.CONTINUE).assertIsNotEnabled()
        tagged(WebLoginSheetTestTags.SCAN).assertIsNotEnabled()
        tagged(WebLoginSheetTestTags.CODE).assertIsNotEnabled()
        assertField("ABCD2345")

        repository.gate = null
        gate.complete(Unit)
        waitForIdle()
        onNodeWithText("Chrome на macOS", useUnmergedTree = true).assertExists()
        onNodeWithText("Запрошен в 12:04", useUnmergedTree = true).assertExists()
        onNodeWithText("Подтверждайте только свой вход").assertExists()
        assertTouchTargets()
        assertNoTextOverflow()

        tagged(WebLoginSheetTestTags.APPROVE).performClick()
        waitForIdle()
        onNodeWithText("Готово — вернитесь в браузер", useUnmergedTree = true).assertExists()
        assertTouchTargets()
        assertNoTextOverflow()
        assertEquals(listOf(WebLoginSamples.preview.challengeId), repository.approved)

        tagged(WebLoginSheetTestTags.RESULT_ACTION).performClick()
        assertEquals(1, closed)
    }

    @Test
    fun wrongAndExpiredCodesSayWhatHappened() = runComposeUiTest {
        repository.previews[CODE] = AppResult.Success(WebLoginSamples.preview)
        repository.approval = AppResult.Failure(AppError.NotFound)
        setContent { Sheet() }

        tagged(WebLoginSheetTestTags.CODE).performTextInput("ABC")
        tagged(WebLoginSheetTestTags.CODE).performImeAction()
        waitForIdle()
        assertCodeError("Нужен код из 8 символов")

        tagged(WebLoginSheetTestTags.CODE).performTextReplacement("ZZZZ2345")
        tagged(WebLoginSheetTestTags.CONTINUE).performClick()
        waitForIdle()
        assertCodeError("Код не найден или устарел")
        assertField("ZZZZ2345")
        assertNoTextOverflow()

        tagged(WebLoginSheetTestTags.CODE).performTextReplacement(CODE)
        tagged(WebLoginSheetTestTags.CONTINUE).performClick()
        waitForIdle()
        tagged(WebLoginSheetTestTags.APPROVE).performClick()
        waitForIdle()
        onNodeWithText("Код не найден или устарел", useUnmergedTree = true).assertExists()
        onNodeWithTag(WebLoginSheetTestTags.RESULT_ACTION).assertTextContains("Повторить")
        assertNoTextOverflow()

        tagged(WebLoginSheetTestTags.RESULT_ACTION).performClick()
        waitForIdle()
        assertField("")
        tagged(WebLoginSheetTestTags.CODE_ERROR).assertDoesNotExist()
    }

    @Test
    fun theLongestBrowserNameFitsAtFont13On320Dp() = runComposeUiTest {
        repository.previews[CODE] = AppResult.Success(WebLoginSamples.longAgentPreview)
        setContent { Sheet() }
        assertNoTextOverflow()
        assertTouchTargets()

        tagged(WebLoginSheetTestTags.CODE).performTextInput(CODE)
        tagged(WebLoginSheetTestTags.CONTINUE).performClick()
        waitForIdle()

        onNodeWithText("Яндекс Браузер на Windows", useUnmergedTree = true).assertExists()
        assertNoTextOverflow()
        assertTouchTargets()
    }

    @Test
    fun aScannerThatCannotStartLeavesTheFieldUsable() = runComposeUiTest {
        setContent { Sheet(onScan = model::onScannerUnavailable) }

        tagged(WebLoginSheetTestTags.SCAN).performClick()
        waitForIdle()
        assertCodeError("Сканер недоступен, введите код")
        assertNoTextOverflow()

        tagged(WebLoginSheetTestTags.CODE).performTextInput(CODE)
        waitForIdle()
        tagged(WebLoginSheetTestTags.CODE_ERROR).assertDoesNotExist()
        tagged(WebLoginSheetTestTags.CONTINUE).assertIsEnabled()
    }

    @Test
    fun aScannedLinkGoesStraightToTheBrowserAndAnotherQrKeepsTheTypedCode() = runComposeUiTest {
        repository.previews[CODE] = AppResult.Success(WebLoginSamples.preview)
        var scanned = "https://example.com/menu"
        setContent { Sheet(onScan = { model.onScanned(scanned) }) }

        tagged(WebLoginSheetTestTags.CODE).performTextInput("ABCD")
        tagged(WebLoginSheetTestTags.SCAN).performClick()
        waitForIdle()
        assertCodeError("Это не QR для входа на сайт")
        assertField("ABCD")

        scanned = "https://dev.widgets.alllexey.dev/app/login?code=abcd2345"
        tagged(WebLoginSheetTestTags.SCAN).performClick()
        waitForIdle()
        tagged(WebLoginSheetTestTags.BROWSER).assertExists()
    }

    @Test
    fun anApprovalInFlightDisablesBothButtonsAndCancelKeepsTheCode() = runComposeUiTest {
        repository.previews[CODE] = AppResult.Success(WebLoginSamples.preview)
        setContent { Sheet() }
        tagged(WebLoginSheetTestTags.CODE).performTextInput(CODE)
        tagged(WebLoginSheetTestTags.CONTINUE).performClick()
        waitForIdle()

        tagged(WebLoginSheetTestTags.CANCEL).performClick()
        waitForIdle()
        assertField(CODE)

        tagged(WebLoginSheetTestTags.CONTINUE).performClick()
        waitForIdle()
        val gate = CompletableDeferred<Unit>().also { repository.gate = it }
        tagged(WebLoginSheetTestTags.APPROVE).performClick()
        waitForIdle()
        tagged(WebLoginSheetTestTags.APPROVE).assertIsNotEnabled()
        tagged(WebLoginSheetTestTags.CANCEL).assertIsNotEnabled()
        assertEquals(true, (model.uiState.value as WebLoginUiState.Confirm).approving)

        repository.gate = null
        gate.complete(Unit)
        waitForIdle()
        assertEquals(WebLoginUiState.Done, model.uiState.value)
    }

    @Test
    fun everyPreviewStateKeeps48DpTargetsAndClipsNothing() = runComposeUiTest {
        var state by mutableStateOf<WebLoginUiState>(WebLoginSamples.input)
        setContent { Narrow { WebLoginSheetContent(state, WebLoginActions(), Modifier.fillMaxWidth()) } }

        for (value in States) {
            state = value
            waitForIdle()
            assertTouchTargets()
            assertNoTextOverflow()
        }
    }

    private fun ComposeUiTest.tagged(tag: String) = onNodeWithTag(tag, useUnmergedTree = true)

    /** The field shows [text] with the cursor after it. */
    private fun ComposeUiTest.assertField(text: String) {
        val node = onNodeWithTag(WebLoginSheetTestTags.CODE).fetchSemanticsNode()
        assertEquals(text, node.config[SemanticsProperties.EditableText].text)
        assertEquals(TextRange(text.length), node.config[SemanticsProperties.TextSelectionRange])
    }

    private fun ComposeUiTest.assertCodeError(text: String) {
        tagged(WebLoginSheetTestTags.CODE_ERROR).assertTextContains(text)
    }

    @Composable
    private fun Sheet(onScan: () -> Unit = {}) = Narrow {
        WebLoginSheetRoute(onScan = onScan, onClose = { closed++ }, Modifier.fillMaxWidth(), viewModel = model)
    }

    @Composable
    private fun Narrow(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, NARROW_FONT_SCALE)) {
            ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
                Box(Modifier.requiredSize(NARROW_WIDTH, SHEET_HEIGHT)) { content() }
            }
        }
    }

    /** Synthetic Backend answers: codes in [previews] exist, everything else is not found. */
    private class SyntheticRepository : WebLoginRepository {
        val previews = mutableMapOf<String, AppResult<WebLoginPreview>>()
        var approval: AppResult<Unit> = AppResult.Success(Unit)

        /** When set, answers wait for it, so a test can look at the in-button progress. */
        var gate: CompletableDeferred<Unit>? = null
        val approved = mutableListOf<Uuid>()

        override suspend fun preview(code: String): AppResult<WebLoginPreview> {
            gate?.await()
            return previews[code] ?: AppResult.Failure(AppError.NotFound)
        }

        override suspend fun approve(challengeId: Uuid): AppResult<Unit> {
            gate?.await()
            approved += challengeId
            return approval
        }
    }

    private companion object {
        const val CODE = WebLoginSamples.CODE
        const val NARROW_FONT_SCALE = 1.3f
        val NARROW_WIDTH = 320.dp

        /** 90 % of the 891 dp window, the most a fitted sheet takes. */
        val SHEET_HEIGHT = 802.dp

        val States = with(WebLoginSamples) {
            listOf(input, invalid, notFound, scannerUnavailable, checking, confirm, longAgent, approving,
                WebLoginUiState.Done, network, expired)
        }
    }
}
