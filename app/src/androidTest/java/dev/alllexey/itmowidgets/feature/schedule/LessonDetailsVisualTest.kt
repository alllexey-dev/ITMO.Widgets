package dev.alllexey.itmowidgets.feature.schedule

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.EntryPointAccessors
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.notification.NotificationDebugEntryPoint
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.util.DataState
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Room
import dev.alllexey.itmowidgets.feature.schedule.ui.ScheduleFragment
import dev.alllexey.itmowidgets.feature.schedule.ui.ScheduleLifecycleTestActivity
import dev.alllexey.itmowidgets.feature.schedule.ui.details.LessonDetailsArgs
import dev.alllexey.itmowidgets.feature.schedule.ui.details.LessonDetailsBottomSheet
import dev.alllexey.itmowidgets.feature.schedule.ui.details.toDetailsArgs
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestUi
import dev.alllexey.itmowidgets.testing.ViewChecks
import dev.alllexey.itmowidgets.testing.ViewChecks.descendants
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The real card → sheet path on the lifecycle host; the opt-in is off, so the friends block stays hidden. */
@RunWith(AndroidJUnit4::class)
class LessonDetailsVisualTest {
    private val settings = EntryPointAccessors
        .fromApplication(ApplicationProvider.getApplicationContext(), NotificationDebugEntryPoint::class.java)
        .settings()
    private var originalServices = false

    @Before
    fun servicesOff() {
        originalServices = runBlocking { settings.getCustomServicesEnabled() }
        runBlocking { settings.setCustomServicesEnabled(false) }
    }

    @After
    fun restore() {
        runBlocking { settings.setCustomServicesEnabled(originalServices) }
        ScheduleLifecycleTestActivity.days = MutableStateFlow(emptyList())
    }

    @Test
    fun tappingACardOpensTheLessonAndTheSheetSurvivesRecreation() {
        withSchedule { scenario ->
            scenario.onActivity { activity ->
                val card = activity.recycler().descendants().first { it.id == R.id.card_container && it.isShown }
                assertTrue(card.isClickable)
                card.performClick()
            }
            settle()
            scenario.onActivity { activity ->
                val sheet = activity.sheet()
                val root = sheet.requireView()
                assertEquals(lesson().subjectName, root.text(R.id.subject_name))
                assertEquals("Лекция · Очный", root.text(R.id.lesson_kind))
                assertTrue(root.fact(R.id.time_fact), root.fact(R.id.time_fact).endsWith("08:20–09:50"))
                assertEquals(lesson().teacherFio, root.fact(R.id.teacher_fact))
                assertEquals("1506 · Кронверкский проспект, 49", root.fact(R.id.location_fact))
                assertEquals(View.VISIBLE, root.findViewById<View>(R.id.map_button).visibility)
                assertEquals(View.GONE, root.findViewById<View>(R.id.zoom_button).visibility)
                assertEquals(View.VISIBLE, root.findViewById<View>(R.id.note_card).visibility)
                assertEquals(View.GONE, root.findViewById<View>(R.id.friends_card).visibility)
                ViewChecks.assertTextFits(root)
                ViewChecks.assertTouchTargets(root)
            }
            Screenshots.capture("lesson-details-screenshots", "full") { settle() }

            scenario.recreate()
            settle()
            scenario.onActivity { activity ->
                assertEquals(lesson().subjectName, activity.sheet().requireView().text(R.id.subject_name))
            }
        }
    }

