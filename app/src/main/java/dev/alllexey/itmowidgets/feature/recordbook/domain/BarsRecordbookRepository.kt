package dev.alllexey.itmowidgets.feature.recordbook.domain

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.BarsJournalReference
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject

data class BarsSubjectDetails(val subject: RecordbookSubject, val controls: List<RecordbookControl>)

/** Own BARS journals for a MyITMO period; entries carry BARS plan identities, never MyITMO IDs. */
interface BarsRecordbookRepository {
    suspend fun getSubjects(period: RecordbookPeriod): AppResult<List<RecordbookSubject>>
    suspend fun getSubject(journal: BarsJournalReference): AppResult<BarsSubjectDetails>

    /** Controls of a journal the last answer already carried; null before any. */
    fun cachedControls(journal: BarsJournalReference): List<RecordbookControl>? = null
}
