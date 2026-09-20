package dev.alllexey.itmowidgets.feature.schedule.data.home

import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.SchedulePreferencesRepository
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.util.DataState
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.home.HomeScheduleSelector
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Room
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleHomeCardSourceTest {
    private val repository = FakeScheduleRepository()
    private val pending = FakePending()
    private val preferences = FakePreferences()
    private val source = ScheduleHomeCardSource(repository, pending, preferences, Today, HomeScheduleSelector())

    @Test
    fun `the card is built from today and tomorrow of the cache`() = runTest {
        repository.days.value = listOf(DaySchedule(1, 1, Today.today(), null, listOf(lesson(1, "13:30"))))

        val card = source.observe().first().single() as HomeCard.Schedule

        assertEquals(1, card.rows.size)
        val request = repository.observed.single()
        assertEquals(Today.today(), request.start)
        assertEquals(Today.today().plusDays(1), request.end)
    }

    @Test
    fun `pending sport rows follow the schedule preference`() = runTest {
        pending.values.value = DataState.Success(listOf(booking()))

        assertEquals(0, (source.observe().first().single() as HomeCard.Schedule).rows.size)

        preferences.enabled.value = true
        assertEquals(1, (source.observe().first().single() as HomeCard.Schedule).rows.size)
    }

    @Test
    fun `a failed refresh reports the error while the cache keeps the card`() = runTest {
        repository.days.value = listOf(DaySchedule(1, 1, Today.today(), null, listOf(lesson(1, "13:30"))))
        repository.refreshResult = AppResult.Failure(AppError.Network)

        assertEquals(AppResult.Failure(AppError.Network), source.refresh())
        assertEquals(1, pending.refreshes)
        assertTrue(source.observe().first().single() is HomeCard.Schedule)
        val request = repository.refreshed.single()
        assertEquals(Today.today().plusDays(1), request.end)
    }

    private fun lesson(pairId: Long, start: String) = Lesson(
        pairId = pairId, start = LocalTime.parse(start), end = LocalTime.parse(start).plusMinutes(90), type = "Лекция",
        typeId = Lesson.TypeId(1), note = null, subjectName = "Предмет", subjectId = pairId, groupName = "M3100",
        flowId = 100, flowTypeId = 2, teacherIsu = 300001, teacherFio = "Преподаватель", room = Room("1506"),
        building = Building("Кронверкский проспект, 49"), buildingId = 13, mainBuildingId = 13, format = "Очный", formatId = 1,
        zoomUrl = null, zoomPassword = null, zoomInfo = null
    )

    private fun booking() = PendingSportBooking(
        queueId = 1, queueKind = PendingSportBooking.QueueKind.AUTO, lessonId = 101, sectionName = "Бассейн",
        start = Today.today().atTime(16, 0).atZone(Today.zoneId).toOffsetDateTime(),
        end = Today.today().atTime(17, 30).atZone(Today.zoneId).toOffsetDateTime(),
        teacherFio = "Тренер", roomName = "Бассейн", isPrediction = false
    )

    private object Today : AcademicTimeProvider {
        override val zoneId: ZoneId = ZoneId.of("Europe/Moscow")
        override fun today(): LocalDate = LocalDate.of(2026, 9, 7)
        override fun now() = today().atTime(12, 0).atZone(zoneId).toOffsetDateTime()
    }

    private class FakePreferences : SchedulePreferencesRepository {
        val enabled = MutableStateFlow(false)
        override fun observeSportAutoSignEnabled() = enabled
    }

    private class FakePending : PendingSportBookingsRepository {
        val values = MutableStateFlow<DataState<List<PendingSportBooking>>>(DataState.Success(emptyList()))
        var refreshes = 0
        override fun observePendingBookings() = values
        override suspend fun refresh() { refreshes++ }
    }

    private class FakeScheduleRepository : ScheduleRepository {
        val days = MutableStateFlow<List<DaySchedule>>(emptyList())
        val observed = mutableListOf<Request>()
        val refreshed = mutableListOf<Request>()
        var refreshResult: AppResult<Unit> = AppResult.Success(Unit)

        data class Request(val userIsu: Int?, val start: LocalDate, val end: LocalDate)

        override fun observeScheduleForRange(userIsu: Int?, startDate: LocalDate, endDate: LocalDate): Flow<List<DaySchedule>> {
            observed += Request(userIsu, startDate, endDate)
            return days.map { list -> list.filter { !it.date.isBefore(startDate) && !it.date.isAfter(endDate) } }
        }

        override suspend fun refreshSchedule(userIsu: Int?, startDate: LocalDate, endDate: LocalDate): AppResult<Unit> {
            refreshed += Request(userIsu, startDate, endDate)
            return refreshResult
        }

        override suspend fun clearCaches() = Unit
    }
}
