package dev.alllexey.itmowidgets.core.storage

import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class BackendUploadPreferencesTest {

    private val clock = object : Clock {
        var now = Instant.parse("2026-10-20T09:00:00Z")
        override fun now(): Instant = now
    }
    private val dataStore = InMemoryPreferencesDataStore()
    private val uploads = BackendUploadPreferences(dataStore, clock)
    private val sent = mutableListOf<Any>()

    @Test
    fun anUnchangedSnapshotIsSentOnce() = runTest {
        upload(BackendUpload.LESSONS, listOf(1L, 2L))
        upload(BackendUpload.LESSONS, listOf(1L, 2L))
        BackendUploadPreferences(dataStore, clock).sendIfChanged(BackendUpload.LESSONS, listOf(1L, 2L)) { sent += "again" }

        assertEquals(listOf<Any>(listOf(1L, 2L)), sent)
    }

    @Test
    fun aChangedSnapshotIsSentAtOnce() = runTest {
        upload(BackendUpload.LESSONS, listOf(1L))
        upload(BackendUpload.LESSONS, listOf(1L, 2L))

        assertEquals(listOf<Any>(listOf(1L), listOf(1L, 2L)), sent)
    }

    @Test
    fun eachUploadKeepsItsOwnSnapshot() = runTest {
        upload(BackendUpload.LESSONS, listOf(1L))
        upload(BackendUpload.SPORT_BOOKINGS, listOf(1L))

        assertEquals(2, sent.size)
    }

    @Test
    fun anUnchangedSnapshotGoesAgainADayLater() = runTest {
        upload(BackendUpload.SPORT_BOOKINGS, listOf(1L))
        clock.now += 23.hours
        upload(BackendUpload.SPORT_BOOKINGS, listOf(1L))
        clock.now += 1.hours
        upload(BackendUpload.SPORT_BOOKINGS, listOf(1L))

        assertEquals(2, sent.size)
    }

    @Test
    fun aClockMovedBackSendsAgain() = runTest {
        upload(BackendUpload.LESSONS, listOf(1L))
        clock.now -= 1.hours
        upload(BackendUpload.LESSONS, listOf(1L))

        assertEquals(2, sent.size)
    }

    @Test
    fun aFailedUploadIsNotRemembered() = runTest {
        assertFailsWith<IllegalStateException> {
            uploads.sendIfChanged(BackendUpload.LESSONS, listOf(1L)) { error("Backend is down") }
        }
        upload(BackendUpload.LESSONS, listOf(1L))

        assertEquals(1, sent.size)
    }

    @Test
    fun aSessionChangeForgetsEverySnapshot() = runTest {
        upload(BackendUpload.LESSONS, listOf(1L))
        upload(BackendUpload.SPORT_BOOKINGS, listOf(1L))

        uploads.clearSessionData()
        upload(BackendUpload.LESSONS, listOf(1L))
        upload(BackendUpload.SPORT_BOOKINGS, listOf(1L))

        assertEquals(4, sent.size)
    }

    private suspend fun upload(upload: BackendUpload, payload: Any) {
        uploads.sendIfChanged(upload, payload) { sent += payload }
    }
}
