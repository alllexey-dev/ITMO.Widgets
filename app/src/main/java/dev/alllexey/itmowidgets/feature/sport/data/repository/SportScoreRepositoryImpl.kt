package dev.alllexey.itmowidgets.feature.sport.data.repository

import api.myitmo.MyItmo
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.data.demo.DemoSport
import dev.alllexey.itmowidgets.core.debug.SportScoreOverrideProvider
import dev.alllexey.itmowidgets.core.network.requireResult
import dev.alllexey.itmowidgets.core.network.appResultOf
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
    private val overrideProvider: SportScoreOverrideProvider,
    private val time: AcademicTimeProvider,
    private val demo: DemoMode
) : SportScoreRepository {
    override suspend fun getScorePeriods(): AppResult<List<SportScorePeriod>> = if (demo.isActive()) {
        AppResult.Success(DemoSport.periods(time))
    } else request {
        val periods = myItmo.execute(myItmo.api.sportSemesters).requireResult()
        // Without the current semester every period stays "current" with no end date,
        // so the recordbook never raises a sport alarm it cannot justify.
        val current = try {
            myItmo.execute(myItmo.api.currentSportSemester).requireResult()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
        periods.map {
            val isCurrent = current == null || it.id == current.id
            SportScorePeriod(it.id, it.value.trim(), current?.dateEnd?.takeIf { isCurrent }, isCurrent)
        }
    }

    override suspend fun getScoreSummary(semesterId: Long): AppResult<SportScoreSummary> {
        if (demo.isActive()) return AppResult.Success(DemoSport.summary(semesterId, time))
        return when (val result = getSportScore(semesterId)) {
            is AppResult.Success -> AppResult.Success(result.value.summary)
            is AppResult.Failure -> result
        }
    }

    /** The sport feature also needs attendance history, which is not shared with recordbook. */
    suspend fun getSportScore(semesterId: Long? = null): AppResult<SportScore> = if (demo.isActive()) {
        AppResult.Success(DemoSport.score(time))
    } else request {
        val score = myItmo.execute(myItmo.api.getSportScore(semesterId)).requireResult().toModel()
        // Production's provider always returns null. Debug overrides are current-period only.
        val override = overrideProvider.getOverride() ?: return@request score
        val isCurrent = semesterId == null ||
            myItmo.execute(myItmo.api.currentSportSemester).requireResult().id == semesterId
        if (isCurrent) score.copy(attendances = override.attendances, other = override.bonus) else score
    }

    private suspend fun <T> request(block: () -> T): AppResult<T> = appResultOf { withContext(Dispatchers.IO) { block() } }
}
