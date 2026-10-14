package dev.alllexey.itmowidgets.feature.schedule.data.days

import dev.alllexey.itmowidgets.core.lesson.LessonTarget
import dev.alllexey.itmowidgets.core.lesson.SportMark
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import dev.alllexey.itmowidgets.core.testing.FakePendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.testing.FakeSchedulePreferencesRepository
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.scheduleChange
import dev.alllexey.itmowidgets.core.testing.slot
import dev.alllexey.itmowidgets.feature.schedule.FakeScheduleChangesRepository
import dev.alllexey.itmowidgets.feature.schedule.FakeScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.data.local.CacheEntry
import dev.alllexey.itmowidgets.feature.schedule.data.local.ScheduleLocalDataSource
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimelineSamples
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class ScheduleDaysImplTest {

    private val tuesday = LocalDate(2026, 9, 8)
    private val wednesday = LocalDate(2026, 9, 9)

    @Test
    fun aCachedLessonCarriesTheNewestChangeFoundOnIt() = runTest {
        val lesson = ScheduleWidgetTimelineSamples.lesson(1, "08:20", "09:50", "Математический анализ", room = "2202")
        val moved = scheduleChange(
            fields = setOf(ScheduleChangeField.PLACE),
            before = slot(1, wednesday, room = "1506"),
            after = slot(1, wednesday, room = "2202"),
        )
        val gateway = gateway(days = listOf(ScheduleWidgetTimelineSamples.day(wednesday, lesson)), changes = arrayOf(moved))

        val day = gateway.observe(tuesday, wednesday).first().single()

        assertEquals(wednesday, day.date)
        assertEquals(Instant.fromEpochMilliseconds(CACHED_AT), day.cachedAt)
        val change = day.lessons.single().change!!
        assertEquals("1506", change.previousRoom)
        assertEquals(false, change.cancelled)
        assertTrue(day.lessons.single().target is LessonTarget.Lesson)
    }

    @Test
    fun aCancelledLessonStaysOnItsOldDayStruckThrough() = runTest {
        val cancelled = scheduleChange(kind = ScheduleChangeKind.CANCELLED)
        val gateway = gateway(changes = arrayOf(cancelled))

        val day = gateway.observe(tuesday, wednesday).first().single()

        assertEquals(tuesday, day.date)
        assertEquals(true, day.lessons.single().change?.cancelled)
        assertEquals(LocalTime(8, 20), day.lessons.single().start)
    }

    @Test
    fun ownQueuesJoinTheirDayOnlyWhileTheQueueRowsAreOn() = runTest {
        val queued = ScheduleWidgetTimelineSamples.pending(5, tuesday, "15:20")

        val on = gateway(queues = true, bookings = arrayOf(queued)).observe(tuesday, wednesday).first().single()
        assertEquals(SportMark.QUEUED, on.lessons.single().sport)
        assertEquals("Секция 5", on.lessons.single().subject)

        val off = gateway(queues = false, bookings = arrayOf(queued)).observe(tuesday, wednesday).first()
        assertTrue(off.isEmpty())
    }

    @Test
    fun aBookedSportLessonIsMarkedBooked() = runTest {
        val sport = ScheduleWidgetTimelineSamples.lesson(7, "17:00", "18:30", "Волейбол").copy(flowTypeId = 3)
        val day = gateway(days = listOf(ScheduleWidgetTimelineSamples.day(tuesday, sport))).observe(tuesday, wednesday).first().single()

        assertEquals(SportMark.BOOKED, day.lessons.single().sport)
        assertNull(day.lessons.single().change)
    }

    @Test
    fun anotherUsersLessonSaysWithYouWhenTheViewerAttendsItToo() = runTest {
        val shared = ScheduleWidgetTimelineSamples.lesson(1, "08:20", "09:50", "Математический анализ")
        val theirs = ScheduleWidgetTimelineSamples.lesson(2, "10:00", "11:30", "История")
        val repository = FakeScheduleRepository(listOf(ScheduleWidgetTimelineSamples.day(tuesday, shared)))
        repository.schedulesFor(FRIEND).value = listOf(ScheduleWidgetTimelineSamples.day(tuesday, shared, theirs))
        val days = gateway(repository = repository)

        val day = days.observe(FRIEND, tuesday, wednesday).first().single()

        assertEquals(listOf(true, false), day.lessons.map { it.withYou })
    }

    @Test
    fun anotherUsersRefreshLeavesTheOwnQueuesAlone() = runTest {
        val repository = FakeScheduleRepository()
        val pending = FakePendingSportBookingsRepository()
        val days = gateway(repository = repository, pending = pending)

        days.refresh(FRIEND, tuesday, wednesday)

        assertEquals(FRIEND, repository.refreshed.single().userIsu)
        assertEquals(0, pending.refreshes)
    }

    private fun gateway(
        days: List<DaySchedule> = emptyList(),
        repository: FakeScheduleRepository = FakeScheduleRepository(days),
        changes: Array<dev.alllexey.itmowidgets.core.schedule.ScheduleChange> = emptyArray(),
        queues: Boolean = true,
        bookings: Array<dev.alllexey.itmowidgets.core.sport.PendingSportBooking> = emptyArray(),
        pending: FakePendingSportBookingsRepository = FakePendingSportBookingsRepository(*bookings),
    ) = ScheduleDaysImpl(
        repository = repository,
        local = CachedAt,
        changes = FakeScheduleChangesRepository(*changes),
        pending = pending,
        preferences = FakeSchedulePreferencesRepository(enabled = queues),
        time = FixedAcademicTime(tuesday),
    )

    /** Every cached day was saved at [CACHED_AT]; nothing else is read. */
    private object CachedAt : ScheduleLocalDataSource {
        override fun observeRange(userIsu: Int?, start: LocalDate, end: LocalDate): Flow<List<DaySchedule>> = flowOf(emptyList())
        override suspend fun save(schedule: DaySchedule, userIsu: Int?) = Unit
        override suspend fun replaceRange(userIsu: Int?, start: LocalDate, end: LocalDate, schedules: List<DaySchedule>) = Unit
        override suspend fun get(userIsu: Int?, date: LocalDate) = CacheEntry(userIsu, date, CACHED_AT, "")
        override suspend fun clearUser(userIsu: Int) = Unit
        override suspend fun clear() = Unit
    }

    private companion object {
        const val CACHED_AT = 1_788_000_000_000L
        const val FRIEND = 300001
    }
}
