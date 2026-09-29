package dev.alllexey.itmowidgets.feature.reviews.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.TeacherReviewArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.OwnReviewStatus
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewDraft
import dev.alllexey.itmowidgets.core.schedule.TeacherLessons
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.social.presentation.FakeTeacherReviewsRepository
import dev.alllexey.itmowidgets.feature.social.presentation.ownReview
import dev.alllexey.itmowidgets.feature.social.presentation.teacherReviews
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewEditorViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val repository = FakeTeacherReviewsRepository()
    private val lessons = FakeTeacherLessonsGateway()

    @Test fun `a new review starts empty and anonymous`() = runTest(main.dispatcher) {
        val vm = model()

        val state = vm.uiState.value
        assertEquals("", state.subject)
        assertEquals("", state.text)
        assertTrue(state.anonymous)
        assertFalse(state.editing)
        assertFalse(state.canSave)
        assertTrue(state.showsMinimumHint)
        assertEquals(TEACHER, lessons.teacherIsu)
    }

    @Test fun `editing starts from the cached own review`() = runTest(main.dispatcher) {
        repository.cached = mapOf(TEACHER to teacherReviews(TEACHER, mine = ownReview(OwnReviewStatus.PENDING).copy(anonymous = false)))
        val vm = model()

        val state = vm.uiState.value
        assertEquals("Предмет", state.subject)
        assertEquals(ownReview().text, state.text)
        assertFalse(state.anonymous)
        assertTrue(state.editing)
        assertFalse(state.showsMinimumHint)
    }

    @Test fun `values restored after process death win over the cached review`() = runTest(main.dispatcher) {
        repository.cached = mapOf(TEACHER to teacherReviews(TEACHER, mine = ownReview()))
        val handle = handle()
        model(handle).apply { onTextChanged("Черновик после правки"); onAnonymousChanged(false) }
        repository.cached = mapOf(TEACHER to teacherReviews(TEACHER, mine = ownReview().copy(text = "Другой текст")))

        val restored = model(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))

        assertEquals("Черновик после правки", restored.uiState.value.text)
        assertFalse(restored.uiState.value.anonymous)
        assertTrue(restored.uiState.value.editing)
        assertTrue(restored.hasChanges())
    }

    @Test fun `subjects of own lessons become suggestions and a failed history stays silent`() = runTest(main.dispatcher) {
        lessons.answer(AppResult.Success(TeacherLessons(setOf(7L), listOf("Физика", "Механика"))))
        lessons.finish()
        assertEquals(listOf("Физика", "Механика"), model().uiState.value.suggestions)

        val failing = FakeTeacherLessonsGateway().apply { answer(AppResult.Failure(AppError.Network)); finish() }
        val failed = model(lessons = failing)
        val events = mutableListOf<ReviewEditorEvent>()
        backgroundScope.launch { failed.events.toList(events) }
        runCurrent()
        assertTrue(failed.uiState.value.suggestions.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test fun `suggestions grow as the schedule weeks answer`() = runTest(main.dispatcher) {
        val vm = model()
        assertTrue(vm.uiState.value.suggestions.isEmpty())

        lessons.answer(AppResult.Success(TeacherLessons(setOf(3L), listOf("Механика")))); runCurrent()
        assertEquals(listOf("Механика"), vm.uiState.value.suggestions)

        lessons.answer(AppResult.Success(TeacherLessons(setOf(7L, 3L), listOf("Физика", "Механика")))); runCurrent()
        assertEquals(listOf("Физика", "Механика"), vm.uiState.value.suggestions)
    }

    @Test fun `save sends the trimmed draft with the history flows`() = runTest(main.dispatcher) {
        lessons.answer(AppResult.Success(TeacherLessons(setOf(7L, 3L), listOf("Физика"))))
        lessons.finish()
        repository.saveResult = AppResult.Success(teacherReviews(TEACHER, mine = ownReview(OwnReviewStatus.PENDING)))
        val vm = model()

        vm.onSubjectChanged("  Физика  ")
        vm.onTextChanged("  $VALID_TEXT \r\n ")
        vm.onAnonymousChanged(false)
        vm.save(); runCurrent()

        assertEquals(TeacherReviewDraft("Физика", VALID_TEXT, anonymous = false, flowIds = setOf(7L, 3L)), repository.lastDraft)
        assertEquals(listOf("save:$TEACHER"), repository.actions)
        assertEquals(ReviewEditorEvent.Saved, vm.events.first())
        assertFalse(vm.uiState.value.saving)
    }

    @Test fun `a save during the history sends the flows collected so far`() = runTest(main.dispatcher) {
        repository.saveResult = AppResult.Success(teacherReviews(TEACHER))
        val vm = model()
        lessons.answer(AppResult.Success(TeacherLessons(setOf(3L), listOf("Механика")))); runCurrent()

        vm.onTextChanged(VALID_TEXT)
        vm.save(); runCurrent()
        lessons.answer(AppResult.Success(TeacherLessons(setOf(7L, 3L), listOf("Физика", "Механика")))); runCurrent()

        assertEquals(TeacherReviewDraft(null, VALID_TEXT, anonymous = true, flowIds = setOf(3L)), repository.lastDraft)
        assertEquals(ReviewEditorEvent.Saved, vm.events.first())
        assertEquals(listOf("Физика", "Механика"), vm.uiState.value.suggestions)
    }

    @Test fun `a blank subject is sent as none and a slow history does not delay the save`() = runTest(main.dispatcher) {
        repository.saveResult = AppResult.Success(teacherReviews(TEACHER))
        val vm = model()

        vm.onSubjectChanged("   ")
        vm.onTextChanged(VALID_TEXT)
        vm.save(); runCurrent()

        assertEquals(TeacherReviewDraft(null, VALID_TEXT, anonymous = true, flowIds = emptySet()), repository.lastDraft)
        assertEquals(ReviewEditorEvent.Saved, vm.events.first())
    }

    @Test fun `lengths out of bounds are field errors without a network call`() = runTest(main.dispatcher) {
        val vm = model()

        vm.onTextChanged("а".repeat(29)); vm.save(); runCurrent()
        assertEquals(UiText.Resource(R.string.review_text_too_short, listOf(30)), vm.uiState.value.textError)
        assertFalse(vm.uiState.value.showsMinimumHint)

        vm.onTextChanged("а".repeat(3001)); vm.save(); runCurrent()
        assertEquals(UiText.Resource(R.string.review_text_too_long, listOf(3000)), vm.uiState.value.textError)

        vm.onTextChanged(VALID_TEXT)
        assertNull(vm.uiState.value.textError)
        vm.onSubjectChanged("п".repeat(201)); vm.save(); runCurrent()
        assertEquals(UiText.Resource(R.string.review_subject_too_long, listOf(200)), vm.uiState.value.subjectError)
        assertNull(vm.uiState.value.textError)

        assertTrue(repository.actions.isEmpty())
    }

    @Test fun `a restricted save reports moderation and can be sent again`() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.mutationGate = { gate.await() }
        repository.saveResult = AppResult.Failure(AppError.Restricted)
        val vm = model()
        vm.onTextChanged(VALID_TEXT)

        vm.save(); runCurrent()
        assertTrue(vm.uiState.value.saving)
        assertFalse(vm.uiState.value.canSave)
        vm.save(); runCurrent()
        gate.complete(Unit); runCurrent()

        assertEquals(ReviewEditorEvent.Failed(UiText.Resource(R.string.common_error_restricted)), vm.events.first())
        assertFalse(vm.uiState.value.saving)
        assertEquals(1, repository.actions.size)
    }

    @Test fun `changes are known only after an edit`() = runTest(main.dispatcher) {
        repository.cached = mapOf(TEACHER to teacherReviews(TEACHER, mine = ownReview()))
        val vm = model()
        assertFalse(vm.hasChanges())

        vm.onAnonymousChanged(false)
        assertTrue(vm.hasChanges())
        vm.onAnonymousChanged(true)
        assertFalse(vm.hasChanges())
        vm.onSubjectChanged("Физика")
        assertTrue(vm.hasChanges())
    }

    private fun handle() = SavedStateHandle(mapOf(TeacherReviewArgs.TEACHER_ISU to TEACHER, TeacherReviewArgs.TEACHER_NAME to "Иванов Иван Иванович"))

    private fun TestScope.model(
        handle: SavedStateHandle = handle(),
        lessons: FakeTeacherLessonsGateway = this@ReviewEditorViewModelTest.lessons,
    ): ReviewEditorViewModel = ReviewEditorViewModel(handle, repository, lessons).also { runCurrent() }

    private companion object {
        const val TEACHER = 123456
        const val VALID_TEXT = "Понятно объясняет материал и подробно отвечает на вопросы."
    }
}
