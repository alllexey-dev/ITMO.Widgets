package dev.alllexey.itmowidgets.feature.schedule.data.home

import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.testing.FakePendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.testing.FakeSchedulePreferencesRepository
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.FakeScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.home.HomeScheduleSelector
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Room
import dev.alllexey.itmowidgets.feature.schedule.plusMinutes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleHomeCardSourceTest {
    private val repository = FakeScheduleRepository()
    private val pending = FakePendingSportBookingsRepository()
    private val preferences = FakeSchedulePreferencesRepository()
    private val source = ScheduleHomeCardSource(repository, pending, preferences, Today, HomeScheduleSelector())

    @Test
    fun `the card is built from today and tomorrow of the cache`() = runTest {
        repository.days.value = listOf(DaySchedule(1, 1, Today.today(), null, listOf(lesson(1, "13:30"))))

        val card = source.observe().first().single() as HomeCard.Schedule

        assertEquals(1, card.rows.size)
        val request = repository.observed.single()
        assertEquals(Today.today(), request.startDate)
        assertEquals(Today.today().plus(1, DateTimeUnit.DAY), request.endDate)
    }

    @Test
    fun `pending sport rows follow the schedule preference`() = runTest {
        pending.values.value = AppResult.Success(listOf(booking()))

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
        assertEquals(Today.today().plus(1, DateTimeUnit.DAY), request.endDate)
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
        start = Today.today().atTime(16, 0).toInstant(Today.timeZone),
        end = Today.today().atTime(17, 30).toInstant(Today.timeZone),
        teacherFio = "Тренер", roomName = "Бассейн", isPrediction = false
    )

    private object Today : AcademicTimeProvider by FixedAcademicTime(LocalDateTime(2026, 9, 7, 12, 0))
}
