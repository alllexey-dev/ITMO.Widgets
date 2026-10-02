package dev.alllexey.itmowidgets.feature.schedule.data.calendar

import com.google.gson.Gson
import dev.alllexey.itmowidgets.core.location.BuildingDirectory
import dev.alllexey.itmowidgets.core.location.KnownBuilding
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncProblem
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.core.testing.MutableClock
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.FakePhoneCalendars
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarEvent
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.OwnScheduleSource
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.SyncedEvent
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Room
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CalendarSyncRepositoryImplTest {
    @get:Rule val temporary = TemporaryFolder()

    private val folder by lazy { File(temporary.root, "calendar_sync") }
    private val calendars = FakePhoneCalendars().apply { add(GOOGLE) }
    private val clock = MutableClock(Instant.parse("2026-09-07T06:00:00Z"))
    private val requests = mutableListOf<Pair<LocalDate, LocalDate>>()
    private var days: List<DaySchedule> = emptyList()
    private var failure: Exception? = null
    /** Runs inside the request, before the answer; a test holds the answer here. */
    @Volatile private var gate: suspend () -> Unit = {}
    private val source = OwnScheduleSource { start, end ->
        requests += start to end
        gate()
        failure?.let { throw it }
        days
    }
    private val store get() = CalendarSyncFileStore(folder, Gson())
    private val own get() = calendars.ownId!!

    @Test
    fun `turning on without the permission creates nothing`() = runTest {
        calendars.access = false
        val repository = repository()

        assertEquals(CalendarSyncResult.NO_PERMISSION, repository.enable())
        assertNull(calendars.ownId)
        assertFalse(repository.isEnabled())
    }

    @Test
    fun `turning on creates the app calendar and the first sync fills the window from today`() = runTest {
        days = listOf(day(MONDAY, lesson(1), lesson(2, start = LocalTime.of(13, 30))), day(MONDAY.plusDays(28), lesson(3)))
        val repository = repository()

        assertEquals(CalendarSyncResult.DONE, repository.enable())
        assertEquals(AppResult.Success(Unit), repository.sync())

        assertEquals(listOf(MONDAY to MONDAY.plusDays(28)), requests)
        assertEquals(listOf("lesson-1", "lesson-2", "lesson-3"), calendars.eventsIn(own).map { it.key })
        assertEquals(CalendarSyncState(enabled = true), repository.observeState().first())
        assertEquals("1506, Кронверкский проспект, 49, Санкт-Петербург", calendars.eventsIn(own).first().location)
        assertTrue(calendars.eventsIn(GOOGLE).isEmpty())
    }

    @Test
    fun `syncing twice adds nothing twice`() = runTest {
        days = listOf(day(MONDAY, lesson(1), lesson(2, start = LocalTime.of(13, 30))))
        val repository = enabled()

        repository.sync()
        repository.sync()
        repository().sync()

        assertEquals(2, calendars.events.size)
        assertEquals(2, calendars.inserts)
        assertEquals(0, calendars.updates)
    }

    @Test
    fun `a changed lesson updates its event and a cancelled one is deleted`() = runTest {
        days = listOf(day(MONDAY, lesson(1, start = LocalTime.of(10, 0)), lesson(2, start = LocalTime.of(13, 30))))
        val repository = enabled()
        repository.sync()
        val idOfFirst = calendars.events.entries.first { it.value.second.key == "lesson-1" }.key

        days = listOf(day(MONDAY, lesson(1, start = LocalTime.of(11, 40), room = Room("2304"))))
        repository.sync()

        val (_, moved) = calendars.events.getValue(idOfFirst)
        assertEquals(listOf("lesson-1"), calendars.events.values.map { it.second.key })
        assertEquals(Instant.parse("2026-09-07T08:40:00Z"), moved.start)
        assertEquals("2304, Кронверкский проспект, 49, Санкт-Петербург", moved.location)
    }

    @Test
    fun `a lesson over before the sync stays when the schedule drops it`() = runTest {
        days = listOf(day(MONDAY, lesson(1, start = LocalTime.of(10, 0))))
        val repository = enabled()
        repository.sync()

        clock.advance(Duration.ofHours(6))
        days = emptyList()
        repository.sync()

        assertEquals(listOf("lesson-1"), calendars.events.values.map { it.second.key })
        assertEquals(0, calendars.deletes)
    }

    @Test
    fun `an event deleted by the user comes back with the next change`() = runTest {
        days = listOf(day(MONDAY, lesson(1, start = LocalTime.of(10, 0))))
        val repository = enabled()
        repository.sync()
        calendars.events.clear()

        days = listOf(day(MONDAY, lesson(1, start = LocalTime.of(11, 40))))
        repository.sync()

        assertEquals(listOf("lesson-1"), calendars.events.values.map { it.second.key })
    }

    @Test
    fun `turning off deletes the app calendar in one operation`() = runTest {
        days = listOf(day(MONDAY, lesson(1), lesson(2, start = LocalTime.of(13, 30))))
        val repository = enabled()
        repository.sync()

        repository.disable()

        assertNull(calendars.ownId)
        assertEquals(setOf(GOOGLE), calendars.calendars)
        assertEquals(0, calendars.deletes)
        assertTrue(calendars.events.isEmpty())
        assertEquals(CalendarSyncState(), repository.observeState().first())
        assertFalse(repository.hasPendingCleanup())
    }

    @Test
    fun `a revoked permission turns sync off and turning on again adopts the app calendar`() = runTest {
        days = listOf(day(MONDAY, lesson(1)))
        val repository = enabled()
        repository.sync()
        calendars.access = false

        assertEquals(AppResult.Success(Unit), repository.sync())

        assertEquals(CalendarSyncState(problem = CalendarSyncProblem.NO_PERMISSION), repository.observeState().first())
        calendars.access = true
        repository.enable()
        repository.sync()
        assertEquals(1, calendars.events.size)
        assertEquals(1, calendars.inserts)
    }

    @Test
    fun `a deleted app calendar turns sync off and forgets the events`() = runTest {
        days = listOf(day(MONDAY, lesson(1)))
        val repository = enabled()
        repository.sync()
        calendars.deleteOwn(own)

        assertEquals(AppResult.Success(Unit), repository.sync())

        assertEquals(CalendarSyncState(problem = CalendarSyncProblem.CALENDAR_MISSING), repository.observeState().first())
        assertTrue(store.read()!!.events.isEmpty())
    }

    @Test
    fun `a failed request changes nothing and is a network error`() = runTest {
        days = listOf(day(MONDAY, lesson(1)))
        val repository = enabled()
        repository.sync()
        failure = IOException("offline")

        assertEquals(AppResult.Failure(AppError.Network), repository.sync())

        assertEquals(1, calendars.events.size)
        assertTrue(repository.isEnabled())
    }

    @Test
    fun `events inserted before the provider failed are kept, so the next sync adds no duplicate`() = runTest {
        days = listOf(day(MONDAY, lesson(1), lesson(2, start = LocalTime.of(13, 30)), lesson(3, start = LocalTime.of(15, 20))))
        val repository = enabled()
        calendars.failOnInsert = 2

        assertEquals(AppResult.Failure(AppError.Unknown()), repository.sync())
        assertEquals(listOf("lesson-1"), store.read()!!.events.map { it.key })
        assertEquals(AppResult.Success(Unit), repository.sync())

        assertEquals(listOf("lesson-1", "lesson-2", "lesson-3"), calendars.events.values.map { it.second.key })
    }

    @Test
    fun `turning off while a sync waits for My ITMO leaves no event behind`() = runTest {
        days = listOf(day(MONDAY, lesson(1), lesson(2, start = LocalTime.of(13, 30))))
        val repository = enabled()
        val answer = CompletableDeferred<Unit>()
        val asked = CompletableDeferred<Unit>()
        gate = { asked.complete(Unit); answer.await() }

        val sync = async(Dispatchers.Default) { repository.sync() }
        asked.await()
        val disable = async(Dispatchers.Default) { repository.disable() }
        answer.complete(Unit)
        sync.await()
        disable.await()

        assertTrue(calendars.events.isEmpty())
        assertNull(calendars.ownId)
        assertEquals(CalendarSyncState(), repository.observeState().first())
    }

    @Test
    fun `turning off during the inserts of the first sync leaves nothing behind`() = runTest {
        days = listOf(day(MONDAY, lesson(1), lesson(2, start = LocalTime.of(13, 30)), lesson(3, start = LocalTime.of(15, 20))))
        val repository = enabled()
        var disable: Deferred<Unit>? = null
        calendars.onInsert = {
            if (disable == null) disable = async(Dispatchers.Default) { repository.disable() }
        }

        repository.sync()
        disable!!.await()

        assertTrue(calendars.events.isEmpty())
        assertFalse(repository.isEnabled())
        assertTrue(store.read()!!.events.isEmpty())
    }

    @Test
    fun `a sync cancelled while writing keeps every inserted id`() = runTest {
        days = listOf(day(MONDAY, lesson(1), lesson(2, start = LocalTime.of(13, 30)), lesson(3, start = LocalTime.of(15, 20))))
        val repository = enabled()
        lateinit var sync: Job
        calendars.onInsert = { sync.cancel() }

        sync = launch(Dispatchers.Default) { repository.sync() }
        sync.join()
        calendars.onInsert = {}

        assertEquals(3, calendars.events.size)
        assertEquals(calendars.events.keys.toList(), store.read()!!.events.map { it.eventId })
    }

    @Test
    fun `every insert is written at once`() = runTest {
        days = listOf(day(MONDAY, lesson(1), lesson(2, start = LocalTime.of(13, 30))))
        val repository = enabled()
        val written = mutableListOf<Int>()
        calendars.onInsert = { written += (store.read()?.events?.size ?: 0) }

        repository.sync()

        // Each insert sees the ids of the inserts before it already in the file.
        assertEquals(listOf(0, 1), written)
    }

    @Test
    fun `a sync deletes tagged events it does not know before inserting, so nothing doubles`() = runTest {
        days = listOf(day(MONDAY, lesson(1), lesson(2, start = LocalTime.of(13, 30))))
        enabled().sync()
        val foreign = calendars.insertForeign(own, calendars.eventsIn(own).first().copy(key = "user"))
        store.write(store.read()!!.copy(events = emptyList()))

        repository().sync()

        assertEquals(listOf("user", "lesson-1", "lesson-2"), calendars.eventsIn(own).map { it.key })
        assertTrue(foreign in calendars.events)
        assertEquals(2, store.read()!!.events.size)
    }

    @Test
    fun `a Google calendar of an earlier build reads as off and the next run leaves it without inserting`() = runTest {
        days = listOf(day(MONDAY, lesson(1), lesson(2, start = LocalTime.of(13, 30))))
        val tracked = googleState(enabled = true)
        val foreign = calendars.insertForeign(GOOGLE, event("user"))
        val repository = repository()

        assertFalse(repository.isEnabled())
        assertTrue(repository.hasPendingCleanup())
        assertEquals(AppResult.Success(Unit), repository.sync())

        assertEquals(listOf(foreign), calendars.events.keys.toList())
        assertTrue(tracked.none { it in calendars.events })
        assertNull(calendars.ownId)
        assertTrue(requests.isEmpty())
        assertEquals(listOf(GOOGLE), store.read()!!.cleanups.orEmpty().map { it.calendarId })
        assertEquals(CalendarSyncState(), repository.observeState().first())
    }

    @Test
    fun `events Google writes back into the left calendar are swept until it stays clean`() = runTest {
        googleState(enabled = true)
        val repository = repository()
        repository.sync()

        // Android's guard undid the deletes: the sync adapter writes the server's copies back as new rows.
        calendars.writeBack(GOOGLE, event("lesson-1"))
        calendars.writeBack(GOOGLE, event("lesson-2"))
        repository.sync()
        assertTrue(calendars.events.isEmpty())
        assertTrue(repository.hasPendingCleanup())
        assertTrue(requests.isEmpty())

        clock.advance(Duration.ofDays(4))
        repository.sync()
        assertFalse(repository.hasPendingCleanup())
    }

    @Test
    fun `turning on after an earlier Google calendar writes only into the app calendar`() = runTest {
        days = listOf(day(MONDAY, lesson(1)))
        googleState(enabled = false)
        val repository = repository()

        assertEquals(CalendarSyncResult.DONE, repository.enable())
        repository.sync()

        assertTrue(calendars.eventsIn(GOOGLE).isEmpty())
        assertEquals(listOf("lesson-1"), calendars.eventsIn(own).map { it.key })
        assertTrue(repository.hasPendingCleanup())
    }

    @Test
    fun `a failed delete in a left calendar keeps its id for the next turn on or off`() = runTest {
        val tracked = googleState(enabled = true)
        calendars.failOnDelete += tracked.first()

        repository().sync()
        assertEquals(listOf(tracked.first()), store.read()!!.events.map { it.eventId })
        calendars.failOnDelete.clear()
        repository().disable()

        assertTrue(calendars.events.isEmpty())
        assertTrue(store.read()!!.events.isEmpty())
    }

    @Test
    fun `a file of an earlier build without calendar ids is cleaned up completely`() = runTest {
        days = listOf(day(MONDAY, lesson(1), lesson(2, start = LocalTime.of(13, 30))))
        val ids = listOf(calendars.insert(GOOGLE, event("lesson-1")), calendars.insert(GOOGLE, event("lesson-2")))
        folder.mkdirs()
        File(folder, "state.json").writeText(
            """{"format":1,"enabled":true,"target":"phone","calendarId":$GOOGLE,"calendarName":"Учёба",""" +
                """"events":[""" + ids.joinToString(",") { id ->
                    """{"key":"k$id","eventId":$id,"start":0,"end":1,"title":"Физика"}"""
                } + "]}"
        )

        repository().disable()

        assertTrue(calendars.events.isEmpty())
    }

    @Test
    fun `sign-out deletes the events and the file`() = runTest {
        days = listOf(day(MONDAY, lesson(1)))
        val repository = enabled()
        repository.sync()

        repository.clearSessionData()

        assertNull(calendars.ownId)
        assertTrue(calendars.events.isEmpty())
        assertFalse(folder.exists())
        assertFalse(repository.isEnabled())
    }

    @Test
    fun `a corrupt file starts over switched off`() = runTest {
        folder.mkdirs()
        File(folder, "state.json").writeText("{broken")

        assertFalse(repository().isEnabled())
        assertFalse(File(folder, "state.json").exists())
    }

    /** The state an earlier build left after writing two lessons into a Google calendar; their ids. */
    private fun googleState(enabled: Boolean): List<Long> {
        val events = listOf(event("lesson-1"), event("lesson-2"))
        val synced = events.map { SyncedEvent(calendars.insert(GOOGLE, it), it) }
        store.write(
            StoredCalendarSync(
                enabled = enabled, target = TARGET_PHONE, calendarId = GOOGLE, calendarName = "Учёба",
                events = synced.map { it.toStored(GOOGLE) }
            )
        )
        return synced.map { it.eventId }
    }

    private fun event(key: String) = CalendarEvent(
        key = key, title = "Физика", start = Instant.parse("2026-09-08T07:00:00Z"),
        end = Instant.parse("2026-09-08T08:30:00Z"), location = null, description = null
    )

    private suspend fun enabled() = repository().also { check(it.enable() == CalendarSyncResult.DONE) }

    private fun repository() = CalendarSyncRepositoryImpl(calendars, source, store, ClockTime(clock), BUILDINGS)

    private fun day(date: LocalDate, vararg lessons: Lesson) = DaySchedule(date.dayOfWeek.value, 1, date, null, lessons.toList())

    private fun lesson(pairId: Long, start: LocalTime = LocalTime.of(10, 0), room: Room? = Room("1506")) = Lesson(
        pairId = pairId, start = start, end = start.plusMinutes(90), type = "Лекция", typeId = Lesson.TypeId(1),
        note = null, subjectName = "Физика", subjectId = pairId * 10, groupName = "ФИЗ ПИИКТ 3.2", flowId = pairId * 100,
        flowTypeId = 2, teacherIsu = 300001, teacherFio = "Тестовый преподаватель", room = room,
        building = Building("Кронверкский пр., 49"), buildingId = 13, mainBuildingId = 13, format = "Очный", formatId = 1,
        zoomUrl = null, zoomPassword = null, zoomInfo = null
    )

    private class ClockTime(private val clock: Clock) : AcademicTimeProvider {
        override val zoneId: ZoneId = ZoneId.of("Europe/Moscow")
        override fun today(): LocalDate = LocalDate.ofInstant(clock.instant(), zoneId)
        override fun now(): OffsetDateTime = OffsetDateTime.ofInstant(clock.instant(), zoneId)
    }

    private companion object {
        /** 2026-09-07 09:00 in Moscow is a Monday morning. */
        val MONDAY: LocalDate = LocalDate.of(2026, 9, 7)
        /** The Google calendar an earlier build wrote to. */
        const val GOOGLE = 7L
        val BUILDINGS = BuildingDirectory(
            listOf(
                KnownBuilding(
                    "kronva", listOf(13), listOf("кронв"), "Кронверкский пр., 49",
                    "Кронверкский проспект, 49, Санкт-Петербург", 59.95, 30.30
                )
            )
        )
    }
}
