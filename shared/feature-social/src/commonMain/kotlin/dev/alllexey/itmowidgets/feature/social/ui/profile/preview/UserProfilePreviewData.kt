package dev.alllexey.itmowidgets.feature.social.ui.profile.preview

import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.reviews.ReviewDate
import dev.alllexey.itmowidgets.core.reviews.ReviewOrigin
import dev.alllexey.itmowidgets.core.reviews.SummaryConfidence
import dev.alllexey.itmowidgets.core.reviews.SummaryScale
import dev.alllexey.itmowidgets.core.reviews.SummaryScaleKind
import dev.alllexey.itmowidgets.core.reviews.SummaryScaleValue
import dev.alllexey.itmowidgets.core.reviews.SummaryTag
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.reviews.TeacherReview
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.reviews.TeacherSummary
import dev.alllexey.itmowidgets.feature.social.domain.model.Person
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonEducation
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonPosition
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonRoom
import dev.alllexey.itmowidgets.feature.social.presentation.ProfilePart
import dev.alllexey.itmowidgets.feature.social.presentation.SocialBlock
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileUiState
import dev.alllexey.itmowidgets.feature.social.presentation.userProfileUiState
import kotlinx.datetime.YearMonth

/**
 * Synthetic people for the profile previews and host tests, the fixtures LC-1c captured the XML references with:
 * a teacher with long texts and an AI summary, a student who is a friend, and the same student under every
 * relationship. Each page goes through [userProfileUiState], as the ViewModel's does; no real person or text.
 */
internal object UserProfilePreviewData {

    const val ISU = 100001
    const val LONG_NAME = "Александра Константиновна Константинопольская"
    const val BACKEND_NAME = "Соколов Артём Игоревич"
    private const val LONG_DEPARTMENT =
        "Факультет информационных технологий и программирования, кафедра прикладной математики и теоретической информатики"
    private const val UNTITLED_DEPARTMENT = "Институт международного развития и партнёрства"
    private const val LONG_SUBJECT = "Математические методы моделирования сложных информационных систем"
    private const val LONG_REVIEW = "Преподаватель последовательно объясняет сложные темы, разбирает примеры и отвечает на " +
        "вопросы студентов. На практических занятиях можно обсудить разные подходы к решению задачи и понять, " +
        "почему один из них лучше подходит."

    val teacher = Person(
        ISU,
        LONG_NAME,
        null,
        listOf(PersonPosition("Доцент", LONG_DEPARTMENT), PersonPosition(null, UNTITLED_DEPARTMENT)),
        listOf(PersonRoom("405", "Кронверкский проспект, 49")),
        emptyList(),
    )

    val student = teacher.copy(
        positions = emptyList(),
        rooms = emptyList(),
        education = listOf(PersonEducation("M3234", 2, "ФИТиП")),
    )

    val summary = TeacherSummary(
        reviewCount = 12,
        description = "Студенты чаще всего отмечают понятные лекции и честные оценки. " +
            "Защита лабораторных строгая, к ней нужно готовиться заранее.",
        pros = listOf("Понятно объясняет сложные темы", "Честно оценивает"),
        cons = listOf("Строгая защита лабораторных"),
        tags = listOf(SummaryTag.MANY_LABS, SummaryTag.STRICT_DEFENSE, SummaryTag.CLEAR_REQUIREMENTS),
        scales = listOf(
            SummaryScale(SummaryScaleKind.EXPLAINS, SummaryScaleValue.HIGH, "Хвалят понятные лекции"),
            SummaryScale(SummaryScaleKind.ATTITUDE, SummaryScaleValue.MEDIUM, "Ровное отношение без поблажек"),
            SummaryScale(SummaryScaleKind.FAIRNESS, SummaryScaleValue.HIGH, "Оценки считают честными"),
            SummaryScale(SummaryScaleKind.STRICTNESS, SummaryScaleValue.HIGH, "Строго принимает лабораторные"),
            SummaryScale(SummaryScaleKind.WORKLOAD, SummaryScaleValue.NOT_ENOUGH_DATA, null),
        ),
        level = TeacherLevel.POSITIVE,
        confidence = SummaryConfidence.MEDIUM,
    )

    val teacherReviews = TeacherReviews(
        ISU,
        listOf(
            copiedReview("review-one", LONG_SUBJECT, ReviewDate.Month(YearMonth(2025, 1)), "Отзывы ПИ",
                "Понятно объясняет материал и подробно отвечает на вопросы."),
            copiedReview("review-two", null, ReviewDate.BeforeYear(2023), null, "На занятиях было интересно."),
            copiedReview("review-three", null, null, null, LONG_REVIEW),
        ),
        mine = null,
        canWrite = false,
        canVote = false,
        canReport = false,
        knownTeacher = false,
        summary = summary,
    )

    /** A student's Backend profile: the group and every share open, so a friend's rows lead on. */
    fun studentProfile(relationship: RelationshipState, sharing: UserSharing = openSharing) = UserProfile(
        UserSummary(ISU, BACKEND_NAME, null, listOf(UserGroup("M3234", 2, "ФИТиП")), sharing),
        relationship,
    )

    /** Nothing shared with someone who is not a friend yet. */
    val closedSharing = UserSharing(sport = false, schedule = false, friends = false)
    private val openSharing = UserSharing(sport = true, schedule = true, friends = true)

    /** The teacher from My ITMO with copied reviews and the AI summary; no Backend profile. */
    val teacherPage: UserProfileUiState = page(teacher, social = null, reviews = teacherReviews)

    /** A friend from both sources; no reviews. */
    val friendPage: UserProfileUiState = studentPage(RelationshipState.FRIENDS)

    /** The viewer's own page. */
    val selfPage: UserProfileUiState = page(student, SocialBlock(studentProfile(RelationshipState.FRIENDS), isSelf = true, busy = false))

    /** Only Backend knows the person, with no group: the hero alone over `ITMO.Widgets`. */
    val noFactsPage: UserProfileUiState = page(
        person = null,
        social = SocialBlock(
            UserProfile(UserSummary(ISU, BACKEND_NAME, null, emptyList(), closedSharing), RelationshipState.NONE),
            isSelf = false,
            busy = false,
        ),
    )

    /** Custom services are off: My ITMO's person without the friendship, `ITMO.Widgets` or reviews. */
    val disabledPage: UserProfileUiState = page(student, social = null)

    /** The same student under [relationship], with [sharing]; [busy] while a request is in flight. */
    fun studentPage(
        relationship: RelationshipState,
        sharing: UserSharing = openSharing,
        busy: Boolean = false,
    ): UserProfileUiState = page(student, SocialBlock(studentProfile(relationship, sharing), isSelf = false, busy = busy))

    fun page(person: Person?, social: SocialBlock?, reviews: TeacherReviews? = null): UserProfileUiState =
        userProfileUiState(
            ISU,
            person?.let { ProfilePart.Ready(it) } ?: ProfilePart.Absent,
            social?.let { ProfilePart.Ready(it) } ?: ProfilePart.Absent,
            reviews?.let { ProfilePart.Ready(it) } ?: ProfilePart.Absent,
        )

    private fun copiedReview(id: String, subject: String?, written: ReviewDate?, source: String?, text: String) =
        TeacherReview(id, subject, written, text, score = 0, myVote = 0,
            origin = ReviewOrigin.Reviews(source, "https://example.org/reviews/$id"))
}
