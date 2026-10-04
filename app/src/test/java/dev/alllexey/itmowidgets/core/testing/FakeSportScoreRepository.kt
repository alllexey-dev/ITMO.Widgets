package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.sport.SportScorePeriod
import dev.alllexey.itmowidgets.core.sport.SportScoreRepository
import dev.alllexey.itmowidgets.core.sport.SportScoreSummary

class FakeSportScoreRepository : SportScoreRepository {
    var periods: AppResult<List<SportScorePeriod>> = AppResult.Success(listOf(SportScorePeriod(9, "Осень 2025/2026"), SportScorePeriod(10, "Весна 2025/2026")))
    var score: AppResult<SportScoreSummary> = AppResult.Success(SportScoreSummary(64, 26))
    var periodRequests = 0
    val scoreRequests = mutableListOf<Long>()
    override suspend fun getScorePeriods(): AppResult<List<SportScorePeriod>> { periodRequests++; return periods }
    override suspend fun getScoreSummary(semesterId: Long): AppResult<SportScoreSummary> { scoreRequests += semesterId; return score }
}
