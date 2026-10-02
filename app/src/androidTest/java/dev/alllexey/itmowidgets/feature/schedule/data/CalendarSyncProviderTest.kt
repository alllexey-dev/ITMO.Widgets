package dev.alllexey.itmowidgets.feature.schedule.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import dev.alllexey.itmowidgets.core.location.BuildingDirectory
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncResult
import dev.alllexey.itmowidgets.core.schedule.CalendarTarget
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.AndroidPhoneCalendars
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.CalendarSyncFileStore
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.CalendarSyncRepositoryImpl
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.OwnScheduleSource
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Room
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Calendar synchronization against the emulator's real CalendarProvider: the app's local calendar, events written
 * twice, updated, deleted, moved to another calendar and cleaned up. The schedule is synthetic and the dates are far
 * ahead, so no lesson is in the past.
 */
@RunWith(AndroidJUnit4::class)
class CalendarSyncProviderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val resolver get() = context.contentResolver
    private val calendars = AndroidPhoneCalendars(context, Time)
    private val folder = File(context.cacheDir, "calendar_sync_test")
    private var days: List<DaySchedule> = emptyList()
    private var otherCalendar: Long? = null

    @Before
    fun grantAndClean() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        AndroidPhoneCalendars.PERMISSIONS.forEach { automation.grantRuntimePermission(context.packageName, it) }
        folder.deleteRecursively()
        calendars.findOwn()?.let(calendars::deleteOwn)
    }

    @After
    fun cleanUp() {
        calendars.findOwn()?.let(calendars::deleteOwn)
        otherCalendar?.let { id ->
            resolver.delete(syncAdapter(ContentUris.withAppendedId(Calendars.CONTENT_URI, id), OTHER_ACCOUNT), null, null)
        }
        folder.deleteRecursively()
    }

    @Test
    fun turningOnCreatesAVisibleLocalCalendarAndTwoSyncsLeaveNoDuplicates() = runBlocking {
        days = listOf(day(MONDAY, lesson(1), lesson(2, LocalTime.of(11, 40), flowTypeId = 5)), day(MONDAY.plusDays(1), lesson(3)))
        val repository = repository()

        assertEquals(CalendarSyncResult.DONE, repository.enable(CalendarTarget.AppCalendar))
        assertEquals(AppResult.Success(Unit), repository.sync())
        assertEquals(AppResult.Success(Unit), repository.sync())
        assertEquals(AppResult.Success(Unit), repository().sync())

        val own = calendars.findOwn()!!
        resolver.query(
            ContentUris.withAppendedId(Calendars.CONTENT_URI, own),
            arrayOf(Calendars.ACCOUNT_TYPE, Calendars.CALENDAR_DISPLAY_NAME, Calendars.VISIBLE, Calendars.CALENDAR_ACCESS_LEVEL),
            null, null, null
        )!!.use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(CalendarContract.ACCOUNT_TYPE_LOCAL, cursor.getString(0))
            assertEquals("ITMO.Widgets", cursor.getString(1))
            assertEquals(1, cursor.getInt(2))
            assertEquals(Calendars.CAL_ACCESS_OWNER, cursor.getInt(3))
        }
        val events = events(own)
        assertEquals(listOf("lesson-1", "lesson-2", "lesson-3"), events.map { it.uid.substringBefore('@') })
        val first = events.first()
        assertEquals("Физика", first.title)
        assertEquals(Instant.parse("2030-09-09T07:00:00Z").toEpochMilli(), first.start)
        assertEquals(Instant.parse("2030-09-09T08:30:00Z").toEpochMilli(), first.end)
        assertEquals("Europe/Moscow", first.timeZone)
        assertEquals("1506, Кронверкский пр., 49", first.location)
        assertEquals("Лекция\nТестовый преподаватель\nФИЗ ПИИКТ 3.2", first.description)
        assertEquals(Events.AVAILABILITY_BUSY, first.availability)
        assertEquals("lesson-1@widgets.alllexey.dev", first.uid)
        assertEquals(0, first.hasAlarm)
        resolver.query(
            ContentUris.withAppendedId(Events.CONTENT_URI, first.id),
            arrayOf(Events.CUSTOM_APP_PACKAGE, Events.CUSTOM_APP_URI), null, null, null
        )!!.use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(context.packageName, cursor.getString(0))
            assertEquals("lesson-1", cursor.getString(1))
        }
        resolver.query(CalendarContract.Reminders.CONTENT_URI, arrayOf(CalendarContract.Reminders._ID),
            "${CalendarContract.Reminders.EVENT_ID} = ?", arrayOf(first.id.toString()), null)!!.use { assertEquals(0, it.count) }
    }

    @Test
    fun aChangedLessonUpdatesItsEventAndAVanishedOneIsDeleted() = runBlocking {
        days = listOf(day(MONDAY, lesson(1), lesson(2, LocalTime.of(11, 40))))
        val repository = enabled()
        repository.sync()
        val own = calendars.findOwn()!!
        val before = events(own).associateBy { it.uid }

        days = listOf(day(MONDAY, lesson(1, LocalTime.of(13, 30), room = Room("2304"))))
        repository.sync()

        val after = events(own)
        assertEquals(listOf("lesson-1@widgets.alllexey.dev"), after.map { it.uid })
        assertEquals(before.getValue("lesson-1@widgets.alllexey.dev").id, after.single().id)
        assertEquals(Instant.parse("2030-09-09T10:30:00Z").toEpochMilli(), after.single().start)
        assertEquals("2304, Кронверкский пр., 49", after.single().location)
    }

    @Test
    fun pickingAnotherCalendarDeletesTheAppCalendarAndTheNextSyncFillsTheNewOne() = runBlocking {
        days = listOf(day(MONDAY, lesson(1), lesson(2, LocalTime.of(11, 40))))
        val repository = enabled()
        repository.sync()
        val own = calendars.findOwn()!!
        val other = createOtherCalendar()

        assertEquals(CalendarSyncResult.DONE, repository.enable(CalendarTarget.PhoneCalendar(other)))
        repository.sync()

        assertNull(calendars.findOwn())
        assertTrue(events(own).isEmpty())
        assertEquals(listOf("lesson-1", "lesson-2"), events(other).map { it.uid.substringBefore('@') })
        // The picker offers Google-account calendars only; this local stand-in is reached by id alone.
        assertTrue(calendars.writable().none { it.id == other })
    }

    @Test
    fun turningOffRemovesOnlyTheAppsEvents() = runBlocking {
        days = listOf(day(MONDAY, lesson(1)))
        val other = createOtherCalendar()
        val foreign = insertForeignEvent(other)
        val repository = repository()
        assertEquals(CalendarSyncResult.DONE, repository.enable(CalendarTarget.PhoneCalendar(other)))
        repository.sync()
        assertEquals(2, events(other).size)

        repository.disable()

        assertEquals(listOf(foreign), events(other).map { it.id })
        assertFalse(repository.isEnabled())
    }

    @Test
    fun eventsWhoseIdsWereLostAreFoundByTheMarker() = runBlocking {
        days = listOf(day(MONDAY, lesson(1), lesson(2, LocalTime.of(11, 40))))
        val other = createOtherCalendar()
        val foreign = insertForeignEvent(other)
        val store = CalendarSyncFileStore(folder, Gson())
        repository().apply {
            assertEquals(CalendarSyncResult.DONE, enable(CalendarTarget.PhoneCalendar(other)))
            sync()
        }
        assertEquals(3, events(other).size)
        // The file forgets the ids, as on the phone where a switch-off left events behind.
        store.write(store.read()!!.copy(events = emptyList()))

        // A sync replaces the untracked copies instead of doubling them...
        repository().sync()
        assertEquals(3, events(other).size)
        assertEquals(listOf("lesson-1", "lesson-2"), events(other).filter { it.id != foreign }.map { it.uid.substringBefore('@') })
        store.write(store.read()!!.copy(events = emptyList()))

        // ...and turning off sweeps them by the marker, leaving the user's own event.
        repository().disable()
        assertEquals(listOf(foreign), events(other).map { it.id })
    }

    @Test
    fun turningOffTheAppCalendarDeletesIt() = runBlocking {
        days = listOf(day(MONDAY, lesson(1)))
        val repository = enabled()
        repository.sync()

        repository.disable()

        assertNull(calendars.findOwn())
    }

    @Test
    fun aDeletedCalendarTurnsSyncOff() = runBlocking {
        days = listOf(day(MONDAY, lesson(1)))
        val repository = enabled()
        repository.sync()
        calendars.deleteOwn(calendars.findOwn()!!)

        assertEquals(AppResult.Success(Unit), repository.sync())

        assertFalse(repository.isEnabled())
        assertNull(calendars.findOwn())
    }

    private suspend fun enabled() = repository().also { assertEquals(CalendarSyncResult.DONE, it.enable(CalendarTarget.AppCalendar)) }

    private fun repository() = CalendarSyncRepositoryImpl(
        calendars,
        OwnScheduleSource { _, _ -> days },
        CalendarSyncFileStore(folder, Gson()),
        Time,
        BuildingDirectory(emptyList())
    )

    private fun createOtherCalendar(): Long {
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, OTHER_ACCOUNT)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, "itmo_widgets_test")
            put(Calendars.CALENDAR_DISPLAY_NAME, "Учёба")
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, OTHER_ACCOUNT)
            put(Calendars.VISIBLE, 1)
        }
        return ContentUris.parseId(resolver.insert(syncAdapter(Calendars.CONTENT_URI, OTHER_ACCOUNT), values)!!)
            .also { otherCalendar = it }
    }

    private fun insertForeignEvent(calendarId: Long): Long = ContentUris.parseId(
        resolver.insert(Events.CONTENT_URI, ContentValues().apply {
            put(Events.CALENDAR_ID, calendarId)
            put(Events.TITLE, "Встреча")
            put(Events.DTSTART, Instant.parse("2030-09-09T15:00:00Z").toEpochMilli())
            put(Events.DTEND, Instant.parse("2030-09-09T16:00:00Z").toEpochMilli())
            put(Events.EVENT_TIMEZONE, "Europe/Moscow")
        })!!
    )

    private fun events(calendarId: Long): List<StoredEvent> = resolver.query(
        Events.CONTENT_URI,
        arrayOf(
            Events._ID, Events.TITLE, Events.DTSTART, Events.DTEND, Events.EVENT_TIMEZONE, Events.EVENT_LOCATION,
            Events.DESCRIPTION, Events.AVAILABILITY, Events.UID_2445, Events.HAS_ALARM
        ),
        "${Events.CALENDAR_ID} = ? AND ${Events.DELETED} = 0",
        arrayOf(calendarId.toString()),
        "${Events.DTSTART}, ${Events._ID}"
    )!!.use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    StoredEvent(
                        cursor.getLong(0), cursor.getString(1), cursor.getLong(2), cursor.getLong(3), cursor.getString(4),
                        cursor.getString(5), cursor.getString(6), cursor.getInt(7), cursor.getString(8).orEmpty(),
                        cursor.getInt(9)
                    )
                )
            }
        }
    }

    private fun syncAdapter(uri: android.net.Uri, account: String) = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, account)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    private fun day(date: LocalDate, vararg lessons: Lesson) = DaySchedule(date.dayOfWeek.value, 1, date, null, lessons.toList())

    private fun lesson(
        pairId: Long,
        start: LocalTime = LocalTime.of(10, 0),
        room: Room? = Room("1506"),
        flowTypeId: Int = 2
    ) = Lesson(
        pairId = pairId, start = start, end = start.plusMinutes(90), type = "Лекция", typeId = Lesson.TypeId(1),
        note = null, subjectName = "Физика", subjectId = pairId * 10, groupName = "ФИЗ ПИИКТ 3.2",
        flowId = pairId * 100, flowTypeId = flowTypeId, teacherIsu = 300001, teacherFio = "Тестовый преподаватель",
        room = room, building = Building("Кронверкский пр., 49"), buildingId = null, mainBuildingId = null,
        format = "Очный", formatId = 1, zoomUrl = null, zoomPassword = null, zoomInfo = null
    )

    private data class StoredEvent(
        val id: Long,
        val title: String,
        val start: Long,
        val end: Long,
        val timeZone: String,
        val location: String?,
        val description: String?,
        val availability: Int,
        val uid: String,
        val hasAlarm: Int
    )

    /** Monday 2030-09-09, 08:00 in Moscow: every lesson of the tests is ahead. */
    private object Time : AcademicTimeProvider {
        override val zoneId: ZoneId = ZoneId.of("Europe/Moscow")
        override fun today(): LocalDate = MONDAY
        override fun now(): OffsetDateTime = MONDAY.atTime(8, 0).atZone(zoneId).toOffsetDateTime()
    }

    private companion object {
        val MONDAY: LocalDate = LocalDate.of(2030, 9, 9)
        const val OTHER_ACCOUNT = "ITMO.Widgets test"
    }
}
