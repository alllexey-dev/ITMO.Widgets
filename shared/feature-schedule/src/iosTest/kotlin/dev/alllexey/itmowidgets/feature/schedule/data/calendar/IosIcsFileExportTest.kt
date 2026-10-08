package dev.alllexey.itmowidgets.feature.schedule.data.calendar

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.location.BuildingDirectory
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.IcsFile
import dev.alllexey.itmowidgets.core.schedule.ScheduleExportRange
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.OwnScheduleSource
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Room
import dev.alllexey.itmowidgets.testkit.fakeFileSystemOf
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import okio.IOException
import okio.Path.Companion.toPath

/**
 * The `.ics` file of iOS's sheet (IO-15b) on okio's fake file system: the same range, events and file name as
 * Android's `IcsFileExport`, in the temporary directory, by the address `ShareLink` takes.
 */
class IosIcsFileExportTest {

    private val fileSystem = fakeFileSystemOf()
    private val dispatchers = UnconfinedTestDispatcher().let { AppDispatchers(io = it, default = it, main = it) }
    private val requests = mutableListOf<Pair<LocalDate, LocalDate>>()
    private var days: List<DaySchedule> = emptyList()
    private var failure: Exception? = null
    private val export = IosIcsFileExport(
        OwnScheduleSource { start, end ->
            requests += start to end
            failure?.let { throw it }
            days
        },
        FixedAcademicTime(TODAY.atTime(9, 0)),
        BuildingDirectory(emptyList()),
        dispatchers,
        DIRECTORY,
        fileSystem,
    ) { path -> "file://$path" }

    @AfterTest
    fun noOpenFiles() = fileSystem.checkNoOpenFiles()

    @Test
    fun theWeekFromTodayIsWrittenWithEveryLesson() = runTest {
        days = listOf(day(TODAY, lesson(1), lesson(2)), day(TODAY.plus(6, DateTimeUnit.DAY), lesson(3)))

        val result = export.export(ScheduleExportRange.Week)

        val name = "itmo-schedule-2026-10-02-2026-10-08.ics"
        assertEquals(listOf(TODAY to TODAY.plus(6, DateTimeUnit.DAY)), requests)
        assertEquals(AppResult.Success(IcsFile("file:///tmp/ics/$name", name, 3)), result)
        val text = fileSystem.read(DIRECTORY / name) { readUtf8() }
        assertEquals(3, Regex("BEGIN:VEVENT").findAll(text).count())
        assertTrue("UID:lesson-3@widgets.alllexey.dev\r\n" in text)
    }

    @Test
    fun aRangeWithoutLessonsWritesNothing() = runTest {
        assertEquals(AppResult.Success(null), export.export(ScheduleExportRange.TwoWeeks))

        assertFalse(fileSystem.exists(DIRECTORY))
    }

    @Test
    fun onlyTheLatestFileIsKept() = runTest {
        days = listOf(day(TODAY, lesson(1)))
        export.export(ScheduleExportRange.Week)

        export.export(ScheduleExportRange.Custom(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30)))

        assertEquals(listOf("itmo-schedule-2026-09-01-2026-09-30.ics"), fileSystem.list(DIRECTORY).map { it.name })
    }

    @Test
    fun aFailedReadIsAnErrorAndNoFile() = runTest {
        failure = IOException("offline")

        assertIs<AppResult.Failure>(export.export(ScheduleExportRange.Semester))
        assertFalse(fileSystem.exists(DIRECTORY))
    }

    private fun day(date: LocalDate, vararg lessons: Lesson) =
        DaySchedule(date.dayOfWeek.isoDayNumber, 1, date, null, lessons.toList())

    private fun lesson(pairId: Long) = Lesson(
        pairId = pairId, start = LocalTime(10, 0), end = LocalTime(11, 30), type = "Лекция",
        typeId = Lesson.TypeId(1), note = null, subjectName = "Физика", subjectId = pairId * 10,
        groupName = "ФИЗ ПИИКТ 3.2", flowId = pairId * 100, flowTypeId = 2, teacherIsu = 300001,
        teacherFio = "Тестовый преподаватель", room = Room("1506"), building = Building("Кронверкский пр., 49"),
        buildingId = 13, mainBuildingId = 13, format = "Очный", formatId = 1, zoomUrl = null, zoomPassword = null,
        zoomInfo = null
    )

    private companion object {
        val TODAY: LocalDate = LocalDate(2026, 10, 2)
        val DIRECTORY = "/tmp/ics".toPath()
    }
}
