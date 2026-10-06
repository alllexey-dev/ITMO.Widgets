package dev.alllexey.itmowidgets.feature.schedule.data.demo

import dev.alllexey.itmowidgets.client.schedule.ScheduleApi
import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.demo.DemoSportSlots
import dev.alllexey.itmowidgets.core.demo.DemoStudy
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.unreachable
import dev.alllexey.itmowidgets.feature.schedule.data.LessonFriendsRepositoryImpl
import dev.alllexey.itmowidgets.feature.schedule.data.calendar.MyItmoOwnScheduleSource
import dev.alllexey.itmowidgets.feature.schedule.data.remote.ScheduleRemoteDataSourceImpl
import dev.alllexey.itmowidgets.feature.schedule.data.remote.unreachableScheduleMyItmoClient
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The demo schedule is read from the demo set; My ITMO and Backend are never asked. */
class ScheduleDemoGateTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatchers = mainDispatcherRule.appDispatchers

    private val demo = FakeDemoMode(active = true)
    private val time = FixedAcademicTime()
    private val myItmo = unreachableScheduleMyItmoClient()
    private val backend = unreachable<ScheduleApi>()
    // The stored opt-in is on: the demo alone must keep every request local.
    private val gate = FakeBackendGate(optedIn = true, demo)
    private val week = time.today().minus(time.today().dayOfWeek.isoDayNumber - 1L, DateTimeUnit.DAY).let { it..it.plus(6, DateTimeUnit.DAY) }

    @Test
    fun `the own week has lessons, the volleyball and no Sunday classes`() = runTest {
        val remote = ScheduleRemoteDataSourceImpl(gate, myItmo, backend, time, demo, dispatchers)

        val days = remote.getSchedule(null, week.start, week.endInclusive)

        assertEquals(7, days.size)
        assertTrue(days.flatMap { it.lessons }.map { it.subjectId }.containsAll(DemoStudy.CURRENT.dropLast(1).map { it.id }))
        assertTrue(days.flatMap { it.lessons }.any { it.subjectName == DemoSportSlots.ANNA_WEEKLY.section && it.typeId.raw == 11 })
        assertTrue(days.last().lessons.isEmpty())
    }

    @Test
    fun `a friend's schedule and friends on a lesson come from the demo set`() = runTest {
        val remote = ScheduleRemoteDataSourceImpl(gate, myItmo, backend, time, demo, dispatchers)
        val friends = LessonFriendsRepositoryImpl(gate, backend, demo, dispatchers)

        val ivan = remote.getSchedule(DemoPeople.IVAN.isu, week.start, week.endInclusive)
        val lecture = ivan.first { day -> day.lessons.any { it.typeId.raw == 1 } }.let { day -> day.date to day.lessons.first { it.typeId.raw == 1 } }
        val onLecture = (friends.friendsOnLesson(lecture.second.pairId, lecture.first) as AppResult.Success).value

        assertTrue(ivan.all { it.weekNumber == -1 })
        assertTrue(onLecture.map { it.isu }.containsAll(listOf(DemoPeople.IVAN.isu, DemoPeople.MARIA.isu)))
    }

    @Test
    fun `the export source reads the demo schedule`() = runTest {
        val source = MyItmoOwnScheduleSource(myItmo, time, demo, dispatchers)

        val days = source.read(week.start, week.start.plus(40, DateTimeUnit.DAY))

        assertEquals(41, days.size)
        assertTrue(days.sumOf { it.lessons.size } > 20)
    }
}
