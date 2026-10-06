package dev.alllexey.itmowidgets.feature.schedule.data.changes

import dev.alllexey.itmowidgets.core.testing.ClockAcademicTime
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.blockingIoAppDispatchers
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import dev.alllexey.itmowidgets.core.testing.RecordingAppNotifier
import dev.alllexey.itmowidgets.feature.schedule.data.remote.requestedRange
import dev.alllexey.itmowidgets.feature.schedule.data.remote.scheduleMyItmoClient
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleCheckResult
import java.io.File
import java.io.IOException
import java.net.UnknownHostException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ScheduleChangesRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** The fake network holds a request on a blocked thread; see [blockingIoAppDispatchers]. */
    private val dispatchers = blockingIoAppDispatchers(mainDispatcherRule.dispatcher)

    @get:Rule val temporary = TemporaryFolder()
    private val folder by lazy { File(temporary.root, "schedule_changes") }
    private val requests = CopyOnWriteArrayList<String>()
    /** Day objects of the next answer; [status] and [body] replace the whole answer when set. */
    @Volatile private var days: List<String> = emptyList()
    @Volatile private var status = 200
    @Volatile private var body: String? = null
    @Volatile private var gate: CountDownLatch? = null
    /** Thrown instead of any answer, as when a backgrounded app has no network. */
    @Volatile private var failure: IOException? = null
    private val myItmo = scheduleMyItmoClient { request ->
        requests += "${request.url.encodedPath}?${request.requestedRange()}"
        gate?.let { check(it.await(WAIT_SECONDS, TimeUnit.SECONDS)) { "The answer was never released" } }
        failure?.let { throw it }
        status to (body ?: """{"code":0,"data":[${days.joinToString(",")}],"message":null}""")
    }
    private val clock = MutableClock(Instant.parse("2026-09-07T09:00:00Z"))
    private val notifier = RecordingAppNotifier()
    private val store get() = ScheduleChangesFileStore(folder.toOkioPath())
    private val file get() = File(folder, "state.json")

    @Test
    fun `asks My ITMO for the personal schedule of today and seven days once`() = runTest {
        repository().check()

        assertEquals(listOf("/api/schedule/schedule/personal?2026-09-07..2026-09-14"), requests)
    }

    @Test
    fun `the first check is a baseline without changes`() = runTest {
        days = WEEK
        val repository = repository()

        assertEquals(AppResult.Success(ScheduleCheckResult.Baseline), repository.check())
        assertEquals(emptyList<ScheduleChange>(), repository.observeChanges().first())
        assertTrue(file.exists())
        assertEquals(3, store.read()!!.snapshot!!.lessons.size)
    }

    @Test
    fun `the same answer again finds nothing`() = runTest {
        days = WEEK
        val repository = repository()
        repository.check()

        assertEquals(AppResult.Success(ScheduleCheckResult.Compared(0)), repository.check())
    }

    @Test
    fun `a move and a cancel are stored unread and undelivered with the detection time`() = runTest {
        days = WEEK
        val repository = repository()
        repository.check()
        clock.advance(Duration.ofMinutes(5))
        days = listOf(day("2026-09-07", MONDAY), day("2026-09-10", lesson(2, start = "10:00")))

        assertEquals(AppResult.Success(ScheduleCheckResult.Compared(2)), repository.check())

        val millis = clock.millis()
        val changes = repository.observeChanges().first()
        assertEquals(listOf("$millis-0", "$millis-1"), changes.map { it.id })
        assertEquals(listOf(ScheduleChangeKind.UPDATED, ScheduleChangeKind.CANCELLED), changes.map { it.kind })
        assertEquals(setOf(ScheduleChangeField.TIME), changes[0].fields)
        assertEquals(kotlinx.datetime.LocalDate.parse("2026-09-10"), changes[0].after!!.date)
        assertEquals(kotlinx.datetime.LocalDate.parse("2026-09-09"), changes[1].before!!.date)
        assertTrue(changes.all { !it.read && !it.notified && it.detectedAt == kotlin.time.Instant.fromEpochMilliseconds(millis) })
        assertEquals("Физика 2", changes[0].subjectName)
        assertEquals("Поток 2", changes[0].flowName)
    }

    @Test
    fun `sport and room bookings coming and going are not changes`() = runTest {
        days = WEEK + day("2026-09-11", lesson(50, flowType = 3))
        val repository = repository()
        repository.check()
        days = WEEK + day("2026-09-12", lesson(60, flowType = 5))

        assertEquals(AppResult.Success(ScheduleCheckResult.Compared(0)), repository.check())
    }

    @Test
    fun `failed answers keep the snapshot and say why`() = runTest {
        days = WEEK
        val repository = repository()
        repository.check()
        val before = store.read()

        status = 500
        body = """{"code":500,"data":null,"message":"Synthetic failure"}"""
        assertTrue(repository.check() is AppResult.Failure)
        status = 401
        body = """{"code":401,"data":null,"message":"Synthetic"}"""
        assertEquals(AppResult.Failure(AppError.Unauthorized), repository.check())
        status = 200
        body = """{"code":0,"data":null,"message":null}"""
        assertTrue((repository.check() as AppResult.Failure).error is AppError.Unknown)

        assertEquals(before, store.read())
        body = null
        assertEquals(AppResult.Success(ScheduleCheckResult.Compared(0)), repository.check())
    }

    @Test
    fun `no network is a network failure that keeps the snapshot and finds nothing`() = runTest {
        days = WEEK
        val repository = repository()
        repository.check()
        val before = store.read()

        failure = UnknownHostException("Synthetic")
        assertEquals(AppResult.Failure(AppError.Network), repository.check())

        assertEquals(before, store.read())
        assertEquals(emptyList<ScheduleChange>(), repository.observeChanges().first())
        failure = null
        assertEquals(AppResult.Success(ScheduleCheckResult.Compared(0)), repository.check())
    }

    @Test
    fun `one empty answer is held and a second one cancels what is not over`() = runTest {
        days = WEEK
        val repository = repository()
        repository.check()
        val snapshot = store.read()!!.snapshot
        days = emptyList()

        assertEquals(AppResult.Success(ScheduleCheckResult.EmptyHeld), repository.check())
        assertEquals(snapshot, store.read()!!.snapshot)
        assertTrue(store.read()!!.emptyHeld)

        // The Monday morning lesson is over at noon, so only the two later lessons are cancelled.
        assertEquals(AppResult.Success(ScheduleCheckResult.Compared(2)), repository.check())
        assertTrue(repository.observeChanges().first().all { it.kind == ScheduleChangeKind.CANCELLED })
        assertFalse(store.read()!!.emptyHeld)

        assertEquals(AppResult.Success(ScheduleCheckResult.Compared(0)), repository.check())
    }

    @Test
    fun `more than five lessons landing on empty weeks are a new baseline`() = runTest {
        val repository = repository()
        repository.check()
        days = (1..6).map { day(date(it), lesson(it.toLong())) }

        assertEquals(AppResult.Success(ScheduleCheckResult.Baseline), repository.check())
        assertEquals(emptyList<ScheduleChange>(), repository.observeChanges().first())
        assertEquals(6, store.read()!!.snapshot!!.lessons.size)
    }

    @Test
    fun `five lessons landing on empty weeks are added`() = runTest {
        val repository = repository()
        repository.check()
        days = (1..5).map { day(date(it), lesson(it.toLong())) }

        assertEquals(AppResult.Success(ScheduleCheckResult.Compared(5)), repository.check())
        assertTrue(repository.observeChanges().first().all { it.kind == ScheduleChangeKind.ADDED })
    }

    @Test
    fun `a day that left the window is not cancelled the next day`() = runTest {
        days = WEEK
        val repository = repository()
        repository.check()
        clock.advance(Duration.ofDays(1))
        days = WEEK.drop(1)

        assertEquals(AppResult.Success(ScheduleCheckResult.Compared(0)), repository.check())
        assertEquals("/api/schedule/schedule/personal?2026-09-08..2026-09-15", requests.last())
    }

    @Test
    fun `changes are marked delivered and read and reading removes the digest`() = runTest {
        val repository = withTwoChanges()
        val ids = repository.observeChanges().first().map { it.id }

        repository.markNotified(setOf(ids[0]))
        assertEquals(listOf(true, false), repository.observeChanges().first().map { it.notified })

        repository.markAllRead()
        assertTrue(repository.observeChanges().first().all { it.read })
        assertTrue(store.read()!!.changes.all { it.read })
        assertEquals(listOf("schedule_changes" to 1), notifier.cancelled)
    }

    @Test
    fun `changes older than thirty days are hidden and dropped on the next write`() = runTest {
        val now = clock.millis()
        store.write(StoredScheduleChanges(changes = listOf(
            storedChange("old", now - Duration.ofDays(31).toMillis()),
            storedChange("new", now - Duration.ofDays(1).toMillis())
        )))
        val repository = repository()

        assertEquals(listOf("new"), repository.observeChanges().first().map { it.id })
        repository.markAllRead()

        assertEquals(listOf("new"), store.read()!!.changes.map { it.id })
    }

    @Test
    fun `only the five hundred newest changes are kept`() = runTest {
        val now = clock.millis()
        store.write(StoredScheduleChanges(changes = (0 until 520).map { storedChange("$it", now - it * 60_000L) }.reversed()))
        val repository = repository()

        repository.markNotified(setOf("0"))

        val kept = store.read()!!.changes.map { it.id }.toSet()
        assertEquals((0 until 500).map { "$it" }.toSet(), kept)
    }

    @Test
    fun `a reset snapshot makes the next check a baseline and keeps the history`() = runTest {
        val repository = withTwoChanges()

        repository.resetSnapshot()

        assertEquals(AppResult.Success(ScheduleCheckResult.Baseline), repository.check())
        assertEquals(2, repository.observeChanges().first().size)
    }

    @Test
    fun `clearing the session deletes the file and empties the history`() = runTest {
        val repository = withTwoChanges()

        repository.clearSessionData()

        assertFalse(folder.exists())
        assertEquals(emptyList<ScheduleChange>(), repository.observeChanges().first())
    }

    @Test
    fun `a check asked before the session was cleared writes nothing`() = runTest {
        days = WEEK
        val repository = repository()
        repository.check()
        gate = CountDownLatch(1)
        days = emptyList()
        val pending = async(Dispatchers.Default) { repository.check() }
        awaitRequests(2)

        repository.clearSessionData()
        gate!!.countDown()

        assertEquals(AppResult.Failure(AppError.Unauthorized), pending.await())
        assertFalse(file.exists())
    }

    @Test
    fun `a corrupt file starts over with a baseline and is rewritten`() = runTest {
        folder.mkdirs()
        file.writeText("{broken")
        days = WEEK
        val repository = repository()

        assertEquals(AppResult.Success(ScheduleCheckResult.Baseline), repository.check())
        assertEquals(3, store.read()!!.snapshot!!.lessons.size)
    }

    @Test
    fun `a new repository reads the same state`() = runTest {
        val first = withTwoChanges()

        assertEquals(first.observeChanges().first(), repository().observeChanges().first())
        assertEquals(AppResult.Success(ScheduleCheckResult.Compared(0)), repository().check())
    }

    /** A baseline of [WEEK], then the Tuesday lesson moved to Thursday and the Wednesday one cancelled. */
    private suspend fun withTwoChanges(): ScheduleChangesRepositoryImpl {
        days = WEEK
        val repository = repository()
        repository.check()
        days = listOf(day("2026-09-07", MONDAY), day("2026-09-10", lesson(2, start = "10:00")))
        check(repository.check() == AppResult.Success(ScheduleCheckResult.Compared(2)))
        return repository
    }

    @Test
    fun `the demo shows its changes and checks without My ITMO`() = runTest {
        val repository = repository(FakeDemoMode(active = true))

        val changes = repository.observeChanges().first()
        val check = repository.check()

        assertEquals(2, changes.size)
        assertTrue(changes.all { it.subjectName.isNotBlank() })
        assertTrue(check is AppResult.Success)
        assertTrue(requests.isEmpty())
        assertFalse(file.exists())
    }

    private fun repository(demo: DemoMode = noDemo()) = ScheduleChangesRepositoryImpl(myItmo, store, ClockAcademicTime(clock), clock, notifier, demo, dispatchers)

    private fun awaitRequests(count: Int) {
        repeat(WAIT_SECONDS.toInt() * 100) { if (requests.size >= count) return; Thread.sleep(10) }
        error("Only ${requests.size} of $count requests were made")
    }

    private fun storedChange(id: String, detectedAt: Long) = StoredChange(
        id = id, detectedAt = detectedAt, kind = "CANCELLED", fields = emptyList(), subjectName = "Физика", typeId = 1,
        flowName = null, read = false, notified = false, after = null,
        before = StoredLesson(
            pairId = 1, date = "2026-09-08", start = "10:00", end = "11:30", subjectId = 1, subjectName = "Физика",
            typeId = 1, flowId = 1, flowName = null, teacherIsu = null, teacherName = null, room = null, building = null,
            formatId = 1, format = null
        )
    )

    private class MutableClock(private var now: Instant) : Clock() {
        fun advance(duration: Duration) { now = now.plus(duration) }
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }

    private companion object {
        const val WAIT_SECONDS = 10L

        fun date(offset: Int): String = LocalDate.parse("2026-09-07").plusDays(offset.toLong()).toString()

        fun day(date: String, vararg lessons: String) =
            """{"day_number":1,"week_number":1,"date":"$date","lessons":[${lessons.joinToString(",")}]}"""

        fun lesson(pairId: Long, start: String = "10:00", end: String = "11:30", flowType: Int = 2) =
            """{"pair_id":$pairId,"subject":"Физика $pairId","subject_id":$pairId,"time_start":"$start",""" +
                """"time_end":"$end","teacher_id":300001,"teacher_name":"Тестовый преподаватель","room":"1506",""" +
                """"building":"Кронверкский проспект, 49","format":"Очный","format_id":1,"work_type":"Лекция",""" +
                """"work_type_id":1,"group":"Поток $pairId","flow_type_id":$flowType,"flow_id":$pairId}"""

        /** Over at noon on Monday, the first day of the window. */
        val MONDAY = lesson(1, start = "08:20", end = "09:50")
        val WEEK = listOf(
            day("2026-09-07", MONDAY),
            day("2026-09-08", lesson(2)),
            day("2026-09-09", lesson(3))
        )
    }
}
