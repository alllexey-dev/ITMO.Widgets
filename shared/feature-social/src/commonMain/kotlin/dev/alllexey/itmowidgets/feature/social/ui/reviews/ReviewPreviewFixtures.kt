package dev.alllexey.itmowidgets.feature.social.ui.reviews

import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.reviews.OwnReviewStatus
import dev.alllexey.itmowidgets.core.reviews.OwnTeacherReview
import dev.alllexey.itmowidgets.core.reviews.ReviewDate
import dev.alllexey.itmowidgets.core.reviews.ReviewOrigin
import dev.alllexey.itmowidgets.core.reviews.SummaryConfidence
import dev.alllexey.itmowidgets.core.reviews.SummaryScale
import dev.alllexey.itmowidgets.core.reviews.SummaryScaleKind
import dev.alllexey.itmowidgets.core.reviews.SummaryScaleValue
import dev.alllexey.itmowidgets.core.reviews.SummaryTag
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.reviews.TeacherReview
import dev.alllexey.itmowidgets.core.reviews.TeacherSummary
import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth

/** Synthetic reviews and summaries for the previews and host tests; no real person or text. */
internal object ReviewPreviewFixtures {

    val author = UserSummary(
        isu = 100002,
        name = "Иванова Анна Сергеевна",
        pictureUrl = null,
        groups = emptyList(),
        sharing = UserSharing(sport = false, schedule = false),
    )

    val named = TeacherReview(
        id = "r1",
        subject = "Математический анализ",
        written = ReviewDate.Month(YearMonth(2026, Month.SEPTEMBER)),
        text = "Понятно объясняет материал и подробно отвечает на вопросы.",
        score = 12,
        myVote = 1,
        origin = ReviewOrigin.Community(verified = true, author = author, reportedByMe = false),
    )

    val anonymous = TeacherReview(
        id = "r2",
        subject = "Дискретная математика",
        written = ReviewDate.Month(YearMonth(2025, Month.MAY)),
        text = "Строго принимает лабораторные, к защите нужно готовиться заранее.",
        score = -3,
        myVote = 0,
        origin = ReviewOrigin.Community(verified = false, author = null, reportedByMe = false),
    )

    val reported = anonymous.copy(
        id = "r3",
        subject = null,
        written = ReviewDate.Month(YearMonth(2026, Month.JANUARY)),
        text = "На занятиях было интересно.",
        score = 0,
        origin = ReviewOrigin.Community(verified = true, author = null, reportedByMe = true),
    )

    val copy = TeacherReview(
        id = "r4",
        subject = "Методы моделирования информационных систем",
        written = ReviewDate.Month(YearMonth(2025, Month.JANUARY)),
        text = "Понятно объясняет материал и подробно отвечает на вопросы.",
        score = 0,
        myVote = 0,
        origin = ReviewOrigin.Reviews(sourceTitle = "Отзывы ПИ", sourceUrl = "https://example.org/reviews/1"),
    )

    val oldCopy = copy.copy(
        id = "r5",
        subject = null,
        written = ReviewDate.BeforeYear(2023),
        text = "На занятиях было интересно.",
        origin = ReviewOrigin.Reviews(sourceTitle = null, sourceUrl = "https://example.org/reviews/2"),
    )

    val bareCopy = oldCopy.copy(id = "r6", written = null, text = "Хорошо структурированный курс.", score = 2)

    val long = TeacherReview(
        id = "r7",
        subject = "Математические методы моделирования сложных информационных систем и процессов управления",
        written = ReviewDate.Month(YearMonth(2026, Month.FEBRUARY)),
        text = "Преподаватель последовательно объясняет сложные темы, разбирает примеры и отвечает на вопросы " +
            "студентов. На практических занятиях можно обсудить разные подходы к решению задачи и понять, почему " +
            "один из них лучше подходит. Требования к отчётам по лабораторным работам строгие, но заранее понятные.",
        score = 128,
        myVote = -1,
        origin = ReviewOrigin.Community(
            verified = true,
            author = author.copy(name = "Константинопольская Александра Константиновна"),
            reportedByMe = false,
        ),
    )

