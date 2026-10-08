package dev.alllexey.itmowidgets.feature.schedule.data.calendar

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.location.BuildingDirectory
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncProblem
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import dev.alllexey.itmowidgets.core.testing.FakeSessionTokenStore
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.work.CheckOutcome
import dev.alllexey.itmowidgets.feature.schedule.FakeCalendarSyncScheduler
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarEvent
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.OwnScheduleSource
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Room
import dev.alllexey.itmowidgets.feature.schedule.plusMinutes
import dev.alllexey.itmowidgets.testkit.FakeClock
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import dev.alllexey.itmowidgets.testkit.fakeFileSystemOf
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

/**
 * The shared calendar sync (`CalendarSyncRepositoryImpl`, `DefaultCalendarSync`) on iOS's [EventKitPhoneCalendars]
 * over a fake event store, with the sync file and the EventKit id file on okio's fake file system: what IO-15b adds
 * between the sync and EventKit.
 */
class EventKitCalendarSyncTest {

    private val main = TestMainDispatcher()
    private val dispatchers = main.dispatcher.let { AppDispatchers(io = it, default = it, main = it) }
    private val fileSystem: FakeFileSystem = fakeFileSystemOf()
    private val store = FakeEventStore()
    private val clock = FakeClock(Instant.parse("2026-09-07T06:00:00Z"))
    private val tokens = FakeSessionTokenStore()
    private val scheduler = FakeCalendarSyncScheduler()
    private var days: List<DaySchedule> = listOf(day(MONDAY, lesson(1), lesson(2, start = LocalTime(13, 30))))
    private val source = OwnScheduleSource { _, _ -> days }

    private var calendars = calendars()
    private var repository = repository()
    private var sync = DefaultCalendarSync(repository, scheduler, tokens, noDemo())

    private val own: String get() = store.calendars.keys.single()

    @BeforeTest
    fun setUp() {
        main.install()
    }

    @AfterTest
    fun tearDown() {
        main.reset()
        fileSystem.checkNoOpenFiles()
    }

    @Test
    fun withoutFullAccessTurningOnCreatesNothing() = runTest {
        store.access = false

        assertEquals(CalendarSyncResult.NO_PERMISSION, sync.enable())

        assertEquals(0, store.calendarsCreated)
        assertEquals(CalendarSyncState(), sync.observeState().first())
    }

    @Test
    fun turningOnCreatesTheOwnCalendarOnceAndRunsTheStep() = runTest {
        assertEquals(CalendarSyncResult.DONE, sync.enable())
        assertEquals(CheckOutcome.DONE, sync.run())
        sync.disable()
        assertEquals(CalendarSyncResult.DONE, sync.enable())

        assertEquals(EventKitPhoneCalendars.OWN_CALENDAR_TITLE, store.calendars[own])
        assertEquals(2, store.calendarsCreated, "turning off deletes the calendar, turning on makes a new one")
        assertEquals(2, scheduler.runOnceCalls)
    }

    @Test
    fun aSecondSyncChangesNothing() = runTest {
        sync.enable()
        sync.run()
        val saves = store.saves

        assertEquals(CheckOutcome.DONE, sync.run())

        assertEquals(listOf("lesson-1", "lesson-2"), store.eventsIn(own).map { it.key })
        assertEquals(saves, store.saves)
        assertEquals(0, store.updates)
        assertEquals(0, store.removes)
    }

    @Test
    fun theIdsSurviveANewProcess() = runTest {
        sync.enable()
        sync.run()

        restart()
        sync.run()

        assertEquals(1, store.calendarsCreated)
        assertEquals(listOf("lesson-1", "lesson-2"), store.eventsIn(own).map { it.key })
        assertEquals(2, store.saves)
    }

    @Test
    fun aChangedLessonIsUpdatedInPlace() = runTest {
        sync.enable()
        sync.run()

        days = listOf(day(MONDAY, lesson(1, room = Room("2304")), lesson(2, start = LocalTime(13, 30))))
        sync.run()

        assertEquals(1, store.updates)
        assertTrue(store.eventsIn(own).first().location.orEmpty().startsWith("2304"))
        assertEquals(2, store.events.size)
    }

    @Test
    fun anEventEventKitRenumberedIsNotDoubled() = runTest {
        sync.enable()
        sync.run()
        store.renumber(store.events.keys.first())

        days = listOf(day(MONDAY, lesson(1, room = Room("2304")), lesson(2, start = LocalTime(13, 30))))
        sync.run()

        assertEquals(listOf("lesson-1", "lesson-2"), store.eventsIn(own).map { it.key }.sorted())
    }

