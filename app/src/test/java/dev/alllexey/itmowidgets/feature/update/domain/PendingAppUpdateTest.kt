package dev.alllexey.itmowidgets.feature.update.domain

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PendingAppUpdateTest {

    private val now: Instant = Instant.parse("2026-09-15T10:00:00Z")

    @Test
    fun `offers an update the user has not seen yet`() = runTest {
        val repository = FakeAppUpdateRepository(update = update())

        val pending = PendingAppUpdate(repository, clock())()

        assertEquals(update(), pending)
        assertEquals(1, repository.notifications)
    }

    @Test
    fun `stays silent without an update`() = runTest {
        val repository = FakeAppUpdateRepository(update = null)

        assertNull(PendingAppUpdate(repository, clock())())
        assertEquals(0, repository.notifications)
    }

    @Test
    fun `waits a day between offers of the same release`() = runTest {
        val repository = FakeAppUpdateRepository(
            update = update(),
            reminderState = reminder(notifiedAt = now.minus(Duration.ofHours(23)))
        )

        assertNull(PendingAppUpdate(repository, clock())())

        repository.reminderState = reminder(notifiedAt = now.minus(Duration.ofHours(25)))
        assertNotNull(PendingAppUpdate(repository, clock())())
    }

    @Test
    fun `a skipped release stays skipped until a newer one arrives`() = runTest {
        val repository = FakeAppUpdateRepository(
            update = update(),
            reminderState = reminder(skipped = "2.2")
        )

        assertNull(PendingAppUpdate(repository, clock())())

        repository.update = update(latest = "2.3")
        assertNotNull(PendingAppUpdate(repository, clock())())
    }

    @Test
    fun `an unsupported build ignores both the skip and the interval`() = runTest {
        val repository = FakeAppUpdateRepository(
            update = update(unsupported = true),
            reminderState = reminder(skipped = "2.2", notifiedAt = now)
        )

        assertNotNull(PendingAppUpdate(repository, clock())())
        assertEquals(1, repository.notifications)
    }

    private fun clock(): Clock = Clock.fixed(now, ZoneOffset.UTC)

    private fun update(
        latest: String = "2.2",
        unsupported: Boolean = false
    ) = AppUpdate(
        installed = AppVersionName("2.1"),
        latest = AppVersionName(latest),
        note = "",
        unsupported = unsupported
    )

    private fun reminder(
        skipped: String = "2.1",
        notifiedAt: Instant = Instant.EPOCH
    ) = AppUpdateReminder(AppVersionName(skipped), notifiedAt)

    private class FakeAppUpdateRepository(
        var update: AppUpdate?,
        var reminderState: AppUpdateReminder = AppUpdateReminder(AppVersionName("2.1"), Instant.EPOCH)
    ) : AppUpdateRepository {
        var notifications = 0
            private set
        var skippedVersion: AppVersionName? = null
            private set

        override suspend fun loadUpdate(): AppUpdate? = update

        override suspend fun reminder(): AppUpdateReminder = reminderState

        override suspend fun markNotified() {
            notifications += 1
        }

        override suspend fun skip(version: AppVersionName) {
            skippedVersion = version
        }
    }
}
