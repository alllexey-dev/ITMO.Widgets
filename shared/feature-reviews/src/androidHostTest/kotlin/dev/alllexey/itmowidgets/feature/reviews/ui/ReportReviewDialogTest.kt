package dev.alllexey.itmowidgets.feature.reviews.ui

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
import dev.alllexey.itmowidgets.core.navigation.TeacherReviewArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.testing.FakeTeacherReviewsRepository
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReportReviewEvent
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReportReviewViewModel
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertNoTextOverflow
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The report case of the deleted `ReviewEditorVisualTest` on the real `ReportReviewViewModel`, wired as
 * `ReportReviewDialogFragment` wires it, at font scale 1.3: the four reasons in order, `Отправить` only with a reason,
 * a failure under the comment that keeps the dialog and goes with the next send, closing once the report is accepted,
 * the comment's 500 characters and the cancel button. The looks are the `ReportReviewDialog_*` goldens.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h891dp")
class ReportReviewDialogTest {

    private val reviews = FakeTeacherReviewsRepository()
    private var done = false
    private var dismissed = 0

    @Test
    fun aReasonIsNeededAndAFailureKeepsTheDialogUntilTheReportIsAccepted() = runComposeUiTest {
        reviews.reportResult = AppResult.Failure(AppError.Network)
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
        onNode(hasSetTextAction()).performTextInput(COMMENT)
        onNodeWithText("Отправить").assertIsEnabled().performClick()
        waitForIdle()

        onNodeWithText("Нет связи. Проверьте интернет.").assertIsDisplayed()
        assertEquals(false, done)
        assertEquals(listOf("report:$TEACHER_ISU:$REVIEW_ID:WRONG_TEACHER"), reviews.actions)
        assertEquals(COMMENT, reviews.lastComment)

        reviews.reportResult = AppResult.Success(TeacherReviews(TEACHER_ISU, emptyList(), null, canWrite = true,
            canVote = true, canReport = true, knownTeacher = true))
        onNodeWithText("Отправить").performClick()
        waitForIdle()

        onNodeWithText("Нет связи. Проверьте интернет.").assertDoesNotExist()
        assertTrue(done)
        assertEquals(2, reviews.actions.size)
    }

    @Test
    fun theCommentStopsAt500CharactersAndCancelCloses() = runComposeUiTest {
        setContent { Report(model()) }

        onNode(hasSetTextAction()).performTextInput("ж".repeat(600))
        onNodeWithText("500/500").assertIsDisplayed()

        onNodeWithText("Отмена").performClick()
        assertEquals(1, dismissed)
        assertTrue(reviews.actions.isEmpty())
    }

    /** As the Fragment host: the view model's sending flag, its failure until the next send, its done event. */
    @Composable
    private fun Report(model: ReportReviewViewModel) {
        var failure by remember { mutableStateOf<AppError?>(null) }
        LaunchedEffect(model) {
            model.events.collect { event ->
                when (event) {
                    ReportReviewEvent.Done -> done = true
                    is ReportReviewEvent.Failed -> failure = event.error
                }
            }
        }
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, NARROW_FONT_SCALE)) {
            ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
                val state by model.uiState.collectAsState()
                ReportReviewForm(
                    sending = state.sending,
                    error = failure,
                    onSend = { reason, comment ->
                        failure = null
                        model.send(reason, comment)
                    },
                    onDismiss = { dismissed++ },
                )
            }
        }
    }

    private fun model() = ReportReviewViewModel(
        SavedStateHandle(
            mapOf(
                TeacherReviewArgs.TEACHER_ISU to TEACHER_ISU,
                TeacherReviewArgs.TEACHER_NAME to "Константинопольская Александра Константиновна",
                TeacherReviewArgs.REVIEW_ID to REVIEW_ID,
            ),
        ),
        reviews,
    )

    private companion object {
        const val NARROW_FONT_SCALE = 1.3f
        const val TEACHER_ISU = 100001
        const val REVIEW_ID = "0f8fad5b-d9cb-469f-a165-70867728950e"
        const val COMMENT = "Ведёт другой предмет"
        val REASONS = listOf("Оскорбления", "Не тот преподаватель", "Спам", "Другое")
    }
}
