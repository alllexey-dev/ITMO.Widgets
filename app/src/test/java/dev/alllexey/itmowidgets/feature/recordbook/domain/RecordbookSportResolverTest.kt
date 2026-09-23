package dev.alllexey.itmowidgets.feature.recordbook.domain

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.sport.SportScorePeriod
import dev.alllexey.itmowidgets.feature.recordbook.FakeSportScoreRepository
import dev.alllexey.itmowidgets.feature.recordbook.recordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import java.time.OffsetDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RecordbookSportResolverTest {
    private val sport = FakeSportScoreRepository()
    private val resolver = RecordbookSportResolver(sport)
    private val pe = recordbookSubject(name = "Физическая культура и спорт (элективная)")
    private fun period(semester: Int = 2, year: String = "2025/2026") = RecordbookPeriod(year, semester, 1, false)

    @Test fun `uses historical sport ID rather than academic semester number`() = runTest {
        val state = resolver.resolve(period(), listOf(pe)) as RecordbookSportState.Content
        assertEquals(listOf(10L), sport.scoreRequests)
        assertEquals("Весна 2025/2026", state.periodLabel)
    }

    @Test fun `end date and current flag come from the matched period`() = runTest {
        val endsAt = OffsetDateTime.parse("2026-06-28T00:00:00+03:00")
        sport.periods = AppResult.Success(listOf(SportScorePeriod(9, "Осень 2025/2026"), SportScorePeriod(10, "Весна 2025/2026", endsAt, current = true)))
        val current = resolver.resolve(period(), listOf(pe)) as RecordbookSportState.Content
        assertEquals(endsAt, current.endsAt)
        assertTrue(current.current)
        val past = resolver.resolve(period(1), listOf(pe)) as RecordbookSportState.Content
        assertNull(past.endsAt)
        assertFalse(past.current)
    }

    @Test fun `global odd semester maps to autumn within the selected year`() = runTest {
        resolver.resolve(period(5), listOf(pe))
        assertEquals(listOf(9L), sport.scoreRequests)
    }

    @Test fun `unmatched future period never falls back to current score`() = runTest {
        assertEquals(RecordbookSportState.Unavailable, resolver.resolve(period(year = "2028/2029"), listOf(pe)))
        assertTrue(sport.scoreRequests.isEmpty())
    }

    @Test fun `ambiguous or unrecognized labels do not guess`() = runTest {
        sport.periods = AppResult.Success(listOf(SportScorePeriod(1, "Весна 2025/2026"), SportScorePeriod(2, "Весна 2025/2026")))
        assertEquals(RecordbookSportState.Unavailable, resolver.resolve(period(), listOf(pe)))
        sport.periods = AppResult.Success(listOf(SportScorePeriod(1, "2025/2026, второй семестр")))
        assertEquals(RecordbookSportState.Unavailable, resolver.resolve(period(), listOf(pe)))
        assertTrue(sport.scoreRequests.isEmpty())
    }

    @Test fun `PE name recognition does not include physics or other similar titles`() = runTest {
        assertNull(resolver.resolve(period(), listOf(recordbookSubject(name = "Физическая химия"))))
        assertEquals(0, sport.periodRequests)
    }

    @Test fun `base and elective rows share one period score request`() = runTest {
        resolver.resolve(period(), listOf(pe, pe.copy(name = "Физическая культура и спорт (базовая)")))
        assertEquals(listOf(10L), sport.scoreRequests)
    }

    @Test fun `API failure is distinct from period not available`() = runTest {
        sport.score = AppResult.Failure(AppError.Network)
        assertEquals(RecordbookSportState.Error, resolver.resolve(period(), listOf(pe)))
    }
}
