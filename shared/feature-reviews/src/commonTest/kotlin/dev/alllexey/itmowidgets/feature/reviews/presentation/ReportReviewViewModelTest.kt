package dev.alllexey.itmowidgets.feature.reviews.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.navigation.TeacherReviewArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.ReviewReportReason
import dev.alllexey.itmowidgets.core.testing.FakeTeacherReviewsRepository
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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ReportReviewViewModelTest {
    private val main = TestMainDispatcher()
    private val repository = FakeTeacherReviewsRepository()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test fun aBlankCommentIsSentAsNoneAndSuccessIsDone() = runTest(main.dispatcher) {
        repository.reportResult = AppResult.Success(teacherReviews(TEACHER))
        val vm = model()

        vm.send(ReviewReportReason.WRONG_TEACHER, "   "); runCurrent()

        assertEquals(listOf("report:$TEACHER:$REVIEW:WRONG_TEACHER"), repository.actions)
        assertNull(repository.lastComment)
        assertEquals(ReportReviewEvent.Done, vm.events.first())
    }

    @Test fun aCommentIsTrimmed() = runTest(main.dispatcher) {
        repository.reportResult = AppResult.Success(teacherReviews(TEACHER))
        model().send(ReviewReportReason.OTHER, "  Не по теме  "); runCurrent()

        assertEquals("Не по теме", repository.lastComment)
    }

    @Test fun aFailureCanBeSentAgain() = runTest(main.dispatcher) {
        repository.reportResult = AppResult.Failure(AppError.Network)
        val vm = model()

        vm.send(ReviewReportReason.SPAM, null); runCurrent()
        assertEquals(ReportReviewEvent.Failed(AppError.Network), vm.events.first())
        assertFalse(vm.uiState.value.sending)

        repository.reportResult = AppResult.Success(teacherReviews(TEACHER))
        vm.send(ReviewReportReason.SPAM, null); runCurrent()
        assertEquals(ReportReviewEvent.Done, vm.events.first())
        assertEquals(2, repository.actions.size)
    }

    @Test fun aReportInFlightBlocksASecondOne() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.mutationGate = { gate.await() }
        repository.reportResult = AppResult.Success(teacherReviews(TEACHER))
        val vm = model()

        vm.send(ReviewReportReason.OFFENSIVE, null); runCurrent()
        assertTrue(vm.uiState.value.sending)
        vm.send(ReviewReportReason.OFFENSIVE, null); runCurrent()
        gate.complete(Unit); runCurrent()

        assertEquals(1, repository.actions.size)
        assertFalse(vm.uiState.value.sending)
    }

    private fun model() = ReportReviewViewModel(SavedStateHandle(mapOf(
        TeacherReviewArgs.TEACHER_ISU to TEACHER, TeacherReviewArgs.TEACHER_NAME to "Иванов Иван Иванович", TeacherReviewArgs.REVIEW_ID to REVIEW,
    )), repository)

    private companion object {
        const val TEACHER = 123456
        const val REVIEW = "0f8fad5b-d9cb-469f-a165-70867728950e"
    }
}
