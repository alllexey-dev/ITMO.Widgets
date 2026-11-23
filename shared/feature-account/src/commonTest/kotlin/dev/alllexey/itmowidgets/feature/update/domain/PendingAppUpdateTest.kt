package dev.alllexey.itmowidgets.feature.update.domain

import dev.alllexey.itmowidgets.feature.update.FakeAppUpdateAvailability
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

        val pending = pending(repository)

        assertEquals(update(), pending)
        assertEquals(1, repository.notifications)
    }

    @Test
    fun staysSilentWithoutAnUpdate() = runTest {
        val repository = FakeAppUpdateRepository(update = null)

        assertNull(pending(repository))
        assertEquals(0, repository.notifications)
    }

    @Test
    fun waitsADayBetweenOffersOfTheSameRelease() = runTest {
        val repository = FakeAppUpdateRepository(
            update = update(),
            reminderState = reminder(notifiedAt = now - 23.hours)
        )

        assertNull(pending(repository))

        repository.reminderState = reminder(notifiedAt = now - 25.hours)
        assertNotNull(pending(repository))
    }

    @Test
    fun aSkippedReleaseStaysSkippedUntilANewerOneArrives() = runTest {
        val repository = FakeAppUpdateRepository(
            update = update(),
            reminderState = reminder(skipped = "2.2")
        )

        assertNull(pending(repository))

        repository.update = update(latest = "2.3")
        assertNotNull(pending(repository))
    }

    @Test
    fun anUnsupportedBuildIgnoresBothTheSkipAndTheInterval() = runTest {
        val repository = FakeAppUpdateRepository(
            update = update(unsupported = true),
            reminderState = reminder(skipped = "2.2", notifiedAt = now)
        )

        assertNotNull(pending(repository))
        assertEquals(1, repository.notifications)
    }

    @Test
    fun anUpdateTheStoreDoesNotHaveYetIsNotOfferedAndDoesNotStartTheInterval() = runTest {
        val repository = FakeAppUpdateRepository(update = update())
        val store = FakeAppUpdateAvailability(available = false)

        assertNull(pending(repository, store))
        assertEquals(0, repository.notifications)

        // The store got the release: the next check offers it, not a day later.
        store.available = true
        assertEquals(update(), pending(repository, store))
        assertEquals(1, repository.notifications)
        assertEquals(listOf(update(), update()), store.asked)
    }

    @Test
    fun anUnsupportedBuildAlsoWaitsForTheStore() = runTest {
        val repository = FakeAppUpdateRepository(update = update(unsupported = true))
        val store = FakeAppUpdateAvailability(available = false)

        // A forced screen with nothing to update to would be a dead end.
        assertNull(pending(repository, store))
        assertEquals(0, repository.notifications)

        store.available = true
        assertEquals(update(unsupported = true), pending(repository, store))
        assertEquals(listOf(update(unsupported = true), update(unsupported = true)), store.asked)
    }

    @Test
    fun theStoreIsNotAskedAboutAnOfferTheReminderHoldsBack() = runTest {
        val store = FakeAppUpdateAvailability()

        assertNull(pending(FakeAppUpdateRepository(update = null), store))
        assertNull(pending(FakeAppUpdateRepository(update(), reminder(skipped = "2.2")), store))
        assertNull(pending(FakeAppUpdateRepository(update(), reminder(notifiedAt = now - 23.hours)), store))

        assertEquals(emptyList(), store.asked)
    }

    private suspend fun pending(
        repository: FakeAppUpdateRepository,
        store: FakeAppUpdateAvailability = FakeAppUpdateAvailability()
    ) = PendingAppUpdate(repository, store, clock())()

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
