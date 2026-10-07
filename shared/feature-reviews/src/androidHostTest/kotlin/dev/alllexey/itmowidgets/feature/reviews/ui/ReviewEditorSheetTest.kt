package dev.alllexey.itmowidgets.feature.reviews.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.navigation.TeacherReviewArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.OwnReviewStatus
import dev.alllexey.itmowidgets.core.reviews.OwnTeacherReview
import dev.alllexey.itmowidgets.core.reviews.ReviewDate
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewDraft
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.schedule.TeacherLessons
import dev.alllexey.itmowidgets.core.testing.FakeTeacherLessonsGateway
import dev.alllexey.itmowidgets.core.testing.FakeTeacherReviewsRepository
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReviewEditorViewModel
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertNoTextOverflow
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.YearMonth

/**
 * Every case of the deleted `ReviewEditorVisualTest` that the body owns, on the real `ReviewEditorViewModel` over fake
 * reviews and lessons, at 320 dp and font scale 1.3: the new and the edited form, suggestions that arrive with the
 * history and fill or clear the subject, anonymity, the minimum as a hint and then the error, the close button and the
 * discard question, text and cursor after a recreation and a restore, one save at a time, and `Отправить` above the
 * keyboard on a short window. The host's part (back, no tap outside or drag, the snackbar, closing after a save) is
 * `ReviewsHostsKoinTest` in `:app`; the looks are the `ReviewEditorSheetContent_*` goldens.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h891dp")
class ReviewEditorSheetTest {

    private val reviews = FakeTeacherReviewsRepository()
    private val lessons = FakeTeacherLessonsGateway()
    private var closes = 0
    private var discards = 0
    private var keeps = 0

    @Test
    fun aNewReviewIsAnonymousAndSuggestsSubjectsOnceTheHistoryAnswers() = runComposeUiTest {
        val model = model()
        setContent { Editor(model) }

        onNode(isHeading()).assertTextContains("Новый отзыв")
        onNodeWithText("Константинопольская А.\u00A0К.").assertIsDisplayed()
        onNodeWithContentDescription("Закрыть").assertIsDisplayed()
        onNodeWithTag(ReviewEditorSheetTestTags.ANONYMOUS).assertIsOn()
        onNodeWithText("Имя будет видно всем").assertDoesNotExist()
        onNodeWithTag(ReviewEditorSheetTestTags.SEND).assertTextContains("Отправить").assertIsNotEnabled()
        onNodeWithText("Не короче 30 символов").assertIsDisplayed()
        onNodeWithText("0/3000").assertIsDisplayed()
        onNodeWithTag(ReviewEditorSheetTestTags.SUGGESTIONS).assertDoesNotExist()

        lessons.answer(AppResult.Success(TeacherLessons(FLOWS, SUBJECTS)))
        waitForIdle()
        SUBJECTS.forEach { chip(it).assertIsNotSelected() }

        chip(SUBJECTS[1]).performScrollTo().performClick()
        assertEquals(SUBJECTS[1], model.uiState.value.subject)
        chip(SUBJECTS[1]).assertIsSelected()
        onNodeWithTag(ReviewEditorSheetTestTags.SUBJECT).assert(editableText(SUBJECTS[1]))
        onNodeWithTag(ReviewEditorSheetTestTags.SUBJECT).assert(selection(TextRange(SUBJECTS[1].length)))

        chip(SUBJECTS[1]).performScrollTo().performClick()
        assertEquals("", model.uiState.value.subject)
        chip(SUBJECTS[1]).assertIsNotSelected()

        onNodeWithTag(ReviewEditorSheetTestTags.TEXT).performTextInput(REVIEW_TEXT)
        assertEquals(REVIEW_TEXT, model.uiState.value.text)
        onNodeWithTag(ReviewEditorSheetTestTags.SEND).assertIsEnabled()
        onNodeWithText("Не короче 30 символов").assertDoesNotExist()
        assertTouchTargets()
        assertNoTextOverflow()
    }

    @Test
    fun turningAnonymityOffWarnsThatTheNameIsPublic() = runComposeUiTest {
        val model = model()
        setContent { Editor(model) }

        onNodeWithTag(ReviewEditorSheetTestTags.ANONYMOUS).performClick()

        onNodeWithTag(ReviewEditorSheetTestTags.ANONYMOUS).assertIsOff()
        onNodeWithText("Имя будет видно всем").assertIsDisplayed()
        assertEquals(false, model.uiState.value.anonymous)
    }

    @Test
    fun aShortTextIsAnErrorOnlyAfterSendAndTheCounterCountsToTheLimit() = runComposeUiTest {
        val model = model()
        setContent { Editor(model) }

        onNodeWithTag(ReviewEditorSheetTestTags.TEXT).performTextInput("а".repeat(29))
        onNodeWithText("29/3000").assertIsDisplayed()
        onNodeWithText("Не короче 30 символов").assertIsDisplayed()
        onNodeWithTag(ReviewEditorSheetTestTags.TEXT).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))

        onNodeWithTag(ReviewEditorSheetTestTags.SEND).performClick()

        onNodeWithTag(ReviewEditorSheetTestTags.TEXT).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        onNodeWithText("Не короче 30 символов").assertIsDisplayed()
        assertTrue(reviews.actions.isEmpty())
    }

    @Test
    fun editingStartsFromTheOwnReview() = runComposeUiTest {
        reviews.cached = mapOf(TEACHER_ISU to reviews(own()))
        val model = model()
        setContent { Editor(model) }

        onNode(isHeading()).assertTextContains("Изменить отзыв")
        onNodeWithTag(ReviewEditorSheetTestTags.SUBJECT).assert(editableText(SUBJECTS[0]))
        onNodeWithTag(ReviewEditorSheetTestTags.TEXT).assert(editableText(REVIEW_TEXT))
        onNodeWithTag(ReviewEditorSheetTestTags.ANONYMOUS).assertIsOff()
        onNodeWithText("Имя будет видно всем").assertIsDisplayed()
        onNodeWithTag(ReviewEditorSheetTestTags.SEND).assertTextContains("Сохранить").assertIsEnabled()
        assertTouchTargets()
        assertNoTextOverflow()
    }

    @Test
    fun closeGoesToTheHostAndTheDiscardQuestionAnswersBothWays() = runComposeUiTest {
        val model = model()
        var discarding by mutableStateOf(false)
        setContent { Editor(model, discarding = discarding) }

        onNodeWithContentDescription("Закрыть").performClick()
        assertEquals(1, closes)
        onNodeWithText("Не сохранять отзыв?").assertDoesNotExist()

        discarding = true
        waitForIdle()
        onNodeWithText("Не сохранять отзыв?").assertIsDisplayed()
        onNodeWithText("Отмена").performClick()
        assertEquals(1, keeps)
        onNodeWithText("Не сохранять").performClick()
        assertEquals(1, discards)
    }

    @Test
    fun aRecreatedOrRestoredEditorShowsTheTypedTextWithTheCursorAtItsEnd() = runComposeUiTest {
        val handle = handle()
        val model = model(handle)
        model.onTextChanged(REVIEW_TEXT)
        model.onAnonymousChanged(false)
        var restored by mutableStateOf(model)
        var generation by mutableIntStateOf(0)
        setContent { key(generation) { Editor(restored) } }

        onNodeWithTag(ReviewEditorSheetTestTags.TEXT).assert(editableText(REVIEW_TEXT))
        onNodeWithTag(ReviewEditorSheetTestTags.TEXT).assert(selection(TextRange(REVIEW_TEXT.length)))

        // A process death: a new view model from the saved handle, a new composition.
        restored = model(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
        generation++
        waitForIdle()

        onNodeWithTag(ReviewEditorSheetTestTags.TEXT).assert(editableText(REVIEW_TEXT))
        onNodeWithTag(ReviewEditorSheetTestTags.TEXT).assert(selection(TextRange(REVIEW_TEXT.length)))
        onNodeWithTag(ReviewEditorSheetTestTags.ANONYMOUS).assertIsOff()

        onNodeWithTag(ReviewEditorSheetTestTags.TEXT).performTextInput("!")
        assertEquals("$REVIEW_TEXT!", restored.uiState.value.text)
    }

    @Test
    fun aSaveRunsOnceAndAFailureLetsTheViewerSendAgain() = runComposeUiTest {
        val gate = CompletableDeferred<Unit>()
        reviews.mutationGate = { gate.await() }
        reviews.saveResult = AppResult.Failure(AppError.Network)
        lessons.answer(AppResult.Success(TeacherLessons(FLOWS, SUBJECTS)))
        val model = model()
        setContent { Editor(model) }
        onNodeWithTag(ReviewEditorSheetTestTags.TEXT).performTextInput(REVIEW_TEXT)

        onNodeWithTag(ReviewEditorSheetTestTags.SEND).performClick()
        onNodeWithTag(ReviewEditorSheetTestTags.SEND).performClick()
        assertEquals(listOf("save:$TEACHER_ISU"), reviews.actions)
        assertTrue(model.uiState.value.saving)

        gate.complete(Unit)
        waitForIdle()
        assertEquals(false, model.uiState.value.saving)
        assertEquals(TeacherReviewDraft(null, REVIEW_TEXT, anonymous = true, flowIds = FLOWS), reviews.lastDraft)
        onNodeWithTag(ReviewEditorSheetTestTags.SEND).assertIsEnabled().performClick()
        assertEquals(2, reviews.actions.size)
    }

    @Test
    fun sendStaysAboveTheKeyboardOnANarrowShortWindow() = runComposeUiTest {
        lessons.answer(AppResult.Success(TeacherLessons(FLOWS, SUBJECTS)))
        val model = model()
        // The sheet over the keyboard: SOFT_INPUT_ADJUST_RESIZE leaves it this much of the 320 dp wide window.
        setContent { Editor(model, height = ABOVE_KEYBOARD) }
        onNodeWithTag(ReviewEditorSheetTestTags.TEXT).performTextInput(LONG_TEXT)
        waitForIdle()

        val send = onNodeWithTag(ReviewEditorSheetTestTags.SEND).assertIsDisplayed().getBoundsInRoot()
        assertTrue(send.bottom <= ABOVE_KEYBOARD, "Send ends at ${send.bottom}, under the keyboard at $ABOVE_KEYBOARD")
        assertTrue(send.bottom - send.top >= 40.dp)
        assertTouchTargets()
    }

    @Composable
    private fun Editor(model: ReviewEditorViewModel, discarding: Boolean = false, height: Dp = SHEET_HEIGHT) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, NARROW_FONT_SCALE)) {
            ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
                Box(Modifier.size(NARROW_WIDTH, height)) {
                    val form by model.uiState.collectAsState()
                    ReviewEditorSheet(
                        form = form,
                        teacherName = model.teacherName,
                        discarding = discarding,
                        actions = ReviewEditorActions(
                            onSubjectChange = model::onSubjectChanged,
                            onTextChange = model::onTextChanged,
                            onAnonymousChange = model::onAnonymousChanged,
                            onSave = model::save,
                            onClose = { closes++ },
                            onDiscard = { discards++ },
                            onKeepEditing = { keeps++ },
                        ),
                    )
                }
            }
        }
    }

    private fun ComposeUiTest.chip(subject: String) =
        onNode(hasText(subject) and hasAnyAncestor(hasTestTag(ReviewEditorSheetTestTags.SUGGESTIONS)))

    private fun editableText(text: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(text))

    private fun selection(range: TextRange) =
        SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, range)

    private fun handle() = SavedStateHandle(
        mapOf(TeacherReviewArgs.TEACHER_ISU to TEACHER_ISU, TeacherReviewArgs.TEACHER_NAME to TEACHER_NAME),
    )

    private fun model(handle: SavedStateHandle = handle()) = ReviewEditorViewModel(handle, reviews, lessons)

    private fun reviews(mine: OwnTeacherReview?) = TeacherReviews(TEACHER_ISU, emptyList(), mine,
        canWrite = true, canVote = true, canReport = true, knownTeacher = true)

    private fun own() = OwnTeacherReview("own", SUBJECTS[0], REVIEW_TEXT, anonymous = false,
        status = OwnReviewStatus.PUBLISHED, reviewNote = null, score = 2, verified = true,
        written = ReviewDate.Month(YearMonth(2026, 9)))

    private companion object {
        const val NARROW_FONT_SCALE = 1.3f
        val NARROW_WIDTH = 320.dp
        val SHEET_HEIGHT = 802.dp
        val ABOVE_KEYBOARD = 360.dp
        const val TEACHER_ISU = 100001
        const val TEACHER_NAME = "Константинопольская Александра Константиновна"
        val FLOWS = setOf(7101L, 7102L)
        val SUBJECTS = listOf("Математический анализ", "Линейная алгебра", "Дискретная математика")
        const val REVIEW_TEXT = "Лекции понятные, на практике разбираем задачи из контрольных."
        val LONG_TEXT = (1..12).joinToString(" ") {
            "Абзац $it: преподаватель подробно объясняет материал и отвечает на вопросы."
        }
    }
}
