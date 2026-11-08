package dev.alllexey.itmowidgets.feature.finances.domain

import dev.alllexey.itmowidgets.core.result.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * The student's Договоры об обучении from my.itmo. [observe] is the last successful answer kept on the device (null
 * before the first one), so the screen and Профиль read it offline; [refresh] asks my.itmo again and keeps the answer.
 */
interface TuitionRepository {
    fun observe(): Flow<TuitionAnswer?>

    suspend fun refresh(): AppResult<TuitionAnswer>
}
