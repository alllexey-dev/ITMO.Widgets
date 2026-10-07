package dev.alllexey.itmowidgets.feature.resources.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.resources.SubjectLinksState
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeSubjectLinksRepository
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEvent
import dev.alllexey.itmowidgets.feature.resources.presentation.SubjectLinksViewModel
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertNoTextOverflow
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.robolectric.annotation.Config

/**
 * `Жалоба на ссылку` on the real `SubjectLinksViewModel`, wired as `ReportLinkDialogFragment` wires it, at font scale
 * 1.3: the four reasons in order, `Отправить` only with a reason, a failure under the comment that keeps the dialog and
 * goes with the next send, closing once the report is accepted, the comment's 500 characters and the cancel button.
 * The deleted View dialog had no test of its own; its looks were the `ReportLinkDialog_reason` reference and are now
 * the `ReportLinkDialog_*` goldens.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h891dp")
class ReportLinkDialogTest {

    private val links = FakeSubjectLinksRepository().apply {
        state.value = SubjectLinksState.Content(SubjectLinksSamples.snapshot)
    }
    private var done = false
    private var dismissed = 0

    @Test
    fun aReasonIsNeededAndAFailureKeepsTheDialogUntilTheReportIsAccepted() = runComposeUiTest {
        links.result = AppResult.Failure(AppError.Network)
        val model = model()
        setContent { Report(model) }

        REASONS.forEach { onNodeWithText(it).assertIsDisplayed() }
        assertTrue(
            onNodeWithText(REASONS[0]).fetchSemanticsNode().boundsInRoot.top <
                onNodeWithText(REASONS[3]).fetchSemanticsNode().boundsInRoot.top,
        )
        onNodeWithText("Отправить").assertIsNotEnabled()
        assertTouchTargets()
        assertNoTextOverflow()

        onNodeWithText(REASONS[1]).performClick()
        onNodeWithText(REASONS[1]).assertIsSelected()
        onNode(hasSetTextAction()).performTextInput("  $COMMENT  ")
        onNodeWithText("Отправить").assertIsEnabled().performClick()
        waitForIdle()

        onNodeWithText("Нет связи. Проверьте интернет.").assertIsDisplayed()
        assertEquals(false, done)
        assertEquals(listOf("report:materials-all:WRONG_SUBJECT"), links.actions)

        links.result = AppResult.Success(Unit)
        onNodeWithText("Отправить").performClick()
        waitForIdle()

        onNodeWithText("Нет связи. Проверьте интернет.").assertDoesNotExist()
        assertTrue(done)
        assertEquals(2, links.actions.size)
    }

    @Test
    fun theCommentStopsAt500CharactersAndCancelCloses() = runComposeUiTest {
        setContent { Report(model()) }

        onNode(hasSetTextAction()).performTextInput("ж".repeat(600))
        onNodeWithText("500/500").assertIsDisplayed()

        onNodeWithText("Отмена").performClick()
        assertEquals(1, dismissed)
        assertTrue(links.actions.isEmpty())
    }

    /** As the Fragment host: the view model's busy flag, its failure until the next send, its done event. */
    @Composable
    private fun Report(model: SubjectLinksViewModel) {
        var failure by remember { mutableStateOf<AppError?>(null) }
        LaunchedEffect(model) {
            model.events.collect { event ->
                when (event) {
                    LinkEvent.Done, LinkEvent.Saved -> done = true
                    is LinkEvent.Failed -> failure = event.error
                }
            }
        }
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, NARROW_FONT_SCALE)) {
            ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
                val state by model.uiState.collectAsState()
                ReportLinkForm(
                    sending = state.busy,
                    error = failure,
                    onSend = { reason, comment ->
                        failure = null
                        model.report(LINK_ID, reason, comment.trim().ifEmpty { null })
                    },
                    onDismiss = { dismissed++ },
                )
            }
        }
    }

    private fun model() = SubjectLinksViewModel(
        SavedStateHandle(
            mapOf(
                SubjectLinksArgs.SUBJECT_ID to SubjectLinksSamples.scope.subjectId,
                SubjectLinksArgs.SUBJECT_NAME to SubjectLinksSamples.scope.subjectName,
                SubjectLinksArgs.PERIOD_KEY to SubjectLinksSamples.scope.periodKey,
            ),
        ),
        links,
    )

    private companion object {
        const val NARROW_FONT_SCALE = 1.3f
        const val LINK_ID = "materials-all"
        const val COMMENT = "Ссылка на курс другого семестра"
        val REASONS = listOf("Не открывается", "Другой предмет", "Спам", "Другое")
    }
}
