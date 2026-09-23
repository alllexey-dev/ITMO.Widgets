package dev.alllexey.itmowidgets.core.sport

import dev.alllexey.itmowidgets.core.result.AppResult
import java.time.OffsetDateTime

/**
 * Official sport periods use their own IDs, not recordbook semester numbers.
 * Only the current period knows its [endsAt]; the list API carries no dates.
 */
data class SportScorePeriod(
    val id: Long,
    val label: String,
    val endsAt: OffsetDateTime? = null,
    val current: Boolean = false
)

/** Same calculation as “Мой спорт”; this is progress, never an official academic grade. */
data class SportScoreSummary(val attendances: Int, val bonus: Int) {
    val creditedBonus: Int get() = bonus.coerceAtMost(40)
    val total: Int get() = attendances + creditedBonus
    val totalCapped: Int get() = total.coerceAtMost(100)
    val remaining: Int get() = (100 - totalCapped).coerceAtLeast(0)
}

interface SportScoreRepository {
    suspend fun getScorePeriods(): AppResult<List<SportScorePeriod>>
    suspend fun getScoreSummary(semesterId: Long): AppResult<SportScoreSummary>
}