    val unnamedAuthor = named.copy(
        id = "r8",
        origin = ReviewOrigin.Community(verified = false, author = author.copy(name = " "), reportedByMe = false),
    )

    val own = OwnTeacherReview(
        id = "o1",
        subject = "Математический анализ",
        text = "Лекции понятные, на консультациях можно разобрать любой вопрос.",
        anonymous = true,
        status = OwnReviewStatus.PENDING,
        reviewNote = null,
        score = 0,
        verified = false,
        written = ReviewDate.Month(YearMonth(2026, Month.SEPTEMBER)),
    )

    val ownRejected = own.copy(
        status = OwnReviewStatus.REJECTED,
        anonymous = false,
        reviewNote = "В отзыве есть личные данные",
    )

    val ownHidden = own.copy(status = OwnReviewStatus.HIDDEN, subject = null)

    val ownPublished = own.copy(status = OwnReviewStatus.PUBLISHED, score = 7, verified = true, anonymous = false)

    val ownPublishedUnverified = own.copy(status = OwnReviewStatus.PUBLISHED, score = -2, verified = false)

    val summary = TeacherSummary(
        reviewCount = 12,
        description = "Студенты чаще всего отмечают понятные лекции и честные оценки. Защита лабораторных строгая, " +
            "к ней нужно готовиться заранее.",
        pros = listOf("Понятно объясняет сложные темы", "Честно оценивает"),
        cons = listOf("Строгая защита лабораторных"),
        tags = listOf(SummaryTag.MANY_LABS, SummaryTag.STRICT_DEFENSE, SummaryTag.CLEAR_REQUIREMENTS),
        scales = listOf(
            SummaryScale(SummaryScaleKind.EXPLAINS, SummaryScaleValue.HIGH, "Лекции называют понятными"),
            SummaryScale(SummaryScaleKind.ATTITUDE, SummaryScaleValue.MEDIUM, "Отвечает на вопросы, но держит дистанцию"),
            SummaryScale(SummaryScaleKind.FAIRNESS, SummaryScaleValue.HIGH, "Оценки считают честными"),
            SummaryScale(SummaryScaleKind.STRICTNESS, SummaryScaleValue.HIGH, "Строго принимает лабораторные"),
            SummaryScale(SummaryScaleKind.WORKLOAD, SummaryScaleValue.NOT_ENOUGH_DATA, null),
        ),
        level = TeacherLevel.POSITIVE,
        confidence = SummaryConfidence.HIGH,
    )

    /** Low confidence hides the tone; without pros the cons start the block; no tags. */
    val sparseSummary = summary.copy(
        reviewCount = 3,
        description = "Отзывов пока немного, мнения расходятся.",
        pros = emptyList(),
        tags = emptyList(),
        scales = listOf(SummaryScale(SummaryScaleKind.WORKLOAD, SummaryScaleValue.LOW, "Домашних заданий немного")),
        level = TeacherLevel.MIXED,
        confidence = SummaryConfidence.LOW,
    )

    /** Every block long, for the narrow window at font 1.3. */
    val longSummary = summary.copy(
        reviewCount = 21,
        description = "Студенты отмечают, что преподаватель последовательно и понятно объясняет даже самые сложные " +
            "темы курса, подробно разбирает примеры на практических занятиях и всегда отвечает на вопросы. При этом " +
            "защита лабораторных работ проходит строго, а требования к отчётам высокие.",
        pros = listOf(
            "Понятно объясняет сложные темы и разбирает много примеров на практике",
            "Честно оценивает и заранее объявляет критерии",
            "Быстро отвечает на письма",
        ),
        cons = listOf("Строгая защита лабораторных работ с вопросами по всей теории курса", "Много домашних заданий"),
        tags = listOf(
            SummaryTag.MANY_LABS,
            SummaryTag.STRICT_DEFENSE,
            SummaryTag.CLEAR_REQUIREMENTS,
            SummaryTag.ATTENDANCE_REQUIRED,
            SummaryTag.INTERESTING_CLASSES,
            SummaryTag.QUICK_REPLIES,
        ),
        level = TeacherLevel.VERY_NEGATIVE,
    )
}
