package dev.alllexey.itmowidgets.feature.social.presentation

import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.OwnReviewStatus
import dev.alllexey.itmowidgets.core.reviews.OwnTeacherReview
import dev.alllexey.itmowidgets.core.reviews.ReviewDate
import dev.alllexey.itmowidgets.core.reviews.ReviewOrigin
import dev.alllexey.itmowidgets.core.reviews.ReviewReportReason
import dev.alllexey.itmowidgets.core.reviews.SummaryConfidence
import dev.alllexey.itmowidgets.core.reviews.SummaryScale
import dev.alllexey.itmowidgets.core.reviews.SummaryScaleKind
import dev.alllexey.itmowidgets.core.reviews.SummaryScaleValue
import dev.alllexey.itmowidgets.core.reviews.SummaryTag
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.reviews.TeacherReview
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewDraft
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewsRepository
import dev.alllexey.itmowidgets.core.reviews.TeacherSummary
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
    summary: TeacherSummary? = null,
) = TeacherReviews(isu, reviews, mine, canWrite, canVote, canReport, knownTeacher, summary)

internal fun teacherSummary(
    reviewCount: Int = 12,
    level: TeacherLevel = TeacherLevel.POSITIVE,
    confidence: SummaryConfidence = SummaryConfidence.MEDIUM,
    description: String = "Понятно объясняет и честно оценивает, но строго принимает лабораторные.",
    pros: List<String> = listOf("Понятные лекции", "Честные оценки"),
    cons: List<String> = listOf("Строгая защита лабораторных"),
    tags: List<SummaryTag> = listOf(SummaryTag.MANY_LABS, SummaryTag.STRICT_DEFENSE),
    scales: List<SummaryScale> = listOf(
        SummaryScale(SummaryScaleKind.EXPLAINS, SummaryScaleValue.HIGH, "Хвалят понятные лекции"),
        SummaryScale(SummaryScaleKind.ATTITUDE, SummaryScaleValue.MEDIUM, "Ровное отношение"),
        SummaryScale(SummaryScaleKind.FAIRNESS, SummaryScaleValue.HIGH, "Оценки считают честными"),
        SummaryScale(SummaryScaleKind.STRICTNESS, SummaryScaleValue.HIGH, "Строго на защите"),
        SummaryScale(SummaryScaleKind.WORKLOAD, SummaryScaleValue.NOT_ENOUGH_DATA, null),
    ),
) = TeacherSummary(reviewCount, description, pros, cons, tags, scales, level, confidence)

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
    var lastComment: String? = null
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

    override suspend fun report(isu: Int, reviewId: String, reason: ReviewReportReason, comment: String?): AppResult<TeacherReviews> {
        lastComment = comment
        return mutate("report:$isu:$reviewId:$reason") { reportResult }
    }

    private suspend fun mutate(action: String, result: () -> AppResult<TeacherReviews>): AppResult<TeacherReviews> {
        actions += action
        mutationGate()
        return result().also { if (it is AppResult.Success) updates.emit(it.value) }
    }
}
