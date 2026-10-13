package dev.alllexey.itmowidgets.feature.sport.domain.repository

import dev.alllexey.itmowidgets.core.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/**
 * `Очередь через неделю` of a pinned section: the student's lesson repeats every two weeks, so the rule names the
 * section, the teacher, the weekday, the start and the week of [prototypeLessonId], and Backend queues every lesson
 * My ITMO publishes that matches it, spending one queue of the limit per date (DECISIONS.md "Data the design needs").
 */
data class SportRecurringRule(
    val prototypeLessonId: Long,
    val sectionId: Long,
    val teacherIsu: Int,
    val weekday: DayOfWeek,
    val start: LocalTime,
)

/**
 * The rules of `Очередь через неделю`. Backend does not store them yet, so the app binds
 an implementation that is never [available], so the switch stays hidden; screens and tests run against this contract.
 */
interface SportRecurringQueueRepository {
    /** False until Backend keeps the rules: the screens hide the switch and its dates. */
    val available: Boolean

    fun observeRules(): Flow<List<SportRecurringRule>>

    suspend fun enable(rule: SportRecurringRule): AppResult<Unit>

    suspend fun disable(rule: SportRecurringRule): AppResult<Unit>
}
