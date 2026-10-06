package dev.alllexey.itmowidgets.feature.schedule.data.local

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.testkit.FakeClock
import dev.alllexey.itmowidgets.testkit.fakeFileSystemOf
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import okio.Path.Companion.toPath
import okio.buffer
import okio.fakefilesystem.FakeFileSystem
import okio.gzip
import okio.use

/** `cache/schedule_cache` on okio's fake file system; the 2.2 goldens are `ScheduleCache22GoldenTest` in `:app`. */
class ScheduleLocalDataSourceTest {

    private val clock = FakeClock(Instant.parse("2026-09-08T08:00:00Z"))
    private val fileSystem: FakeFileSystem = fakeFileSystemOf(clock = clock)

    @AfterTest
    fun tearDown() {
        fileSystem.checkNoOpenFiles()
    }

    @Test
    fun replacementPublishesOnlyTheCompleteRangeSnapshot() = withCache { fixture ->
        val old = (0L..79L).map { day(DATE.plus(it, DateTimeUnit.DAY), "old") }
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
            runCurrent()
            assertTrue(snapshots.tryReceive().isFailure, "No empty or partial intermediate snapshots")
        } finally {
            collector.cancelAndJoin()
        }
    }

    @Test
    fun rangeReplacementRemovesOmittedDaysAndPreservesOtherDatesAndUsersOnDisk() = withCache { fixture ->
        val local = fixture.local()
        val own = (0L..4L).map { day(DATE.plus(it, DateTimeUnit.DAY), "own") }
        val peer = day(DATE.plus(2, DateTimeUnit.DAY), "friend")
        local.replaceRange(null, DATE, DATE.plus(4, DateTimeUnit.DAY), own)
        local.save(peer, 900001)

        val replacement = day(DATE.plus(2, DateTimeUnit.DAY), "new")
        local.replaceRange(null, DATE.plus(1, DateTimeUnit.DAY), DATE.plus(3, DateTimeUnit.DAY), listOf(
            replacement,
            day(DATE, "out-of-range must not overwrite")
        ))

        val reopened = fixture.local()
        assertEquals(listOf(own.first(), replacement, own.last()), reopened.observeRange(null, DATE, DATE.plus(4, DateTimeUnit.DAY)).first())
        assertEquals(listOf(peer), reopened.observeRange(900001, DATE, DATE.plus(4, DateTimeUnit.DAY)).first())
        assertNull(reopened.get(null, DATE.plus(1, DateTimeUnit.DAY)))
        assertNull(reopened.get(null, DATE.plus(3, DateTimeUnit.DAY)))

        local.replaceRange(null, DATE.plus(1, DateTimeUnit.DAY), DATE.plus(3, DateTimeUnit.DAY), emptyList())
        assertEquals(listOf(own.first(), own.last()), fixture.local().observeRange(null, DATE, DATE.plus(4, DateTimeUnit.DAY)).first())
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
            val json = fileSystem.source(local.cacheDir / "default_$DATE.json").gzip().buffer().use { it.readUtf8() }
            val diskEntry = ScheduleStoreJson.decodeFromString<StoredCacheEntry>(json)
            assertNull(diskEntry.userIsu)
            assertEquals(DATE.toString(), diskEntry.date)
            assertEquals(clock.now().toEpochMilliseconds(), diskEntry.timestamp)
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
        clock.advanceBy(24.hours)
        assertNotNull(local.get(null, DATE))
        assertEquals(listOf(schedule), local.observeRange(null, DATE, DATE).first())

        clock.advanceBy(1.milliseconds)
        assertNull(local.get(null, DATE))
        assertEquals(emptyList<DaySchedule>(), local.observeRange(null, DATE, DATE).first())
        assertEquals(emptyList<DaySchedule>(), fixture.local().observeRange(null, DATE, DATE).first())
    }

    @Test
    fun concurrentWritesToDifferentScopesDoNotLoseEitherSnapshot() = withCache { fixture ->
        val local = fixture.local()
        val own = (0L..14L).map { day(DATE.plus(it, DateTimeUnit.DAY), "own") }
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
        val days = listOf(day(DATE, "private"), day(DATE.plus(30, DateTimeUnit.DAY), "later"))
        days.forEach { local.save(it, 900001) }
        val own = day(DATE, "own")
        val other = day(DATE, "other user with same ISU prefix")
        local.save(own, null)
        local.save(other, 9000012)
        val snapshots = Channel<List<DaySchedule>>(Channel.UNLIMITED)
        val collector = launch { local.observeRange(900001, DATE, DATE.plus(30, DateTimeUnit.DAY)).collect { snapshots.send(it) } }
        try {
            assertEquals(days, withTimeout(5_000) { snapshots.receive() })
            local.clearUser(900001)
            assertEquals(emptyList<DaySchedule>(), withTimeout(5_000) { snapshots.receive() })
            assertEquals(emptyList<DaySchedule>(), fixture.local().observeRange(900001, DATE, DATE.plus(30, DateTimeUnit.DAY)).first())
            assertNull(fixture.local().get(900001, DATE.plus(30, DateTimeUnit.DAY)))
            assertEquals(listOf(own), fixture.local().observeRange(null, DATE, DATE).first())
            assertEquals(listOf(other), fixture.local().observeRange(9000012, DATE, DATE).first())
        } finally {
            collector.cancelAndJoin()
        }
    }

    @Test
    fun anEntryThatIsNotGzipOrDoesNotDecodeIsAMissNotACrash() = withCache { fixture ->
        val next = DATE.plus(1, DateTimeUnit.DAY)
        fileSystem.createDirectories(CACHE_DIR)
        fileSystem.write(CACHE_DIR / "default_$DATE.json") { writeUtf8("not gzip") }
        val entry = """{"date":"$next","timestamp":${clock.now().toEpochMilliseconds()},"data":"{\"dayNumber\":1}"}"""
        fileSystem.sink(CACHE_DIR / "default_$next.json").gzip().buffer().use { it.writeUtf8(entry) }
        val local = fixture.local()

        assertEquals(emptyList<DaySchedule>(), local.observeRange(null, DATE, next).first())
        assertNull(local.get(null, DATE))
        assertNull(local.get(null, next))
    }

    private fun withCache(block: suspend TestScope.(Fixture) -> Unit) = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        block(Fixture(AppDispatchers(io = dispatcher, default = dispatcher, main = dispatcher)))
    }

    private inner class Fixture(private val dispatchers: AppDispatchers) {
        fun local() = ScheduleLocalDataSourceImpl(clock, CACHE_DIR, dispatchers, fileSystem)
    }

    private fun day(date: LocalDate, note: String? = null) = DaySchedule(date.dayOfWeek.isoDayNumber, 1, date, note, emptyList())

    private companion object {
        val DATE: LocalDate = LocalDate(2026, 9, 7)
        val CACHE_DIR = "/cache/schedule_cache".toPath()
    }
}
