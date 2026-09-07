package dev.alllexey.itmowidgets.feature.sport.data.repository

import dev.alllexey.itmowidgets.core.debug.SportScoreOverride
import dev.alllexey.itmowidgets.core.debug.SportScoreOverrideProvider
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import dev.alllexey.itmowidgets.core.testing.myItmoStub
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SportScoreRepositoryImplTest {
    @Test fun `sends explicit sport semester query and accepts null attendance history`() = runTest {
        val repository = SportScoreRepositoryImpl(myItmoStub { request ->
            assertEquals("/api/sport/personal/score", request.url.encodedPath)
            assertEquals("10", request.url.queryParameter("semester_id"))
            SCORE
        }, overrides())
        assertEquals(AppResult.Success(SportScoreSummary(66, 48)), repository.getScoreSummary(10))
    }

    @Test fun `debug scores affect current my sport and matching recordbook period but not history`() = runTest {
        val repository = SportScoreRepositoryImpl(myItmoStub { request ->
            if (request.url.encodedPath.endsWith("/current")) """{"error_code":0,"result":{"id":41}}""" else SCORE
        }, overrides(SportScoreOverride(80, 20)))
        assertEquals(AppResult.Success(SportScoreSummary(80, 20)), repository.getScoreSummary(41))
        assertEquals(AppResult.Success(SportScoreSummary(66, 48)), repository.getScoreSummary(10))
        assertEquals(SportScoreSummary(80, 20), (repository.getSportScore() as AppResult.Success).value.summary)
    }

    @Test fun `HTTP 200 API error cannot become a successful zero score`() = runTest {
        val repository = SportScoreRepositoryImpl(myItmoStub { """{"error_code":401,"result":null}""" }, overrides())
        assertEquals(AppResult.Failure(AppError.Unauthorized), repository.getScoreSummary(10))
    }

    private fun overrides(value: SportScoreOverride? = null) = object : SportScoreOverrideProvider {
        override fun getOverride() = value
    }
    private companion object {
        const val SCORE = """{"error_code":0,"result":{"sum":{"attendances":66,"other":48},"attendances":null}}"""
    }
}
