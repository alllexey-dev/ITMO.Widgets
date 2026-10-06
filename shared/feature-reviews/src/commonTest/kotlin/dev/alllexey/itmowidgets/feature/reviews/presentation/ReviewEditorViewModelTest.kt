package dev.alllexey.itmowidgets.feature.reviews.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.navigation.TeacherReviewArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.OwnReviewStatus
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewDraft
import dev.alllexey.itmowidgets.core.schedule.TeacherLessons
import dev.alllexey.itmowidgets.core.testing.FakeTeacherLessonsGateway
import dev.alllexey.itmowidgets.core.testing.FakeTeacherReviewsRepository
import dev.alllexey.itmowidgets.core.testing.ownReview
import dev.alllexey.itmowidgets.core.testing.teacherReviews
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewEditorViewModelTest {
    private val main = TestMainDispatcher()
    private val repository = FakeTeacherReviewsRepository()
    private val lessons = FakeTeacherLessonsGateway()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test fun aNewReviewStartsEmptyAndAnonymous() = runTest(main.dispatcher) {
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

    @Test fun editingStartsFromTheCachedOwnReview() = runTest(main.dispatcher) {
        repository.cached = mapOf(TEACHER to teacherReviews(TEACHER, mine = ownReview(OwnReviewStatus.PENDING).copy(anonymous = false)))
        val vm = model()

        val state = vm.uiState.value
        assertEquals("Предмет", state.subject)
        assertEquals(ownReview().text, state.text)
        assertFalse(state.anonymous)
        assertTrue(state.editing)
        assertFalse(state.showsMinimumHint)
    }

    @Test fun valuesRestoredAfterProcessDeathWinOverTheCachedReview() = runTest(main.dispatcher) {
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

    @Test fun anUntouchedEditorRestoredAfterProcessDeathKeepsItsOpeningValues() = runTest(main.dispatcher) {
        val handle = handle()
        model(handle)
        repository.cached = mapOf(TEACHER to teacherReviews(TEACHER, mine = ownReview()))

        val restored = model(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))

        assertEquals("", restored.uiState.value.text)
        assertFalse(restored.uiState.value.editing)
        assertFalse(restored.hasChanges())
    }

    @Test fun subjectsOfOwnLessonsBecomeSuggestionsAndAFailedHistoryStaysSilent() = runTest(main.dispatcher) {
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

    @Test fun suggestionsGrowAsTheScheduleWeeksAnswer() = runTest(main.dispatcher) {
        val vm = model()
        assertTrue(vm.uiState.value.suggestions.isEmpty())

        lessons.answer(AppResult.Success(TeacherLessons(setOf(3L), listOf("Механика")))); runCurrent()
        assertEquals(listOf("Механика"), vm.uiState.value.suggestions)

        lessons.answer(AppResult.Success(TeacherLessons(setOf(7L, 3L), listOf("Физика", "Механика")))); runCurrent()
        assertEquals(listOf("Физика", "Механика"), vm.uiState.value.suggestions)
    }

    @Test fun saveSendsTheTrimmedDraftWithTheHistoryFlows() = runTest(main.dispatcher) {
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

    @Test fun aSaveDuringTheHistorySendsTheFlowsCollectedSoFar() = runTest(main.dispatcher) {
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

    @Test fun aBlankSubjectIsSentAsNoneAndASlowHistoryDoesNotDelayTheSave() = runTest(main.dispatcher) {
        repository.saveResult = AppResult.Success(teacherReviews(TEACHER))
        val vm = model()

        vm.onSubjectChanged("   ")
        vm.onTextChanged(VALID_TEXT)
        vm.save(); runCurrent()

        assertEquals(TeacherReviewDraft(null, VALID_TEXT, anonymous = true, flowIds = emptySet()), repository.lastDraft)
        assertEquals(ReviewEditorEvent.Saved, vm.events.first())
    }

    @Test fun lengthsOutOfBoundsAreFieldErrorsWithoutANetworkCall() = runTest(main.dispatcher) {
        val vm = model()

        vm.onTextChanged("а".repeat(29)); vm.save(); runCurrent()
        assertEquals(ReviewFieldError.TEXT_TOO_SHORT, vm.uiState.value.textError)
        assertFalse(vm.uiState.value.showsMinimumHint)

        vm.onTextChanged("а".repeat(3001)); vm.save(); runCurrent()
        assertEquals(ReviewFieldError.TEXT_TOO_LONG, vm.uiState.value.textError)

        vm.onTextChanged(VALID_TEXT)
        assertNull(vm.uiState.value.textError)
        vm.onSubjectChanged("п".repeat(201)); vm.save(); runCurrent()
        assertEquals(ReviewFieldError.SUBJECT_TOO_LONG, vm.uiState.value.subjectError)
        assertNull(vm.uiState.value.textError)

        assertTrue(repository.actions.isEmpty())
    }

    /** An emoji outside the basic plane is two UTF-16 units but one code point, as Backend counts it. */
    @Test fun emojiCountOnceAtTheTextBounds() = runTest(main.dispatcher) {
        repository.saveResult = AppResult.Success(teacherReviews(TEACHER))
        val vm = model()

        vm.onTextChanged(EMOJI.repeat(29)); vm.save(); runCurrent()
        assertEquals(ReviewFieldError.TEXT_TOO_SHORT, vm.uiState.value.textError)

        vm.onTextChanged(EMOJI.repeat(30))
        assertFalse(vm.uiState.value.showsMinimumHint)
        vm.save(); runCurrent()
        assertNull(vm.uiState.value.textError)
        assertEquals(EMOJI.repeat(30), repository.lastDraft?.text)

        vm.onTextChanged(EMOJI.repeat(3001)); vm.save(); runCurrent()
        assertEquals(ReviewFieldError.TEXT_TOO_LONG, vm.uiState.value.textError)

        vm.onTextChanged(EMOJI.repeat(3000)); vm.save(); runCurrent()
        assertNull(vm.uiState.value.textError)
        assertEquals(EMOJI.repeat(3000), repository.lastDraft?.text)
        assertEquals(listOf("save:$TEACHER", "save:$TEACHER"), repository.actions)
    }

    @Test fun aSavedEventWaitsForTheNextCollector() = runTest(main.dispatcher) {
        repository.saveResult = AppResult.Success(teacherReviews(TEACHER))
        val vm = model()
        val first = backgroundScope.launch { vm.events.collect {} }
        runCurrent()
        first.cancel(); runCurrent()

        vm.onTextChanged(VALID_TEXT)
        vm.save(); runCurrent()

        assertEquals(ReviewEditorEvent.Saved, vm.events.first())
    }

    @Test fun aRestrictedSaveReportsModerationAndCanBeSentAgain() = runTest(main.dispatcher) {
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

        assertEquals(ReviewEditorEvent.Failed(AppError.Restricted), vm.events.first())
        assertFalse(vm.uiState.value.saving)
        assertEquals(1, repository.actions.size)
    }

    @Test fun changesAreKnownOnlyAfterAnEdit() = runTest(main.dispatcher) {
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
        const val EMOJI = "\uD83D\uDE00"
    }
}
