package dev.alllexey.itmowidgets.feature.social.presentation

import dev.alllexey.itmowidgets.core.reviews.OwnTeacherReview
import dev.alllexey.itmowidgets.core.reviews.TeacherReview
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.testing.communityReview
import dev.alllexey.itmowidgets.core.testing.copiedReview
import dev.alllexey.itmowidgets.core.testing.ownReview
import dev.alllexey.itmowidgets.core.testing.teacherReviews
import dev.alllexey.itmowidgets.core.testing.teacherSummary
import dev.alllexey.itmowidgets.feature.social.domain.model.Person
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `the summary comes with the section and never makes one on its own`() {
        val summary = teacherSummary()
        val withReviews = teacherReviews(5, listOf(copiedReview("r1")), canWrite = false, summary = summary)
        val withoutReviews = teacherReviews(5, canWrite = false, knownTeacher = false, summary = summary)

        assertEquals(summary, profileReviews(withReviews, null, busyId = null)?.summary)
        assertEquals(1, profileReviews(withReviews, null, busyId = null)?.count)
        assertNull(profileReviews(withoutReviews, student, busyId = null))
        assertNull(profileReviews(teacherReviews(5, listOf(copiedReview("r1"))), null, busyId = null)?.summary)
    }

    @Test
    fun `the summary scales are folded unless the screen expanded them`() {
        val reviews = teacherReviews(5, listOf(copiedReview("r1")), summary = teacherSummary())

        assertFalse(checkNotNull(profileReviews(reviews, null, busyId = null)).summaryExpanded)
        assertTrue(checkNotNull(profileReviews(reviews, null, busyId = null, summaryExpanded = true)).summaryExpanded)
    }

    private fun section(
        items: List<TeacherReview> = emptyList(),
        mine: OwnTeacherReview? = null,
        canWrite: Boolean = false,
    ) = ProfileReviews(items, mine, canWrite, canVote = true, canReport = true, busyId = null)

    private data class Case(val name: String, val reviews: TeacherReviews?, val person: Person?, val expected: ProfileReviews?)
}
