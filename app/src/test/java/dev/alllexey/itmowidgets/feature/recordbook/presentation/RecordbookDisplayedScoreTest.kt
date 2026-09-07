package dev.alllexey.itmowidgets.feature.recordbook.presentation

import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookRate
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubjectStatus
import dev.alllexey.itmowidgets.feature.recordbook.recordbookSubject
import org.junit.Assert.*
import org.junit.Test

class RecordbookDisplayedScoreTest {
    @Test fun `sport points appear before the official credit`() {
        val subject = recordbookSubject(name = "Физическая культура и спорт (элективная)").copy(score = null, rate = null)
        val result = subject.displayedScore(sport(66, 28))
        assertEquals(94.0, result.value!!, 0.0)
        assertEquals(940, result.progress)
        assertEquals(RecordbookRate.InProgress, subject.normalizedRate)
        assertEquals(RecordbookSubjectStatus.IN_PROGRESS, subject.status)
    }

    @Test fun `more than one hundred never awards a credit`() {
        val subject = recordbookSubject(name = "Физическая культура и спорт (базовая)").copy(score = 5.0, rate = null)
        val result = subject.displayedScore(sport(100, 20))
        assertEquals(120.0, result.value!!, 0.0)
        assertEquals(1000, result.progress)
        assertEquals(RecordbookSubjectStatus.IN_PROGRESS, subject.status)
        assertEquals(RecordbookRate.Credit, subject.copy(rate = "зачет").normalizedRate)
        assertEquals(result, subject.copy(rate = "зачет").displayedScore(sport(100, 20)))
    }

    @Test fun `missing sport data cannot masquerade as zero or old academic points`() {
        val subject = recordbookSubject(name = "Физическая культура и спорт (базовая)").copy(rate = "зачет")
        listOf(null, RecordbookSportState.Error, RecordbookSportState.Unavailable).forEach { state ->
            assertNull(subject.displayedScore(state).value)
            assertEquals(RecordbookRate.Credit, subject.normalizedRate)
        }
        assertEquals(0.0, subject.displayedScore(sport(0, 0)).value!!, 0.0)
    }

    @Test fun `ordinary subjects keep official points even when sport is available`() {
        assertEquals(75.0, recordbookSubject().displayedScore(sport(100, 20)).value!!, 0.0)
    }

    @Test fun `sport sectors keep the shared bonus cap and both contributions above one hundred`() {
        val score = SportScoreSummary(100, 65)
        val result = RecordbookSportProgress(score)
        assertEquals(140, score.total)
        assertEquals(40, score.creditedBonus)
        assertEquals(100f / 140 * 100, result.attendancePercentage, 0.001f)
        assertEquals(40f / 140 * 100, result.bonusPercentage, 0.001f)
        assertEquals(100f, result.attendancePercentage + result.bonusPercentage, 0.001f)
    }

    @Test fun `zero and small sectors do not fabricate a full ring`() {
        val zero = RecordbookSportProgress(SportScoreSummary(0, 0))
        assertEquals(0f, zero.attendancePercentage + zero.bonusPercentage, 0f)
        val progress = RecordbookSportProgress(SportScoreSummary(66, 1))
        assertEquals(66f, progress.attendancePercentage, 0.001f)
        assertEquals(1f, progress.bonusPercentage, 0.001f)
    }

    private fun sport(attendance: Int, bonus: Int) = RecordbookSportState.Content("Весна 2025/2026", SportScoreSummary(attendance, bonus))
}
