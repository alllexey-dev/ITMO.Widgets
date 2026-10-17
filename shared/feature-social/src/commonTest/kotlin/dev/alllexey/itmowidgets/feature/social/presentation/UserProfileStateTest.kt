package dev.alllexey.itmowidgets.feature.social.presentation

import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.reviews.ReviewOrigin
import dev.alllexey.itmowidgets.core.reviews.TeacherReview
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.schedule.TeacherLessons
import dev.alllexey.itmowidgets.core.schedule.TeacherNowLesson
import dev.alllexey.itmowidgets.core.schedule.lessonTypeName
import kotlinx.datetime.LocalTime
import dev.alllexey.itmowidgets.core.testing.ownReview
import dev.alllexey.itmowidgets.core.testing.profile
import dev.alllexey.itmowidgets.core.testing.teacherReviews
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.social.domain.model.Person
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonEducation
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonPosition
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonRoom
import dev.alllexey.itmowidgets.shared.feature.social.SocialStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserProfileStateTest {

    private val person = samplePerson(5).copy(photoUrl = "https://example.test/person.jpg")
    private val student = person.copy(education = listOf(PersonEducation("M3206", 2, "ФИТиП")))
    private val teacher = person.copy(
        positions = listOf(PersonPosition("доцент", "Факультет программной инженерии и компьютерной техники (ПИиКТ)")),
        rooms = listOf(PersonRoom("1410", "Кронверкский пр., 49")),
    )
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
        origin = ReviewOrigin.Reviews(sourceTitle = null, sourceUrl = "https://example.test/review-1"),
    )), canWrite = false)

    @Test
    fun anyLoadingPartHoldsThePageAndErrorsKeepTheirPrecedence() {
        val ready = ProfilePart.Ready(person)
        val readySocial = ProfilePart.Ready(social)
        assertEquals(UserProfileUiState.Loading, state(ProfilePart.Loading, readySocial))
        assertEquals(UserProfileUiState.Loading, state(ready, ProfilePart.Loading))
        assertEquals(UserProfileUiState.Loading, state(ready, readySocial, reviews = ProfilePart.Loading))
        assertEquals(UserProfileUiState.Loading, state(ready, readySocial, teaches = ProfilePart.Loading))
        val cases = listOf(
            Triple(ProfilePart.Failed(AppError.NotFound), ProfilePart.Absent, AppError.NotFound),
            Triple(ProfilePart.Failed(AppError.Network), ProfilePart.Absent, AppError.Network),
            Triple(ProfilePart.Failed(AppError.NotFound), ProfilePart.Failed(AppError.Network), AppError.Network),
            Triple(ProfilePart.Failed(AppError.NotFound), ProfilePart.Failed(AppError.Forbidden), AppError.Forbidden),
            Triple(ProfilePart.Failed(AppError.Network), ProfilePart.Failed(AppError.Forbidden), AppError.Network),
        )
        for ((personPart, socialPart, error) in cases) {
            assertEquals(UserProfileUiState.Error(error), state(personPart, socialPart))
        }
    }

    @Test
    fun theDirectoryIdentityWinsAndKeepsItsOwnPhoto() {
        val card = content(state(ProfilePart.Ready(person.copy(photoUrl = null)), ProfilePart.Ready(social)))
        assertEquals("Персона 5", card.name)
        assertNull(card.pictureUrl)

        val backendOnly = content(state(ProfilePart.Failed(AppError.Network), ProfilePart.Ready(social)))
        assertEquals("Пользователь 5", backendOnly.name)
        assertEquals("https://example.test/backend.jpg", backendOnly.pictureUrl)
        assertEquals(
            listOf(ProfileLine(UiText.Res(SocialStrings.person_group_course, listOf("M3100", 1)), "ФИТиП")),
            backendOnly.study?.groups,
        )
    }

    @Test
    fun aStudentInTheAppOffersTheFriendshipsNextStep() {
        fun actions(relationship: RelationshipState) =
            content(state(ProfilePart.Ready(student), ProfilePart.Ready(social.withRelationship(relationship)))).actions
        assertEquals(listOf(CardAction.ADD_FRIEND), actions(RelationshipState.NONE))
        assertEquals(listOf(CardAction.CANCEL_REQUEST), actions(RelationshipState.OUTGOING))
        assertEquals(listOf(CardAction.ACCEPT_REQUEST, CardAction.REJECT_REQUEST), actions(RelationshipState.INCOMING))
        assertEquals(emptyList(), actions(RelationshipState.FRIENDS))

        val friend = content(state(ProfilePart.Ready(student), ProfilePart.Ready(social.withRelationship(RelationshipState.FRIENDS))))
        assertTrue(friend.canRemoveFriend)
        assertEquals(
            listOf(ProfileRoleKind.STUDY, ProfileRoleKind.FRIEND),
            friend.roles.map { it.kind },
        )
        assertEquals(UiText.Res(SocialStrings.person_role_student, listOf("M3206")), friend.roles.first().text)
    }

    @Test
    fun aStudentWithoutTheAppIsInvitedButNotWhenTheViewerIsNotConnected() {
        val missing = state(ProfilePart.Ready(student), ProfilePart.Absent, absence = SocialAbsence.NOT_REGISTERED)
        assertEquals(listOf(CardAction.INVITE), content(missing).actions)

        val unknown = state(ProfilePart.Ready(student), ProfilePart.Absent, absence = SocialAbsence.NOT_CONNECTED)
        assertEquals(emptyList(), content(unknown).actions)
    }

    @Test
    fun aPureTeacherHasNoFriendshipAndTheirTeachingBlockShowsWhereToFindThem() {
        val card = content(
            state(ProfilePart.Ready(teacher), ProfilePart.Absent, ProfilePart.Ready(reviews), absence = SocialAbsence.NOT_REGISTERED),
        )

        assertEquals(emptyList(), card.actions)
        assertNull(card.study)
        assertFalse(card.teachingFirst)
        assertEquals(listOf(ProfileRoleKind.POSITION), card.roles.map { it.kind })
        assertEquals(UiText.Dynamic("Доцент, ПИиКТ"), card.roles.single().text)
        val teaching = assertIs<TeachingBlock>(card.teaching)
        assertEquals(listOf(ProfileLine(UiText.Res(SocialStrings.person_room, listOf("1410")), "Кронверкский пр., 49")), teaching.rooms)
        assertEquals(1, card.reviews?.count)
    }

    @Test
    fun whoTeachesTheViewerComesTeachingFirstWithTheReviewButton() {
        val both = teacher.copy(education = listOf(PersonEducation("M4150", 1, "ПИиКТ")))
        val writable = teacherReviews(5, canWrite = true).copy(knownTeacher = true)
        val taught = ProfilePart.Ready(TeacherLessons(setOf(1L), listOf("Алгоритмы")))

        val card = content(state(ProfilePart.Ready(both), ProfilePart.Ready(social), ProfilePart.Ready(writable), taught))

        assertTrue(card.teachingFirst)
        assertEquals(listOf("Алгоритмы"), card.teaching?.subjects)
        assertEquals(listOf(CardAction.WRITE_REVIEW, CardAction.ADD_FRIEND), card.actions)

        val notTaught = content(state(ProfilePart.Ready(both), ProfilePart.Ready(social), ProfilePart.Ready(writable)))
        assertFalse(notTaught.teachingFirst)
        assertEquals(listOf(CardAction.ADD_FRIEND, CardAction.WRITE_REVIEW), notTaught.actions)
    }

    @Test
    fun theStudyRowsFollowBackendsCapabilitiesAndAreAllOpenToSelf() {
        val shared = social.copy(profile = backendProfile.copy(user = backendProfile.user.copy(
            sharing = backendProfile.user.sharing.copy(schedule = true, sport = false, friends = true),
        )))
        val study = content(state(ProfilePart.Ready(student), ProfilePart.Ready(shared))).study
        assertEquals(Triple(true, false, true), study?.let { Triple(it.schedule, it.sport, it.friends) })

        val closed = social.copy(profile = backendProfile.copy(user = backendProfile.user.copy(
            sharing = backendProfile.user.sharing.copy(schedule = false, sport = false, friends = false),
        )))
        val self = content(state(ProfilePart.Ready(student), ProfilePart.Ready(closed.copy(isSelf = true))))
        assertEquals(Triple(true, true, true), self.study?.let { Triple(it.schedule, it.sport, it.friends) })
        assertEquals(emptyList(), self.actions)
        assertTrue(ProfileRoleKind.OWN in self.roles.map { it.kind })
    }

    @Test
    fun theReviewsCarryTheOwnReviewAndTheBusyReview() {
        val section = teacherReviews(5, reviews.reviews, mine = ownReview(), canVote = false)

        val card = content(state(ProfilePart.Ready(teacher), ProfilePart.Absent, ProfilePart.Ready(section), busyId = "own"))

        assertEquals(
            ProfileReviews(reviews.reviews, ownReview(), canWrite = false, canVote = false, canReport = true, busyId = "own"),
            card.reviews,
        )
    }

    @Test
    fun theLessonNowSaysUntilWhenAndWhereAndTheNextOneWhen() {
        val lesson = TeacherNowLesson(
            LocalTime(11, 40), LocalTime(13, 10), "Алгоритмы", 3, "2337", "Кронверкский пр., 49", false,
            listOf("M3207", "M3208"), isNow = true,
        )

        val now = assertIs<TeachingBlock>(content(state(ProfilePart.Ready(teacher), ProfilePart.Absent, now = lesson)).teaching).now

        assertEquals(UiText.Res(SocialStrings.person_now_teaching, listOf("13:10")), now?.heading)
        assertEquals(
            UiText.Joined(listOf(UiText.Res(SocialStrings.person_room, listOf("2337")), UiText.Dynamic("Кронверкский пр., 49")), ", "),
            now?.place,
        )
        assertEquals(
            UiText.Joined(
                listOf(
                    UiText.Dynamic("Алгоритмы"),
                    UiText.Res(SocialStrings.person_lesson_for, listOf(UiText.LowercaseFirst(lessonTypeName(3)), "M3207, M3208")),
                ),
                ", ",
            ),
            now?.lesson,
        )

        val next = teacherNowLine(lesson.copy(start = LocalTime(9, 5), isNow = false, online = true, room = null, groups = emptyList()))
        assertEquals(UiText.Res(SocialStrings.person_next_lesson, listOf("09:05")), next.heading)
        assertEquals(UiText.Res(SocialStrings.person_lesson_online), next.place)
        assertFalse(next.isNow)
    }

    private fun SocialBlock.withRelationship(relationship: RelationshipState) =
        copy(profile = profile.copy(relationship = relationship))

    private fun content(state: UserProfileUiState) = assertIs<UserProfileUiState.Content>(state)

    private fun state(
        person: ProfilePart<Person>,
        social: ProfilePart<SocialBlock>,
        reviews: ProfilePart<TeacherReviews> = ProfilePart.Absent,
        teaches: ProfilePart<TeacherLessons> = ProfilePart.Absent,
        absence: SocialAbsence? = null,
        busyId: String? = null,
        now: TeacherNowLesson? = null,
    ) = userProfileUiState(5, person, social, reviews, teaches, absence, now, busyId)
}
