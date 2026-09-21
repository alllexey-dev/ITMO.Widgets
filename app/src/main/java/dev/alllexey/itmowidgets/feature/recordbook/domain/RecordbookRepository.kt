package dev.alllexey.itmowidgets.feature.recordbook.domain

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject

interface RecordbookRepository {
    suspend fun getPrograms(): AppResult<List<RecordbookProgram>>

    suspend fun getSubjects(
        programId: Long,
        semester: Int
    ): AppResult<List<RecordbookSubject>>

    suspend fun getControls(entryId: Long): AppResult<List<RecordbookControl>>

    /** The last successful answers, kept in memory for the first frame of a screen; null before any. */
    fun cachedPrograms(): List<RecordbookProgram>? = null
    fun cachedSubjects(programId: Long, semester: Int): List<RecordbookSubject>? = null
    fun cachedControls(entryId: Long): List<RecordbookControl>? = null
}
