package dev.alllexey.itmowidgets.feature.dormitory.domain

import dev.alllexey.itmowidgets.core.result.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * The student's dormitory application from my.itmo. [observe] is the last successful answer kept on the device (null
 * before the first one), so the screen and Профиль read it offline; [refresh] asks my.itmo again and keeps the answer.
 */
interface DormitoryRepository {
    fun observe(): Flow<DormitoryAnswer?>

    suspend fun refresh(): AppResult<DormitoryAnswer>
}
