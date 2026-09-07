package dev.alllexey.itmowidgets.feature.recordbook.domain

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.sport.SportScoreRepository
import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import javax.inject.Inject

sealed interface RecordbookSportState {
    data class Content(val periodLabel: String, val score: SportScoreSummary) : RecordbookSportState
    data object Unavailable : RecordbookSportState
    data object Error : RecordbookSportState
}

class RecordbookSportResolver @Inject constructor(private val repository: SportScoreRepository) {
    suspend fun resolve(period: RecordbookPeriod, subjects: List<RecordbookSubject>): RecordbookSportState? {
        if (subjects.none { it.isPhysicalEducation }) return null
        val periods = when (val result = repository.getScorePeriods()) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return RecordbookSportState.Error
        }
        // The list API exposes a label, not a common academic ID. Accept only a unique,
        // exact year/season match; never guess by list order or use current scores as fallback.
        val season = if (period.semesterInCourse == 1) "Осень" else "Весна"
        val expected = "$season ${period.studyYear.trim()}"
        val match = periods.singleOrNull { it.label.trim().equals(expected, ignoreCase = true) }
            ?: return RecordbookSportState.Unavailable
        return when (val result = repository.getScoreSummary(match.id)) {
            is AppResult.Success -> RecordbookSportState.Content(match.label, result.value)
            is AppResult.Failure -> RecordbookSportState.Error
        }
    }
}
