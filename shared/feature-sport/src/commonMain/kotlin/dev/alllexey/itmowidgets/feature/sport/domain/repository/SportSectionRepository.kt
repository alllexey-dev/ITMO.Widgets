package dev.alllexey.itmowidgets.feature.sport.domain.repository

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.feature.sport.domain.model.MySportSection
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportSectionSlot
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * Sections on Backend (DECISIONS.md, Спорт, «Секция»): the user's own sections and the slots of lessons. Every call
 * passes the custom-services opt-in; the demo shows its own section and refuses changes.
 */
interface SportSectionRepository {
    /** `Disabled` while Backend may not be called; mutations below keep it current without a reload. */
    fun observeSections(): Flow<LoadState<List<MySportSection>>>

    suspend fun refreshSections()

    /** The slots of [lessonIds]; a lesson without a provable venue has none and is left out. */
    suspend fun slots(lessonIds: List<Long>): AppResult<List<SportSectionSlot>>

    /** Makes a section of the booked lesson [lessonId]. */
    suspend fun create(lessonId: Long): AppResult<MySportSection>

    /** Turns the date of [sectionId] at [start] off ([skipped]) or back on. */
    suspend fun setSkipped(sectionId: Long, start: Instant, skipped: Boolean): AppResult<MySportSection>

    suspend fun stop(sectionId: Long): AppResult<Unit>
}
