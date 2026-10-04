package dev.alllexey.itmowidgets.feature.schedule.data.home

import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.scheduleChange
import dev.alllexey.itmowidgets.core.testing.slot
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.FakeScheduleChangesRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleChangesHomeCardSourceTest {
    private val repository = FakeScheduleChangesRepository()
    private val source = ScheduleChangesHomeCardSource(repository, Noon)

    @Test
    fun `no unread changes give no card`() = runTest {
        assertTrue(source.observe().first().isEmpty())

        repository.changes.value = listOf(scheduleChange(id = "1", read = true))
        assertTrue(source.observe().first().isEmpty())
    }

    @Test
    fun `the card counts unread changes still ahead and shows the newest of them`() = runTest {
        val older = scheduleChange(id = "older", detectedAt = Instant.parse("2026-09-07T06:00:00Z"))
        val newer = scheduleChange(
            id = "newer", kind = ScheduleChangeKind.CANCELLED, detectedAt = Instant.parse("2026-09-07T08:00:00Z")
        )
        val over = scheduleChange(
            id = "over", kind = ScheduleChangeKind.ADDED, after = slot(2, TODAY),
            detectedAt = Instant.parse("2026-09-07T08:30:00Z")
        )
        repository.changes.value = listOf(older, over, newer)

        val card = source.observe().first().single() as HomeCard.ScheduleChanges

        assertEquals(2, card.unread)
        assertEquals(newer, card.latest)
    }

    @Test
    fun `read changes are not counted`() = runTest {
        val unread = scheduleChange(id = "unread", detectedAt = Instant.parse("2026-09-07T06:00:00Z"))
        val read = scheduleChange(id = "read", read = true, detectedAt = Instant.parse("2026-09-07T08:00:00Z"))
        repository.changes.value = listOf(unread, read)

        val card = source.observe().first().single() as HomeCard.ScheduleChanges

        assertEquals(1, card.unread)
        assertEquals(unread, card.latest)
    }

    @Test
    fun `only the close button of this card marks everything read`() = runTest {
        repository.changes.value = listOf(scheduleChange())

        source.dismiss(HomeCardKind.SPORT)
        assertEquals(0, repository.markAllReadCalls)

        source.dismiss(HomeCardKind.SCHEDULE_CHANGES)
        assertEquals(1, repository.markAllReadCalls)
        assertTrue(source.observe().first().isEmpty())
    }

    @Test
    fun `refresh succeeds without a check`() = runTest {
        assertEquals(AppResult.Success(Unit), source.refresh())
        assertEquals(0, repository.checks)
    }

    private object Noon : AcademicTimeProvider by FixedAcademicTime(TODAY.atTime(12, 0))

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 9, 7)
    }
}
