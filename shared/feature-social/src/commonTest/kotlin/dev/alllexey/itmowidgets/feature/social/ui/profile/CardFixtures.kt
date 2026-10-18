package dev.alllexey.itmowidgets.feature.social.ui.profile

import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.ReviewOrigin
import dev.alllexey.itmowidgets.core.reviews.TeacherReview
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.schedule.TeacherLessons
import dev.alllexey.itmowidgets.core.schedule.TeacherLessonsGateway
import dev.alllexey.itmowidgets.feature.social.domain.model.Person
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonEducation
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonPosition
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonRoom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Synthetic people for the card's host tests: a teacher with reviews and a student in ITMO.Widgets. */
internal object CardFixtures {
    const val ISU = 100001
    const val LONG_NAME = "Александра Константиновна Константинопольская"

    val teacher = Person(
        ISU, LONG_NAME, null,
        listOf(PersonPosition("доцент", "Факультет информационных технологий и программирования")),
        listOf(PersonRoom("405", "Кронверкский пр., 49")),
        emptyList(),
    )

    val student = teacher.copy(positions = emptyList(), rooms = emptyList(), education = listOf(PersonEducation("M3234", 2, "ФИТиП")))

    val teacherReviews = TeacherReviews(
        ISU,
        listOf(
            TeacherReview("review-one", "Математика", null, "Понятно объясняет материал и подробно отвечает на вопросы.", 0, 0,
                ReviewOrigin.Reviews(null, "https://example.org/reviews/review-one")),
        ),
        mine = null,
        canWrite = false,
        canVote = false,
        canReport = false,
        knownTeacher = false,
    )

    fun studentProfile(relationship: RelationshipState, sharing: UserSharing = UserSharing(sport = true, schedule = true, friends = true)) =
        UserProfile(UserSummary(ISU, "Соколов Артём", null, listOf(UserGroup("M3234", 2, "ФИТиП")), sharing), relationship)

    /** The viewer's lessons with nobody. */
    val noLessons = object : TeacherLessonsGateway {
        override fun taughtBy(teacherIsu: Int): Flow<AppResult<TeacherLessons>> =
            flowOf(AppResult.Success(TeacherLessons(emptySet(), emptyList())))
    }
}
