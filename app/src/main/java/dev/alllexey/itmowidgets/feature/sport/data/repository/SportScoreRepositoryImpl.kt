package dev.alllexey.itmowidgets.feature.sport.data.repository

import api.myitmo.MyItmo
import dev.alllexey.itmowidgets.core.debug.SportScoreOverrideProvider
import dev.alllexey.itmowidgets.core.network.requireResult
import dev.alllexey.itmowidgets.core.network.toAppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.sport.SportScorePeriod
import dev.alllexey.itmowidgets.core.sport.SportScoreRepository
import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import dev.alllexey.itmowidgets.feature.sport.data.mapper.toModel
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SportScoreRepositoryImpl @Inject constructor(
    private val myItmo: MyItmo,
    private val overrideProvider: SportScoreOverrideProvider
) : SportScoreRepository {
    override suspend fun getScorePeriods(): AppResult<List<SportScorePeriod>> = request {
        myItmo.execute(myItmo.api.sportSemesters).requireResult()
            .map { SportScorePeriod(it.id, it.value.trim()) }
    }

    override suspend fun getScoreSummary(semesterId: Long): AppResult<SportScoreSummary> {
        return when (val result = getSportScore(semesterId)) {
            is AppResult.Success -> AppResult.Success(result.value.summary)
            is AppResult.Failure -> result
        }
    }

    /** The sport feature also needs attendance history, which is not shared with recordbook. */
    suspend fun getSportScore(semesterId: Long? = null): AppResult<SportScore> = request {
        val score = myItmo.execute(myItmo.api.getSportScore(semesterId)).requireResult().toModel()
        // Production's provider always returns null. Debug overrides are current-period only.
        val override = overrideProvider.getOverride() ?: return@request score
        val isCurrent = semesterId == null ||
            myItmo.execute(myItmo.api.currentSportSemester).requireResult().id == semesterId
        if (isCurrent) score.copy(attendances = override.attendances, other = override.bonus) else score
    }

    private suspend fun <T> request(block: () -> T): AppResult<T> = try {
        AppResult.Success(withContext(Dispatchers.IO) { block() })
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        AppResult.Failure(error.toAppError())
    }
}
