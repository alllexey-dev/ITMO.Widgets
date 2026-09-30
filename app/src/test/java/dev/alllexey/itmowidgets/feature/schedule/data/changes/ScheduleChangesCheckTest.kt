package dev.alllexey.itmowidgets.feature.schedule.data.changes

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.session.SessionTokens
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import dev.alllexey.itmowidgets.core.testing.scheduleChange
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.FakeScheduleChangesRepository
import dev.alllexey.itmowidgets.feature.schedule.RecordingScheduleChangeNotifier
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleCheckResult
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleChangesCheckTest {

    private val tokens = Tokens()
    private val settings = AppSettingsStorage(InMemoryPreferencesDataStore())
    private val repository = FakeScheduleChangesRepository()
    private val notifier = RecordingScheduleChangeNotifier()
    private val time = MutableTime(LocalDateTime.of(2026, 9, 7, 12, 0))
    private val check = ScheduleChangesCheck(tokens, settings, repository, notifier, time)

    @Test
    fun `no session or a switched off check asks nothing and shows nothing`() = runTest {
        repository.changes.value = listOf(scheduleChange(id = "1"))
        tokens.refresh = false
        assertEquals(CheckOutcome.SKIPPED, check.run())

        tokens.refresh = true
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

    private class Tokens : SessionTokenStore {
        var refresh = true
        override fun hasRefreshToken() = refresh
        override fun getIdToken(): String? = null
        override fun replaceWithRefreshToken(refreshToken: String) = Unit
        override fun replaceWithTokens(tokens: SessionTokens) = Unit
        override fun clearTokens() = Unit
    }

    private class MutableTime(var current: LocalDateTime) : AcademicTimeProvider {
        override val zoneId: ZoneId = ZoneId.of("Europe/Moscow")
        override fun today(): LocalDate = current.toLocalDate()
        override fun now(): OffsetDateTime = current.atZone(zoneId).toOffsetDateTime()
    }
}
