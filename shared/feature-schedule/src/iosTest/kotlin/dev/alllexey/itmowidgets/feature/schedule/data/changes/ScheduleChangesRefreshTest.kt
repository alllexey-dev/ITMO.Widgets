package dev.alllexey.itmowidgets.feature.schedule.data.changes

import dev.alllexey.itmowidgets.core.storage.ScheduleCheckPreferences
import dev.alllexey.itmowidgets.core.testing.FakeSessionTokenStore
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import dev.alllexey.itmowidgets.core.testing.scheduleChange
import dev.alllexey.itmowidgets.core.testing.slot
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.work.CheckOutcome
import dev.alllexey.itmowidgets.feature.schedule.FakeScheduleChangesRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangeDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/** The schedule change step of the iOS runner: LT-1's check, and a night change handed to the system for 06:00. */
class ScheduleChangesRefreshTest {

    private val tokens = FakeSessionTokenStore()
    private val settings = ScheduleCheckPreferences(InMemoryPreferencesDataStore())
    private val repository = FakeScheduleChangesRepository(
        scheduleChange(id = "tomorrow", before = slot(1, LocalDate(2026, 10, 8)), after = slot(1, LocalDate(2026, 10, 9)))
    )
    private val notifier = RecordingMorningNotifier()

    @Test
    fun aNightChangeIsScheduledForSixMoscowTimeAndCountsAsDelivered() = runTest {
        settings.setScheduleChangesEnabled(true)

        val outcome = refresh(at = LocalDateTime(2026, 10, 7, 3, 0)).run()

        assertEquals(CheckOutcome.DONE, outcome)
        assertTrue(notifier.shown.isEmpty())
        val (digest, at) = notifier.scheduled.single()
        assertEquals("tomorrow", digest.first.id)
        assertEquals(Instant.parse("2026-10-07T03:00:00Z"), at, "06:00 in Moscow")
        assertEquals(setOf("tomorrow"), repository.notified)
    }

    @Test
    fun aDayChangeIsShownAtOnceByTheCheckItself() = runTest {
        settings.setScheduleChangesEnabled(true)

        assertEquals(CheckOutcome.DONE, refresh(at = LocalDateTime(2026, 10, 7, 12, 0)).run())

        assertEquals("tomorrow", notifier.shown.single().first.id)
        assertTrue(notifier.scheduled.isEmpty())
    }

    @Test
    fun aSwitchedOffCheckOrNoSessionSchedulesNothingAtNight() = runTest {
        settings.setScheduleChangesEnabled(false)
        assertEquals(CheckOutcome.SKIPPED, refresh(at = LocalDateTime(2026, 10, 7, 3, 0)).run())

        settings.setScheduleChangesEnabled(true)
        tokens.signedIn = false
        assertEquals(CheckOutcome.SKIPPED, refresh(at = LocalDateTime(2026, 10, 7, 3, 0)).run())

        assertTrue(notifier.scheduled.isEmpty())
        assertEquals(0, repository.checks)
    }

    @Test
    fun withoutTheScheduleDataGraphTheStepIsSkipped() = runTest {
        val refresh = ScheduleChangesRefresh({ null }, { null }, notifier, FixedAcademicTime())

        assertEquals(CheckOutcome.SKIPPED, refresh.run())
        assertEquals(0, repository.checks)
    }

    private fun refresh(at: LocalDateTime): ScheduleChangesRefresh {
        val time: AcademicTimeProvider = FixedAcademicTime(at)
        val check = ScheduleChangesCheck(tokens, settings, repository, notifier, time)
        return ScheduleChangesRefresh({ check }, { repository }, notifier, time)
    }

    private class RecordingMorningNotifier : MorningScheduleChangeNotifier {
        val shown = mutableListOf<ScheduleChangeDigest>()
        val scheduled = mutableListOf<Pair<ScheduleChangeDigest, Instant>>()

        override fun show(digest: ScheduleChangeDigest) {
            shown += digest
        }

        override suspend fun showAt(digest: ScheduleChangeDigest, at: Instant) {
            scheduled += digest to at
        }
    }
}
