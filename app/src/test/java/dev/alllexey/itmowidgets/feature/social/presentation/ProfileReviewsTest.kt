package dev.alllexey.itmowidgets.feature.social.presentation

import dev.alllexey.itmowidgets.core.reviews.OwnTeacherReview
import dev.alllexey.itmowidgets.core.reviews.TeacherReview
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.feature.social.domain.model.Person
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileReviewsTest {
    private val student = samplePerson(5)
    private val lecturer = samplePerson(5).copy(positions = listOf(PersonPosition("Доцент", "Кафедра")))

    @Test
    fun `the section and writing follow Backend and the person`() {
        val cases = listOf(
            Case("no reviews, a known teacher", teacherReviews(5, knownTeacher = true), student, section(canWrite = true)),
            Case("no reviews, a person with a position", teacherReviews(5, knownTeacher = false), lecturer, section(canWrite = true)),
            Case("no reviews, a student", teacherReviews(5, knownTeacher = false), student, null),
            Case("no reviews, no person", teacherReviews(5, knownTeacher = false), null, null),
            Case("writing not allowed", teacherReviews(5, canWrite = false), lecturer, null),
            Case("an own review", teacherReviews(5, mine = ownReview()), student, section(mine = ownReview())),
            Case("one review without writing", teacherReviews(5, listOf(copiedReview("r1")), canWrite = false, knownTeacher = false),
                null, section(items = listOf(copiedReview("r1")))),
            Case("no reviews at all", null, lecturer, null),
        )

        cases.forEach { case -> assertEquals(case.name, case.expected, profileReviews(case.reviews, case.person, busyId = null)) }
    }

    @Test
    fun `the count includes the own review`() {
        val reviews = teacherReviews(5, listOf(copiedReview("r1"), communityReview("r2")), mine = ownReview())

        assertEquals(3, profileReviews(reviews, null, busyId = "r2")?.count)
        assertEquals("r2", profileReviews(reviews, null, busyId = "r2")?.busyId)
    }

    private fun section(
        items: List<TeacherReview> = emptyList(),
        mine: OwnTeacherReview? = null,
        canWrite: Boolean = false,
    ) = ProfileReviews(items, mine, canWrite, canVote = true, canReport = true, busyId = null)

    private data class Case(val name: String, val reviews: TeacherReviews?, val person: Person?, val expected: ProfileReviews?)
}
