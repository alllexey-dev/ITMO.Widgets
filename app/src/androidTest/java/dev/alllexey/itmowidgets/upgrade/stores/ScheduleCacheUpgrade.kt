package dev.alllexey.itmowidgets.upgrade.stores

import dev.alllexey.itmowidgets.core.storage.AndroidAppDirectories
import dev.alllexey.itmowidgets.feature.schedule.data.local.ScheduleLocalDataSourceImpl
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Room
import dev.alllexey.itmowidgets.testing.DeviceDispatchers
import dev.alllexey.itmowidgets.upgrade.Captured22
import dev.alllexey.itmowidgets.upgrade.Upgrade22Fixture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.time.Clock
import kotlin.time.toKotlinInstant

/**
 * Cache `cache/schedule_cache/123456_2026-10-05.json` (gzip, Gson): read or ignored, never a crash. kotlinx writes the
 * same shape back, which a new instance reads.
 */
object ScheduleCacheUpgrade {

    fun check(fixture: Upgrade22Fixture): Unit = runBlocking {
        val date = LocalDate.parse("2026-10-05")
        val expected = DaySchedule(
            dayNumber = 1,
            weekNumber = 6,
            date = date,
            note = null,
            lessons = listOf(
                Lesson(
                    pairId = 5001,
                    start = LocalTime.parse("10:00"),
                    end = LocalTime.parse("11:30"),
                    type = "Лекция",
                    typeId = Lesson.TypeId(1),
                    note = null,
                    subjectName = Captured22.SUBJECT,
                    subjectId = Captured22.SUBJECT_ID,
                    groupName = "Т3100",
                    flowId = 3001,
                    flowTypeId = 2,
                    teacherIsu = 100101,
                    teacherFio = "Тестовый преподаватель",
                    room = Room("101"),
                    building = Building("Тестовый корпус"),
                    buildingId = 13,
                    mainBuildingId = 13,
                    format = "Очный",
                    formatId = 1,
                    zoomUrl = null,
                    zoomPassword = null,
                    zoomInfo = null
                )
            )
        )

        val directories = AndroidAppDirectories(fixture.context)
        val clock = object : Clock { override fun now() = fixture.clock.instant().toKotlinInstant() }
        val cache = ScheduleLocalDataSourceImpl(clock, directories, DeviceDispatchers)
        val days = cache.observeRange(Captured22.ISU, date, date).first()
        assertTrue("schedule_cache read as $days", days.isEmpty() || days == listOf(expected))

        cache.save(expected, Captured22.ISU)
        val reopened = ScheduleLocalDataSourceImpl(clock, directories, DeviceDispatchers)
        assertEquals(listOf(expected), reopened.observeRange(Captured22.ISU, date, date).first())
    }
}
