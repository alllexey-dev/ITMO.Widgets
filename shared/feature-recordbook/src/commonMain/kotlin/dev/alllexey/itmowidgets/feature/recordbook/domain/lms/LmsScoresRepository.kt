package dev.alllexey.itmowidgets.feature.recordbook.domain.lms

import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface LmsScoresRepository {
    fun observeChanges(): Flow<Unit> = flowOf(Unit)
    suspend fun load(args: RecordbookSubjectArgs): List<LmsScore>
    suspend fun save(args: RecordbookSubjectArgs, scores: List<LmsScore>)
}
