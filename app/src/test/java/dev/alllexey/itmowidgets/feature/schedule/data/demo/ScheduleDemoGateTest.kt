package dev.alllexey.itmowidgets.feature.schedule.data.demo

import api.myitmo.MyItmoApi
import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
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
import kotlinx.coroutines.test.runTest
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
    private val myItmo = unreachable<MyItmoApi>()
    private val backend = unreachable<ItmoWidgetsApi>()
    // The stored opt-in is on: the demo alone must keep every request local.
    private val gate = FakeBackendGate(optedIn = true, demo)
    private val week = time.today().minusDays(time.today().dayOfWeek.value - 1L).let { it..it.plusDays(6) }

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

        val days = source.read(week.start, week.start.plusDays(40))

        assertEquals(41, days.size)
        assertTrue(days.sumOf { it.lessons.size } > 20)
    }
}
