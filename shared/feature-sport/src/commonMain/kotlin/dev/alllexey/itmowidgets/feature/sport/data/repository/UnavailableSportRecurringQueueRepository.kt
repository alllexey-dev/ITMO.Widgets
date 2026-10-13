package dev.alllexey.itmowidgets.feature.sport.data.repository

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportRecurringQueueRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportRecurringRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** No Backend route keeps the rules yet: nothing is stored and the feature is off. */
object UnavailableSportRecurringQueueRepository : SportRecurringQueueRepository {
    override val available = false

    override fun observeRules(): Flow<List<SportRecurringRule>> = flowOf(emptyList())

    override suspend fun enable(rule: SportRecurringRule): AppResult<Unit> = AppResult.Failure(AppError.NotFound)

    override suspend fun disable(rule: SportRecurringRule): AppResult<Unit> = AppResult.Failure(AppError.NotFound)
}
