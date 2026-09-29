package dev.alllexey.itmowidgets.feature.social.presentation

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.reviews.ReviewOrigin
import dev.alllexey.itmowidgets.core.reviews.TeacherReview
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.feature.social.domain.model.Person
import org.junit.Assert.assertEquals
import org.junit.Test

class UserProfileStateTest {

    private val person = samplePerson(5).copy(photoUrl = "https://example.test/person.jpg")
    private val backendProfile = profile(5).let {
        it.copy(user = it.user.copy(pictureUrl = "https://example.test/backend.jpg"))
    }
    private val social = SocialBlock(backendProfile, isSelf = false, busy = false)
    private val reviews = teacherReviews(5, listOf(TeacherReview(
        id = "review-1",
        subject = "Математика",
        written = null,
        text = "Понятные объяснения.",
        score = 0,
        myVote = 0,
        origin = ReviewOrigin.Reviews(sourceTitle = null, sourceUrl = "https://example.test/review-1")
    )))
    private val personContent = UserProfileUiState.Content(
        isu = 5,
        name = "Персона 5",
        pictureUrl = "https://example.test/person.jpg",
        facts = emptyList(),
        social = null,
        reviews = emptyList()
    )

    @Test
    fun `profile parts resolve loading identity fallback and error precedence`() {
        val readyPerson = ProfilePart.Ready(person)
        val readySocial = ProfilePart.Ready(social)
        val readyReviews = ProfilePart.Ready(reviews)
        val cases = listOf(
            StateCase(
                "directory person without backend sections",
                readyPerson, ProfilePart.Absent, ProfilePart.Absent,
                personContent
            ),
            StateCase(
                "loading person holds ready backend and reviews",
                ProfilePart.Loading, readySocial, readyReviews,
                UserProfileUiState.Loading
            ),
            StateCase(
                "loading backend holds a ready person",
                readyPerson, ProfilePart.Loading, ProfilePart.Absent,
                UserProfileUiState.Loading
            ),
            StateCase(
                "loading reviews hold both ready identities",
                readyPerson, readySocial, ProfilePart.Loading,
                UserProfileUiState.Loading
            ),
            StateCase(
                "backend identity and group replace an unavailable person",
                ProfilePart.Failed(AppError.Network), readySocial, ProfilePart.Absent,
                UserProfileUiState.Content(
                    isu = 5,
                    name = "Пользователь 5",
                    pictureUrl = "https://example.test/backend.jpg",
                    facts = listOf(ProfileFact(ProfileFactKind.EDUCATION, "M3100", "ФИТиП", 1)),
                    social = social,
                    reviews = emptyList()
                )
            ),
            StateCase(
                "directory identity takes priority without importing backend education",
                readyPerson, readySocial, readyReviews,
                personContent.copy(social = social, reviews = reviews.reviews)
            ),
            StateCase(
                "forbidden backend does not hide the directory person",
                readyPerson, ProfilePart.Failed(AppError.Forbidden), ProfilePart.Absent,
                personContent
            ),
            StateCase(
                "missing identities report not found",
                ProfilePart.Failed(AppError.NotFound), ProfilePart.Absent, ProfilePart.Absent,
                UserProfileUiState.Error(AppError.NotFound)
            ),
            StateCase(
                "person network failure is reported without a backend identity",
                ProfilePart.Failed(AppError.Network), ProfilePart.Absent, ProfilePart.Absent,
                UserProfileUiState.Error(AppError.Network)
            ),
            StateCase(
                "backend network failure takes priority over person not found",
                ProfilePart.Failed(AppError.NotFound), ProfilePart.Failed(AppError.Network), ProfilePart.Absent,
                UserProfileUiState.Error(AppError.Network)
            ),
            StateCase(
                "backend forbidden takes priority over person not found",
                ProfilePart.Failed(AppError.NotFound), ProfilePart.Failed(AppError.Forbidden), ProfilePart.Absent,
                UserProfileUiState.Error(AppError.Forbidden)
            ),
            StateCase(
                "person network failure takes priority over backend forbidden",
                ProfilePart.Failed(AppError.Network), ProfilePart.Failed(AppError.Forbidden), ProfilePart.Absent,
                UserProfileUiState.Error(AppError.Network)
            )
        )

        for (case in cases) {
            val state = userProfileUiState(5, case.person, case.social, case.reviews)

            assertEquals(case.description, case.expected, state)
        }
    }

    @Test
    fun `a ready directory person without a photo does not inherit the backend photo`() {
        val state = userProfileUiState(
            5,
            ProfilePart.Ready(person.copy(photoUrl = null)),
            ProfilePart.Ready(social),
            ProfilePart.Absent
        )

        assertEquals(personContent.copy(pictureUrl = null, social = social), state)
    }

    private data class StateCase(
        val description: String,
        val person: ProfilePart<Person>,
        val social: ProfilePart<SocialBlock>,
        val reviews: ProfilePart<TeacherReviews>,
        val expected: UserProfileUiState
    )
}
