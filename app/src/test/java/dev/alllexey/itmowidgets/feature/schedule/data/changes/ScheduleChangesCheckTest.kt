package dev.alllexey.itmowidgets.feature.schedule.data.changes

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.storage.ScheduleCheckPreferences
import dev.alllexey.itmowidgets.core.testing.FakeSessionTokenStore
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import dev.alllexey.itmowidgets.core.testing.MutableAcademicTime
import dev.alllexey.itmowidgets.core.testing.scheduleChange
import dev.alllexey.itmowidgets.core.work.CheckOutcome
import dev.alllexey.itmowidgets.feature.schedule.FakeScheduleChangesRepository
import dev.alllexey.itmowidgets.feature.schedule.RecordingScheduleChangeNotifier
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleCheckResult
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleChangesCheckTest {

    private val tokens = FakeSessionTokenStore()
    private val settings = ScheduleCheckPreferences(InMemoryPreferencesDataStore())
    private val repository = FakeScheduleChangesRepository()
    private val notifier = RecordingScheduleChangeNotifier()
    private val time = MutableAcademicTime(LocalDateTime.of(2026, 9, 7, 12, 0))
    private val check = ScheduleChangesCheck(tokens, settings, repository, notifier, time)

    @Test
    fun `no session or a switched off check asks nothing and shows nothing`() = runTest {
        repository.changes.value = listOf(scheduleChange(id = "1"))
        tokens.signedIn = false
        assertEquals(CheckOutcome.SKIPPED, check.run())

        tokens.signedIn = true
        settings.setScheduleChangesEnabled(false)
        assertEquals(CheckOutcome.SKIPPED, check.run())

        assertEquals(0, repository.checks)
        assertTrue(notifier.shown.isEmpty())
        assertTrue(repository.notified.isEmpty())
    }

    @Test
    fun `a successful check at noon shows one digest and marks both changes delivered`() = runTest {
        repository.checkResult = AppResult.Success(ScheduleCheckResult.Compared(2))
        repository.changes.value = listOf(scheduleChange(id = "1"), scheduleChange(id = "2", subject = "Физика"))

        assertEquals(CheckOutcome.DONE, check.run())

        assertEquals(1, repository.checks)
        assertEquals(2, notifier.shown.single().unread)
        assertEquals(setOf("1", "2"), repository.notified)
    }

    @Test
    fun `changes found in the quiet hours wait for the first run after six`() = runTest {
        time.current = LocalDateTime.of(2026, 9, 7, 3, 0)
        repository.changes.value = listOf(scheduleChange(id = "night"))

        assertEquals(CheckOutcome.DONE, check.run())
        assertTrue(notifier.shown.isEmpty())
        assertTrue(repository.notified.isEmpty())

        time.current = LocalDateTime.of(2026, 9, 7, 6, 5)
        assertEquals(CheckOutcome.DONE, check.run())
        assertEquals("night", notifier.shown.single().first.id)
        assertEquals(setOf("night"), repository.notified)
    }

    @Test
    fun `a network failure still delivers what waits and asks for a retry`() = runTest {
        time.current = LocalDateTime.of(2026, 9, 7, 6, 5)
        repository.changes.value = listOf(scheduleChange(id = "night"))
        repository.checkResult = AppResult.Failure(AppError.Network)

        assertEquals(CheckOutcome.RETRY, check.run())

        assertEquals("night", notifier.shown.single().first.id)
        assertEquals(setOf("night"), repository.notified)
    }

    @Test
    fun `an unauthorized answer is final and nothing to deliver shows nothing`() = runTest {
        repository.checkResult = AppResult.Failure(AppError.Unauthorized)
        repository.changes.value = listOf(scheduleChange(id = "read", read = true), scheduleChange(id = "sent", notified = true))

        assertEquals(CheckOutcome.DONE, check.run())

        assertEquals(1, repository.checks)
        assertTrue(notifier.shown.isEmpty())
        assertTrue(repository.notified.isEmpty())
    }
}
