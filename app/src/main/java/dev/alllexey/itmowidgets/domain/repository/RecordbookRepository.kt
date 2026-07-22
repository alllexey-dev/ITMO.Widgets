package dev.alllexey.itmowidgets.domain.repository

import dev.alllexey.itmowidgets.domain.model.recordbook.RecordbookControl
import dev.alllexey.itmowidgets.domain.model.recordbook.RecordbookProgram
import dev.alllexey.itmowidgets.domain.model.recordbook.RecordbookSubject

interface RecordbookRepository {
    suspend fun getPrograms(): List<RecordbookProgram>
    suspend fun getSubjects(programId: Long, semester: Int): List<RecordbookSubject>
    suspend fun getControls(entryId: Long): List<RecordbookControl>
}