    @Test
    fun aLessonWithoutTeacherRoomOrLinkHidesTheirRowsAndActions() {
        withSchedule { scenario ->
            val minimal = LessonDetailsArgs(
                pairId = 7, date = "2026-09-07", subjectName = "", typeId = 5, format = "", start = "10:00", end = "11:30",
                teacherFio = null, teacherIsu = null, room = null, building = null, buildingId = null, mainBuildingId = null,
                note = null, zoomUrl = null, zoomPassword = null, zoomInfo = null
            )
            scenario.onActivity {
                LessonDetailsBottomSheet.newInstance(minimal).show(it.supportFragmentManager, LessonDetailsBottomSheet.TAG)
            }
            settle()
            scenario.onActivity { activity ->
                val root = (activity.supportFragmentManager.findFragmentByTag(LessonDetailsBottomSheet.TAG) as LessonDetailsBottomSheet).requireView()
                assertEquals(activity.getString(R.string.schedule_unknown_subject), root.text(R.id.subject_name))
                assertEquals(View.VISIBLE, root.findViewById<View>(R.id.time_fact).visibility)
                for (id in listOf(R.id.teacher_fact, R.id.location_fact, R.id.zoom_fact, R.id.actions, R.id.note_card, R.id.friends_card)) {
                    assertEquals(View.GONE, root.findViewById<View>(id).visibility)
                }
                ViewChecks.assertTextFits(root)
            }
            Screenshots.capture("lesson-details-screenshots", "minimal") { settle() }
        }
    }

    @Test
    fun detailsArgsCarryEveryFieldOfTheLesson() {
        val args = lesson().toDetailsArgs(LocalDate.of(2026, 9, 7))
        assertEquals(1L, args.pairId)
        assertEquals("2026-09-07", args.date)
        assertEquals("08:20", args.start)
        assertEquals("Кронверкский проспект, 49", args.building)
        assertEquals(13, args.mainBuildingId)
        assertEquals(null, args.zoomUrl)
    }

    private fun withSchedule(block: (ActivityScenario<ScheduleLifecycleTestActivity>) -> Unit) {
        val date = LocalDate.of(2026, 9, 7)
        ScheduleLifecycleTestActivity.days = MutableStateFlow(listOf(DaySchedule(date.dayOfWeek.value, 1, date, null, listOf(lesson()))))
        ScheduleLifecycleTestActivity.friendDays = MutableStateFlow(emptyList())
        ScheduleLifecycleTestActivity.refreshOutcome = { AppResult.Success(Unit) }
        ScheduleLifecycleTestActivity.clearOutcome = {}
        ScheduleLifecycleTestActivity.restrictToRequestedRange = false
        ScheduleLifecycleTestActivity.showPendingSport = MutableStateFlow(false)
        ScheduleLifecycleTestActivity.pendingSport = MutableStateFlow(DataState.Success(emptyList()))
        ScheduleLifecycleTestActivity.refreshPendingOutcome = {}
        ActivityScenario.launch(ScheduleLifecycleTestActivity::class.java).use { scenario ->
            TestUi.eventually { scenario.onActivity { assertNotNull(it.recycler().adapter?.itemCount?.takeIf { count -> count > 0 }) } }
            settle()
            block(scenario)
        }
    }

    private fun ScheduleLifecycleTestActivity.schedule() =
        supportFragmentManager.findFragmentByTag(ScheduleLifecycleTestActivity.SCHEDULE_TAG) as ScheduleFragment

    private fun ScheduleLifecycleTestActivity.recycler() = schedule().requireView().findViewById<RecyclerView>(R.id.outer_recycler_view)

    private fun ScheduleLifecycleTestActivity.sheet() =
        schedule().childFragmentManager.findFragmentByTag(LessonDetailsBottomSheet.TAG) as LessonDetailsBottomSheet

    private fun View.text(id: Int) = findViewById<TextView>(id).text.toString()

    private fun View.fact(id: Int) = findViewById<View>(id).findViewById<TextView>(R.id.fact_value).text.toString()

    private fun settle() = TestUi.settle(400)

    private fun lesson() = Lesson(
        pairId = 1, start = LocalTime.of(8, 20), end = LocalTime.of(9, 50), type = "Лекция", typeId = Lesson.TypeId(1),
        note = "Организационная информация о занятии", subjectName = "Математический анализ (продвинутый уровень)",
        subjectId = 1, groupName = "Тестовая группа", flowId = 1, flowTypeId = 2, teacherIsu = null,
        teacherFio = "Тестовый преподаватель с длинным именем", room = Room("1506"),
        building = Building("Кронверкский проспект, 49"), buildingId = 13, mainBuildingId = 13,
        format = "Очный", formatId = 1, zoomUrl = null, zoomPassword = null, zoomInfo = null
    )
}
