package dev.alllexey.itmowidgets.feature.schedule.data.local

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.Path
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ScheduleLocalDataSourceTest {

    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun replacementPublishesOnlyTheCompleteRangeSnapshot() = withCache { fixture ->
        val old = (0L..79L).map { day(DATE.plusDays(it), "old") }
        val refreshed = old.map { it.copy(note = "refreshed") }
        val local = fixture.local()
        local.replaceRange(null, DATE, old.last().date, old)
        val snapshots = Channel<List<DaySchedule>>(Channel.UNLIMITED)
        val collector = launch {
            local.observeRange(null, DATE, old.last().date).collect { snapshots.send(it) }
        }
        try {
            assertEquals(old, withTimeout(5_000) { snapshots.receive() })
            local.replaceRange(null, DATE, old.last().date, refreshed)
            assertEquals(refreshed, withTimeout(5_000) { snapshots.receive() })
            assertTrue("No empty or partial intermediate snapshots", snapshots.tryReceive().isFailure)
        } finally {
            collector.cancelAndJoin()
        }
    }

    @Test
    fun rangeReplacementRemovesOmittedDaysAndPreservesOtherDatesAndUsersOnDisk() = withCache { fixture ->
        val local = fixture.local()
        val own = (0L..4L).map { day(DATE.plusDays(it), "own") }
        val peer = day(DATE.plusDays(2), "friend")
        local.replaceRange(null, DATE, DATE.plusDays(4), own)
        local.save(peer, 900001)

        val replacement = day(DATE.plusDays(2), "new")
        local.replaceRange(null, DATE.plusDays(1), DATE.plusDays(3), listOf(
            replacement,
            day(DATE, "out-of-range must not overwrite")
        ))

        val reopened = fixture.local()
        assertEquals(listOf(own.first(), replacement, own.last()), reopened.observeRange(null, DATE, DATE.plusDays(4)).first())
        assertEquals(listOf(peer), reopened.observeRange(900001, DATE, DATE.plusDays(4)).first())
        assertNull(reopened.get(null, DATE.plusDays(1)))
        assertNull(reopened.get(null, DATE.plusDays(3)))

        local.replaceRange(null, DATE.plusDays(1), DATE.plusDays(3), emptyList())
        assertEquals(listOf(own.first(), own.last()), fixture.local().observeRange(null, DATE, DATE.plusDays(4)).first())
        assertNotNull(fixture.local().get(900001, peer.date))
    }

    @Test
    fun existingObserverSurvivesSaveAndClearAndDiskFormatRemainsGzippedCacheEntry() = withCache { fixture ->
        val local = fixture.local()
        val snapshots = Channel<List<DaySchedule>>(Channel.UNLIMITED)
        val collector = launch { local.observeRange(null, DATE, DATE).collect { snapshots.send(it) } }
        try {
            assertEquals(emptyList<DaySchedule>(), withTimeout(5_000) { snapshots.receive() })
            val schedule = day(DATE, "saved")
            local.save(schedule, null)
            assertEquals(listOf(schedule), withTimeout(5_000) { snapshots.receive() })
            val diskEntry = GZIPInputStream(File(local.cacheDir, "default_$DATE.json").inputStream()).use {
                ScheduleStoreJson.decodeFromString<StoredCacheEntry>(String(it.readBytes()))
            }
            assertNull(diskEntry.userIsu)
            assertEquals(DATE.toString(), diskEntry.date)
            assertEquals(fixture.clock.millis(), diskEntry.timestamp)
            assertEquals(schedule, ScheduleStoreJson.decodeFromString<StoredDaySchedule>(diskEntry.data).toModel())
            assertEquals(diskEntry.toEntry(), local.get(null, DATE))

            local.clear()
            assertEquals(emptyList<DaySchedule>(), withTimeout(5_000) { snapshots.receive() })
            assertNull(local.get(null, DATE))
            local.save(schedule, null)
            assertEquals(listOf(schedule), withTimeout(5_000) { snapshots.receive() })
        } finally {
            collector.cancelAndJoin()
        }
    }

    @Test
    fun unchangedTwentyFourHourTtlAppliesToDiskAndObservedSnapshots() = withCache { fixture ->
        val local = fixture.local()
        val schedule = day(DATE)
        local.save(schedule, null)
        fixture.clock.now = fixture.clock.now.plusSeconds(24 * 60 * 60L)
        assertNotNull(local.get(null, DATE))
        assertEquals(listOf(schedule), local.observeRange(null, DATE, DATE).first())

        fixture.clock.now = fixture.clock.now.plusMillis(1)
        assertNull(local.get(null, DATE))
        assertEquals(emptyList<DaySchedule>(), local.observeRange(null, DATE, DATE).first())
        assertEquals(emptyList<DaySchedule>(), fixture.local().observeRange(null, DATE, DATE).first())
    }

    @Test
    fun concurrentWritesToDifferentScopesDoNotLoseEitherSnapshot() = withCache { fixture ->
        val local = fixture.local()
        val own = (0L..14L).map { day(DATE.plusDays(it), "own") }
        val peer = own.map { it.copy(note = "friend") }
        val first = async { local.replaceRange(null, DATE, own.last().date, own) }
        val second = async { local.replaceRange(900001, DATE, peer.last().date, peer) }
        first.await()
        second.await()

        assertEquals(own, local.observeRange(null, DATE, own.last().date).first())
        assertEquals(peer, local.observeRange(900001, DATE, peer.last().date).first())
        assertEquals(own, fixture.local().observeRange(null, DATE, own.last().date).first())
        assertEquals(peer, fixture.local().observeRange(900001, DATE, peer.last().date).first())
    }

    @Test
    fun revokedUserIsRemovedFromEveryDateAndDiskWithoutAffectingOthers() = withCache { fixture ->
        val local = fixture.local()
        val days = listOf(day(DATE, "private"), day(DATE.plusDays(30), "later"))
        days.forEach { local.save(it, 900001) }
        val own = day(DATE, "own")
        val other = day(DATE, "other user with same ISU prefix")
        local.save(own, null)
        local.save(other, 9000012)
        val snapshots = Channel<List<DaySchedule>>(Channel.UNLIMITED)
        val collector = launch { local.observeRange(900001, DATE, DATE.plusDays(30)).collect { snapshots.send(it) } }
        try {
            assertEquals(days, withTimeout(5_000) { snapshots.receive() })
            local.clearUser(900001)
            assertEquals(emptyList<DaySchedule>(), withTimeout(5_000) { snapshots.receive() })
            assertEquals(emptyList<DaySchedule>(), fixture.local().observeRange(900001, DATE, DATE.plusDays(30)).first())
            assertNull(fixture.local().get(900001, DATE.plusDays(30)))
            assertEquals(listOf(own), fixture.local().observeRange(null, DATE, DATE).first())
            assertEquals(listOf(other), fixture.local().observeRange(9000012, DATE, DATE).first())
        } finally {
            collector.cancelAndJoin()
        }
    }

    private fun withCache(block: suspend CoroutineScope.(Fixture) -> Unit) = runBlocking {
        block(Fixture(temporary.root.toOkioPath()))
    }

    private class Fixture(root: Path) {
        val clock = MutableClock()
        val directories = object : AppDirectories {
            override val files = root / "files"
            override val cache = root / "cache"
            override val noBackup = root / "no_backup"
        }
        fun local() = ScheduleLocalDataSourceImpl(clock, directories, RealDispatchers)
    }

    private class MutableClock : Clock() {
        var now: Instant = Instant.parse("2026-09-08T08:00:00Z")
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = fixed(now, zone)
    }

    private fun day(date: LocalDate, note: String? = null) = DaySchedule(date.dayOfWeek.value, 1, date, note, emptyList())

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 9, 7)

        /** Real threads, as on a device: the cache's mutex and flows are what is under test. */
        val RealDispatchers = AppDispatchers(io = Dispatchers.IO, default = Dispatchers.Default, main = Dispatchers.Unconfined)
    }
}