    @Test
    fun aTaggedEventWithoutItsIdIsReplacedOnce() = runTest {
        sync.enable()
        sync.run()
        val lost = store.addTagged(own, store.eventsIn(own).first())

        sync.run()

        assertFalse(lost in store.events)
        assertEquals(listOf("lesson-1", "lesson-2"), store.eventsIn(own).map { it.key })
    }

    @Test
    fun turningOffRemovesTheCalendarWithItsEvents() = runTest {
        sync.enable()
        sync.run()

        sync.disable()

        assertTrue(store.calendars.isEmpty())
        assertTrue(store.events.isEmpty())
        assertEquals(CalendarSyncState(), sync.observeState().first())
        assertEquals(CheckOutcome.SKIPPED, sync.run())
    }

    @Test
    fun signingOutRemovesTheCalendar() = runTest {
        sync.enable()
        sync.run()

        repository.clearSessionData()

        assertTrue(store.calendars.isEmpty())
        assertTrue(store.events.isEmpty())
        assertFalse(repository.isEnabled())
    }

    @Test
    fun aCalendarTheUserDeletedTurnsTheSyncOff() = runTest {
        sync.enable()
        sync.run()
        store.removeCalendar(own)

        sync.run()

        assertEquals(CalendarSyncState(problem = CalendarSyncProblem.CALENDAR_MISSING), sync.observeState().first())
    }

    @Test
    fun theCalendarOfAnEarlierInstallIsReplaced() = runTest {
        val earlier = store.addCalendar(EventKitPhoneCalendars.OWN_CALENDAR_TITLE)
        store.addTagged(earlier, CalendarEvent("lesson-9", "Физика", START, START, null, null))

        sync.enable()
        sync.run()

        assertFalse(earlier in store.calendars, "a reinstall leaves no second ITMO.Widgets calendar")
        assertEquals(listOf("lesson-1", "lesson-2"), store.eventsIn(own).map { it.key })
    }

    @Test
    fun aCalendarOfAnotherTitleIsNeverDeleted() = runTest {
        val users = store.addCalendar("Личный")
        sync.enable()
        sync.run()
        val ids = EventKitIdStore(idFile())

        calendars.deleteOwn(ids.idOf(users))

        assertTrue(users in store.calendars)
    }

    @Test
    fun aDamagedIdFileStartsEmpty() {
        fileSystem.createDirectories(ID_FILE.parent!!)
        fileSystem.write(ID_FILE) { writeUtf8("{not json") }

        assertNull(EventKitIdStore(idFile()).identifierOf(1))
    }

    private fun restart() {
        calendars = calendars()
        repository = repository()
        sync = DefaultCalendarSync(repository, scheduler, tokens, noDemo())
    }

    private fun idFile() = AtomicTextFile(ID_FILE, fileSystem)

    private fun calendars() = EventKitPhoneCalendars(store, EventKitIdStore(idFile()))

    private fun repository() = CalendarSyncRepositoryImpl(
        calendars, source, CalendarSyncFileStore("/files/calendar_sync".toPath(), fileSystem), MoscowTime(clock),
        BuildingDirectory(emptyList()), dispatchers
    )

    private fun day(date: LocalDate, vararg lessons: Lesson) =
        DaySchedule(date.dayOfWeek.isoDayNumber, 1, date, null, lessons.toList())

    private fun lesson(pairId: Long, start: LocalTime = LocalTime(10, 0), room: Room? = Room("1506")) = Lesson(
        pairId = pairId, start = start, end = start.plusMinutes(90), type = "Лекция", typeId = Lesson.TypeId(1),
        note = null, subjectName = "Физика", subjectId = pairId * 10, groupName = "ФИЗ ПИИКТ 3.2", flowId = pairId * 100,
        flowTypeId = 2, teacherIsu = 300001, teacherFio = "Тестовый преподаватель", room = room,
        building = Building("Кронверкский пр., 49"), buildingId = 13, mainBuildingId = 13, format = "Очный", formatId = 1,
        zoomUrl = null, zoomPassword = null, zoomInfo = null
    )

    private class MoscowTime(private val clock: Clock) : AcademicTimeProvider {
        override val timeZone: TimeZone = TimeZone.of("Europe/Moscow")

        override fun today(): LocalDate = now().toLocalDateTime(timeZone).date

        override fun now(): Instant = clock.now()
    }

    private companion object {
        /** 2026-09-07 09:00 in Moscow is a Monday morning. */
        val MONDAY: LocalDate = LocalDate(2026, 9, 7)
        val START: Instant = Instant.parse("2026-09-08T07:00:00Z")
        val ID_FILE = "/no-backup/calendar_eventkit/ids.json".toPath()
    }
}
