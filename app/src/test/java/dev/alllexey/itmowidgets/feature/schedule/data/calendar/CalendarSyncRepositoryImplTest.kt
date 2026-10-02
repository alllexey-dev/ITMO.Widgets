package dev.alllexey.itmowidgets.feature.schedule.data.calendar

import com.google.gson.Gson
import dev.alllexey.itmowidgets.core.location.BuildingDirectory
import dev.alllexey.itmowidgets.core.location.KnownBuilding
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncProblem
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.core.schedule.CalendarTarget
import dev.alllexey.itmowidgets.core.schedule.WritableCalendar
import dev.alllexey.itmowidgets.core.testing.MutableClock
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.FakePhoneCalendars
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.OwnScheduleSource
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
import kotlinx.coroutines.flow.first
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
    private val source = OwnScheduleSource { start, end ->
        requests += start to end
        failure?.let { throw it }
        days
    }
    private val store get() = CalendarSyncFileStore(folder, Gson())

    @Test
    fun `turning on without the permission creates nothing`() = runTest {
        calendars.access = false
        val repository = repository()

        assertEquals(CalendarSyncResult.NO_PERMISSION, repository.enable(CalendarTarget.AppCalendar))
        assertNull(calendars.ownId)
        assertFalse(repository.isEnabled())
    }

    @Test
    fun `turning on creates the app calendar and the first sync fills the window from today`() = runTest {
        days = listOf(day(MONDAY, lesson(1), lesson(2, start = LocalTime.of(13, 30))), day(MONDAY.plusDays(28), lesson(3)))
        val repository = repository()

        assertEquals(CalendarSyncResult.DONE, repository.enable(CalendarTarget.AppCalendar))
        assertEquals(AppResult.Success(Unit), repository.sync())

        val own = calendars.ownId!!
        assertEquals(listOf(MONDAY to MONDAY.plusDays(28)), requests)
        assertEquals(listOf("lesson-1", "lesson-2", "lesson-3"), calendars.eventsIn(own).map { it.key })
        assertEquals(CalendarSyncState(enabled = true, target = CalendarTarget.AppCalendar), repository.observeState().first())
        assertEquals("1506, Кронверкский проспект, 49, Санкт-Петербург", calendars.eventsIn(own).first().location)
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
    fun `picking another calendar moves the events and deletes the app calendar`() = runTest {
        days = listOf(day(MONDAY, lesson(1), lesson(2, start = LocalTime.of(13, 30))))
        val repository = enabled()
        repository.sync()
        val own = calendars.ownId!!

        assertEquals(CalendarSyncResult.DONE, repository.enable(CalendarTarget.PhoneCalendar(GOOGLE.id)))

        assertFalse(own in calendars.calendars)
        assertEquals(listOf("lesson-1", "lesson-2"), calendars.eventsIn(GOOGLE.id).map { it.key })
        assertEquals(
            CalendarSyncState(enabled = true, target = CalendarTarget.PhoneCalendar(GOOGLE.id), calendarName = "Учёба", calendarAccount = "student@gmail.com"),
            repository.observeState().first()
        )
        val inserts = calendars.inserts
        repository.sync()
        assertEquals(inserts, calendars.inserts)
        assertEquals(2, calendars.events.size)
    }

    @Test
    fun `a calendar that is gone cannot be picked`() = runTest {
        val repository = repository()

        assertEquals(CalendarSyncResult.CALENDAR_MISSING, repository.enable(CalendarTarget.PhoneCalendar(404)))
        assertFalse(repository.isEnabled())
    }

    @Test
    fun `turning off deletes only the app's events and its calendar`() = runTest {
        days = listOf(day(MONDAY, lesson(1)))
        val repository = repository()
        repository.enable(CalendarTarget.PhoneCalendar(GOOGLE.id))
        repository.sync()
        val foreign = calendars.insert(GOOGLE.id, calendars.eventsIn(GOOGLE.id).single().copy(key = "user", title = "Встреча"))

        repository.disable()

        assertEquals(listOf(foreign), calendars.events.keys.toList())
        assertEquals(CalendarSyncState(), repository.observeState().first())
        assertTrue(store.read()!!.events.isEmpty())
    }

    @Test
    fun `turning the app calendar off deletes it`() = runTest {
        val repository = enabled()

        repository.disable()

        assertNull(calendars.ownId)
        assertTrue(calendars.calendars.keys == setOf(GOOGLE.id))
    }

    @Test
    fun `a revoked permission turns sync off and the same calendar later adopts the events`() = runTest {
        days = listOf(day(MONDAY, lesson(1)))
        val repository = repository()
        repository.enable(CalendarTarget.PhoneCalendar(GOOGLE.id))
        repository.sync()
        calendars.access = false

        assertEquals(AppResult.Success(Unit), repository.sync())

        assertEquals(
            CalendarSyncState(target = CalendarTarget.PhoneCalendar(GOOGLE.id), calendarName = "Учёба", calendarAccount = "student@gmail.com", problem = CalendarSyncProblem.NO_PERMISSION),
            repository.observeState().first()
        )
        calendars.access = true
        repository.enable(CalendarTarget.PhoneCalendar(GOOGLE.id))
        repository.sync()
        assertEquals(1, calendars.events.size)
        assertEquals(1, calendars.inserts)
    }

    @Test
    fun `a deleted calendar turns sync off and forgets the events`() = runTest {
        days = listOf(day(MONDAY, lesson(1)))
        val repository = repository()
        repository.enable(CalendarTarget.PhoneCalendar(GOOGLE.id))
        repository.sync()
        calendars.calendars.remove(GOOGLE.id)

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
    fun `writable calendars leave the app's own out and are null without the permission`() = runTest {
        val repository = enabled()

        assertEquals(listOf(GOOGLE), repository.writableCalendars())
        calendars.access = false
        assertNull(repository.writableCalendars())
    }

    @Test
    fun `a corrupt file starts over switched off`() = runTest {
        folder.mkdirs()
        File(folder, "state.json").writeText("{broken")

        assertFalse(repository().isEnabled())
        assertFalse(File(folder, "state.json").exists())
    }

    private suspend fun enabled() = repository().also { check(it.enable(CalendarTarget.AppCalendar) == CalendarSyncResult.DONE) }

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
        val GOOGLE = WritableCalendar(7, "Учёба", "student@gmail.com")
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
