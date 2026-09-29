package dev.alllexey.itmowidgets.feature.social.presentation

import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.OwnReviewStatus
import dev.alllexey.itmowidgets.core.reviews.OwnTeacherReview
import dev.alllexey.itmowidgets.core.reviews.ReviewDate
import dev.alllexey.itmowidgets.core.reviews.ReviewOrigin
import dev.alllexey.itmowidgets.core.reviews.ReviewReportReason
import dev.alllexey.itmowidgets.core.reviews.TeacherReview
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewDraft
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewsRepository
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

internal fun teacherReviews(
    isu: Int,
    reviews: List<TeacherReview> = emptyList(),
    mine: OwnTeacherReview? = null,
    canWrite: Boolean = true,
    canVote: Boolean = true,
    canReport: Boolean = true,
    knownTeacher: Boolean = true,
) = TeacherReviews(isu, reviews, mine, canWrite, canVote, canReport, knownTeacher)

internal fun copiedReview(id: String) = TeacherReview(id, "Предмет", ReviewDate.BeforeYear(2023), "Отзыв $id", score = 0,
    myVote = 0, origin = ReviewOrigin.Reviews("Источник", "https://example.org/reviews/$id"))

internal fun communityReview(id: String, author: UserSummary? = null) = TeacherReview(id, "Предмет",
    ReviewDate.Month(YearMonth.of(2026, 9)), "Отзыв $id", score = 0, myVote = 0,
    origin = ReviewOrigin.Community(verified = false, author = author, reportedByMe = false))

internal fun ownReview(status: OwnReviewStatus = OwnReviewStatus.PUBLISHED) = OwnTeacherReview(
    id = "own",
    subject = "Предмет",
    text = "Понятно объясняет материал и подробно отвечает на вопросы.",
    anonymous = true,
    status = status,
    reviewNote = if (status == OwnReviewStatus.REJECTED) "Причина отклонения" else null,
    score = 0,
    verified = false,
    written = ReviewDate.Month(YearMonth.of(2026, 9)),
)

internal class FakeTeacherReviewsRepository : TeacherReviewsRepository {
    var results: Map<Int, AppResult<TeacherReviews>> = emptyMap()
    var cached: Map<Int, TeacherReviews> = emptyMap()
    var gate: suspend () -> Unit = {}
    var calls = 0
        private set
    val updates = MutableSharedFlow<TeacherReviews>(extraBufferCapacity = 8)
    var saveResult: AppResult<TeacherReviews> = AppResult.Failure(AppError.CustomServicesDisabled)
    var deleteResult: AppResult<TeacherReviews> = AppResult.Failure(AppError.CustomServicesDisabled)
    var voteResult: AppResult<TeacherReviews> = AppResult.Failure(AppError.CustomServicesDisabled)
    var reportResult: AppResult<TeacherReviews> = AppResult.Failure(AppError.CustomServicesDisabled)
    var mutationGate: suspend () -> Unit = {}
    val actions = mutableListOf<String>()
    var lastDraft: TeacherReviewDraft? = null
        private set

    override fun cachedReviews(isu: Int): TeacherReviews? = cached[isu]

    override suspend fun reviews(isu: Int): AppResult<TeacherReviews> {
        calls += 1
        gate()
        return results[isu] ?: AppResult.Failure(AppError.CustomServicesDisabled)
    }

    override fun observeUpdates(): Flow<TeacherReviews> = updates

    override suspend fun save(isu: Int, draft: TeacherReviewDraft): AppResult<TeacherReviews> {
        lastDraft = draft
        return mutate("save:$isu") { saveResult }
    }

    override suspend fun delete(isu: Int) = mutate("delete:$isu") { deleteResult }

    override suspend fun vote(isu: Int, reviewId: String, value: Int) = mutate("vote:$isu:$reviewId:$value") { voteResult }

    override suspend fun report(isu: Int, reviewId: String, reason: ReviewReportReason, comment: String?) =
        mutate("report:$isu:$reviewId:$reason") { reportResult }

    private suspend fun mutate(action: String, result: () -> AppResult<TeacherReviews>): AppResult<TeacherReviews> {
        actions += action
        mutationGate()
        return result().also { if (it is AppResult.Success) updates.emit(it.value) }
    }
}
