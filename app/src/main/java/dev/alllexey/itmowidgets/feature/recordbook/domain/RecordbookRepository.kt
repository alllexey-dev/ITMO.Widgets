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
}
