package dev.alllexey.itmowidgets.feature.update.domain

import dev.alllexey.itmowidgets.feature.update.FakeAppUpdateRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class PendingAppUpdateTest {

    private val now: Instant = Instant.parse("2026-09-15T10:00:00Z")

    @Test
    fun offersAnUpdateTheUserHasNotSeenYet() = runTest {
        val repository = FakeAppUpdateRepository(update = update())

        val pending = PendingAppUpdate(repository, clock())()

        assertEquals(update(), pending)
        assertEquals(1, repository.notifications)
    }

    @Test
    fun staysSilentWithoutAnUpdate() = runTest {
        val repository = FakeAppUpdateRepository(update = null)

        assertNull(PendingAppUpdate(repository, clock())())
        assertEquals(0, repository.notifications)
    }

    @Test
    fun waitsADayBetweenOffersOfTheSameRelease() = runTest {
        val repository = FakeAppUpdateRepository(
            update = update(),
            reminderState = reminder(notifiedAt = now - 23.hours)
        )

        assertNull(PendingAppUpdate(repository, clock())())

        repository.reminderState = reminder(notifiedAt = now - 25.hours)
        assertNotNull(PendingAppUpdate(repository, clock())())
    }

    @Test
    fun aSkippedReleaseStaysSkippedUntilANewerOneArrives() = runTest {
        val repository = FakeAppUpdateRepository(
            update = update(),
            reminderState = reminder(skipped = "2.2")
        )

        assertNull(PendingAppUpdate(repository, clock())())

        repository.update = update(latest = "2.3")
        assertNotNull(PendingAppUpdate(repository, clock())())
    }

    @Test
    fun anUnsupportedBuildIgnoresBothTheSkipAndTheInterval() = runTest {
        val repository = FakeAppUpdateRepository(
            update = update(unsupported = true),
            reminderState = reminder(skipped = "2.2", notifiedAt = now)
        )

        assertNotNull(PendingAppUpdate(repository, clock())())
        assertEquals(1, repository.notifications)
    }

    private fun clock(): Clock = object : Clock {
        override fun now(): Instant = now
    }

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
        notifiedAt: Instant = NEVER
    ) = AppUpdateReminder(AppVersionName(skipped), notifiedAt)

    private companion object {
        val NEVER: Instant = Instant.fromEpochMilliseconds(0)
    }
}
