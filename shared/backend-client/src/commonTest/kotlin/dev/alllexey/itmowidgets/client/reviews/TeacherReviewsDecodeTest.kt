package dev.alllexey.itmowidgets.client.reviews

import dev.alllexey.itmowidgets.client.common.ResourceVoteRequest
import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewContractFixtures.TEACHER
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewContractFixtures.answering
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewContractFixtures.jsonOf
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewContractFixtures.responseJson
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewContractFixtures.with
import dev.alllexey.itmowidgets.client.support.runSuspend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** What every [TeacherReviewsApi] call returns for a synthetic Backend answer. */
class TeacherReviewsDecodeTest {

    private val fixtures = TeacherReviewContractFixtures

    @Test
    fun teacherReviews() = runSuspend {
        assertEquals(fixtures.response, answering(responseJson()).client.reviews.teacherReviews(TEACHER))
    }

    @Test
    fun saveMyTeacherReview() = runSuspend {
        val pending = fixtures.response.copy(mine = fixtures.mine.copy(status = TeacherReviewStatus.PENDING))

        val result = answering(responseJson(pending)).client.reviews.saveMyTeacherReview(TEACHER, fixtures.save)

        assertEquals(pending, result)
    }

    @Test
    fun deleteMyTeacherReview() = runSuspend {
        val deleted = fixtures.response.copy(mine = null)

        assertEquals(deleted, answering(responseJson(deleted)).client.reviews.deleteMyTeacherReview(TEACHER))
    }

    @Test
    fun voteTeacherReview() = runSuspend {
        val result = answering(responseJson()).client.reviews
            .voteTeacherReview(fixtures.namedId, ResourceVoteRequest(1))

        assertEquals(fixtures.response, result)
    }

    @Test
    fun reportTeacherReview() = runSuspend {
        val result = answering(responseJson()).client.reviews
            .reportTeacherReview(fixtures.anonymousId, TeacherReviewsRouteCases.report)

        assertEquals(fixtures.response, result)
    }

    @Test
    fun teacherSummaryLevels() = runSuspend {
        val body = """[{"teacherIsu":123456,"level":"POSITIVE"},{"teacherIsu":234567,"level":"VERY_NEGATIVE"}]"""

        val result = answering(body).client.reviews.teacherSummaryLevels(TeacherReviewsRouteCases.isus)

        assertEquals(
            listOf(
                TeacherSummaryLevel(123456, SummaryLevel.POSITIVE),
                TeacherSummaryLevel(234567, SummaryLevel.VERY_NEGATIVE),
            ),
            result,
        )
    }

    @Test
    fun anEmptySummaryLevelListIsEmpty() = runSuspend {
        assertEquals(emptyList(), answering("[]").client.reviews.teacherSummaryLevels(listOf(123456)))
    }

    @Test
    fun aResponseWithoutCapabilitiesFailsAsAContract() = runSuspend {
        for (field in listOf("canWrite", "canVote", "canReport")) {
            val body = jsonOf(TeacherReviewsResponse.serializer(), fixtures.response).with(field, null).toString()

            assertFailsWith<BackendException.Contract>(field) { answering(body).client.reviews.teacherReviews(TEACHER) }
        }
    }
}
