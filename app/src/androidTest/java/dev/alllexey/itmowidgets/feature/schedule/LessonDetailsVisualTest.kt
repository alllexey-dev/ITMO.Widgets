package dev.alllexey.itmowidgets.feature.schedule

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.EntryPointAccessors
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.SettingsNavigationTestActivity
import dev.alllexey.itmowidgets.core.debug.PreviewAppearance
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.core.notification.NotificationDebugEntryPoint
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.LessonSlot
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Room
import dev.alllexey.itmowidgets.feature.schedule.domain.model.toDetailsArgs
import dev.alllexey.itmowidgets.feature.schedule.presentation.details.LessonDetailsViewModel
import dev.alllexey.itmowidgets.feature.schedule.ui.ScheduleFragment
import dev.alllexey.itmowidgets.feature.schedule.ui.ScheduleLifecycleTestActivity
import dev.alllexey.itmowidgets.feature.schedule.ui.details.LessonDetailsBottomSheet
import dev.alllexey.itmowidgets.feature.schedule.ui.details.PendingSportDetailsBottomSheet
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.Appearances
import dev.alllexey.itmowidgets.testing.Appearances.assertEffective
import dev.alllexey.itmowidgets.testing.toScheduleLifecycle
import dev.alllexey.itmowidgets.testing.TestUi
import dev.alllexey.itmowidgets.testing.ViewChecks.descendants
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.atTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real card → sheet path on the lifecycle host and the pending-sport sheet. Both sheet bodies are Compose
 * (`LessonDetailsSheetTest`, `PendingSportDetailsSheetTest` and their goldens); here only the card marks, the opening,
 * recreation and the hosts' hand-offs remain until LS-6b.
 */
@RunWith(AndroidJUnit4::class)
class LessonDetailsVisualTest {
    private val servicesOptIn = EntryPointAccessors
        .fromApplication(ApplicationProvider.getApplicationContext(), NotificationDebugEntryPoint::class.java)
        .servicesOptIn()
    private var originalServices = false

    @Before
    fun servicesOff() {
        originalServices = runBlocking { servicesOptIn.getCustomServicesEnabled() }
        runBlocking { servicesOptIn.setCustomServicesEnabled(false) }
    }

    @After
    fun restore() {
        runBlocking { servicesOptIn.setCustomServicesEnabled(originalServices) }
        ScheduleLifecycleTestActivity.days = MutableStateFlow(emptyList())
        ScheduleLifecycleTestActivity.changes.value = emptyList()
        ScheduleLifecycleTestActivity.appearance = PreviewAppearance()
    }

    @Test
    fun aChangedLessonIsMarkedOnTheCardAndOpensItsSheet() {
        // The list marks the lesson from the same store the sheet's change block reads (LessonDetailsSheetTest).
        ScheduleLifecycleTestActivity.changes.value = listOf(change(ScheduleChangeKind.UPDATED, setOf(ScheduleChangeField.PLACE)))
        withSchedule { scenario ->
            scenario.onActivity { activity ->
                val card = activity.recycler().descendants().first { it.id == R.id.card_container && it.isShown }
                assertEquals(View.VISIBLE, card.findViewById<View>(R.id.change_indicator).visibility)
                card.performClick()
            }
            settle()
            scenario.onActivity { activity -> assertTrue(activity.sheet().isResumed) }
        }
    }

    @Test
    fun tappingACardOpensTheLessonAndTheSheetSurvivesRecreation() {
        withSchedule { scenario ->
            scenario.onActivity { activity ->
                val card = activity.recycler().descendants().first { it.id == R.id.card_container && it.isShown }
                assertTrue(card.isClickable)
                assertEquals(View.GONE, card.findViewById<View>(R.id.link_indicator).visibility)
                card.performClick()
            }
            settle()
            scenario.onActivity { activity ->
                val sheet = activity.sheet()
                assertTrue(sheet.isResumed)
                assertEquals(lesson().pairId, sheet.requireArguments().getLong(LessonDetailsViewModel.ARG_PAIR_ID))
            }
            Screenshots.capture("lesson-details-screenshots", "full") { settle() }

            scenario.recreate()
            settle()
            scenario.onActivity { activity ->
                assertTrue(activity.sheet().isResumed)
                assertEquals(lesson().pairId, activity.sheet().requireArguments().getLong(LessonDetailsViewModel.ARG_PAIR_ID))
            }
        }
    }

    @Test
    fun aLinkedLessonIsMarkedOnTheCardAndOpensItsSheet() {
        val date = LocalDate(2026, 9, 7)
        // An online lesson: no room, no building, MyITMO's "virtual rooms" id that the directory does not know.
        val linked = lesson().copy(pairId = 2, zoomUrl = "https://bbb.itmo.ru/b/abc", zoomPassword = "1234",
            room = null, building = null, buildingId = null, mainBuildingId = 319)
        withSchedule(lessons = listOf(linked)) { scenario ->
            scenario.onActivity { activity ->
                val card = activity.recycler().descendants().first { it.id == R.id.card_container && it.isShown }
                assertEquals(View.VISIBLE, card.findViewById<View>(R.id.link_indicator).visibility)
                card.performClick()
            }
            settle()
            scenario.onActivity { activity -> assertTrue(activity.sheet().isResumed) }
            Screenshots.capture("lesson-details-screenshots", "linked") { settle() }
        }
        assertEquals("2026-09-07", linked.toDetailsArgs(date).date)
    }

    /** The body's teacher row calls `onProfile` (LessonDetailsSheetTest); the host closes the sheet before navigating. */
    @Test
    fun teacherInLessonHeaderOpensTheirProfileAfterDismissingTheSheet() {
        val defaultPrimary = mutableMapOf<Boolean, Int>()
        Appearances.default.forEachIndexed { index, spec ->
            ScheduleLifecycleTestActivity.appearance = spec.toScheduleLifecycle()
            withSchedule(lessons = listOf(lesson().copy(teacherIsu = 300001, teacherFio = SettingsNavigationTestActivity.LONG_NAME))) { scenario ->
                scenario.onActivity { activity ->
                    spec.assertEffective(activity.findViewById(R.id.schedule_test_container), defaultPrimary)
                    activity.recycler().descendants().first { it.id == R.id.card_container && it.isShown }.performClick()
                }
                settle()
                scenario.onActivity { activity ->
                    if (spec.widthDp > 0) assertEquals((spec.widthDp * activity.resources.displayMetrics.density).toInt(),
                        activity.sheet().dialog!!.window!!.decorView.width)
                }
                frame(scenario, "teacher-$index")
                scenario.onActivity { it.sheet().actions.onProfile(300001) }
                settle()
                scenario.onActivity { activity ->
                    assertNull(activity.schedule().childFragmentManager.findFragmentByTag(LessonDetailsBottomSheet.TAG))
                    val navigation = activity.openedScreens.single()
                    assertEquals(AppScreen.USER_PROFILE, navigation.first)
                    assertEquals(300001, navigation.second?.getInt(UserScreenArgs.ISU))
                }
            }
        }
    }

    /**
     * The body's teacher row and `Открыть в спорте` call the host (PendingSportDetailsSheetTest); the host closes the
     * sheet before navigating, and the sheet survives recreation.
     */
    @Test
    fun aPendingSportRowOpensItsOwnSheetWithTheSportHandOff() {
        val booking = pendingBooking()
        val defaultPrimary = mutableMapOf<Boolean, Int>()
        Appearances.default.forEachIndexed { index, spec ->
            ScheduleLifecycleTestActivity.appearance = spec.toScheduleLifecycle()
            withSchedule { scenario ->
                scenario.onActivity {
                    spec.assertEffective(it.findViewById(R.id.schedule_test_container), defaultPrimary)
                    PendingSportDetailsBottomSheet.newInstance(booking, MOSCOW).show(it.supportFragmentManager, PendingSportDetailsBottomSheet.TAG)
                }
                settle()
                scenario.onActivity { activity ->
                    val sheet = activity.pendingSheet()!!
                    assertTrue(sheet.isResumed)
                    if (spec.widthDp > 0) assertEquals((spec.widthDp * activity.resources.displayMetrics.density).toInt(),
                        sheet.dialog!!.window!!.decorView.width)
                }
                frame(scenario, "pending-$index")
                scenario.onActivity { it.pendingSheet()!!.actions.onProfile(300002) }
                settle()
                scenario.onActivity { activity ->
                    assertNull(activity.pendingSheet())
                    val navigation = activity.openedScreens.single()
                    assertEquals(AppScreen.USER_PROFILE, navigation.first)
                    assertEquals(300002, navigation.second?.getInt(UserScreenArgs.ISU))
                }
            }
        }
    }

    @Test
    fun thePendingSheetSurvivesRecreationAndClosesForTheSportTab() {
        withSchedule { scenario ->
            scenario.onActivity {
                PendingSportDetailsBottomSheet.newInstance(pendingBooking(), MOSCOW).show(it.supportFragmentManager, PendingSportDetailsBottomSheet.TAG)
            }
            settle()
            scenario.recreate()
            settle()
            scenario.onActivity { activity ->
                val sheet = activity.pendingSheet()!!
                assertTrue(sheet.isResumed)
                sheet.actions.onOpenSport()
            }
            settle()
            scenario.onActivity { activity ->
                assertNull(activity.pendingSheet())
                assertTrue(activity.openedScreens.isEmpty())
            }
        }
    }

    @Test
    fun detailsArgsCarryEveryFieldOfTheLesson() {
        val args = lesson().toDetailsArgs(LocalDate(2026, 9, 7))
        assertEquals(1L, args.pairId)
        assertEquals("2026-09-07", args.date)
        assertEquals("08:20", args.start)
        assertEquals("Кронверкский проспект, 49", args.building)
        assertEquals(13, args.mainBuildingId)
        assertEquals(null, args.zoomUrl)
    }

    private fun withSchedule(
        lessons: List<Lesson> = listOf(lesson()),
        block: (ActivityScenario<ScheduleLifecycleTestActivity>) -> Unit
    ) {
        val date = LocalDate(2026, 9, 7)
        ScheduleLifecycleTestActivity.days = MutableStateFlow(listOf(DaySchedule(date.dayOfWeek.isoDayNumber, 1, date, null, lessons)))
        ScheduleLifecycleTestActivity.friendDays = MutableStateFlow(emptyList())
        ScheduleLifecycleTestActivity.refreshOutcome = { AppResult.Success(Unit) }
        ScheduleLifecycleTestActivity.clearOutcome = {}
        ScheduleLifecycleTestActivity.restrictToRequestedRange = false
        ScheduleLifecycleTestActivity.showPendingSport = MutableStateFlow(false)
        ScheduleLifecycleTestActivity.pendingSport = MutableStateFlow(AppResult.Success(emptyList()))
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

    private fun ScheduleLifecycleTestActivity.pendingSheet() =
        supportFragmentManager.findFragmentByTag(PendingSportDetailsBottomSheet.TAG) as PendingSportDetailsBottomSheet?

    private fun change(
        kind: ScheduleChangeKind,
        fields: Set<ScheduleChangeField>,
        before: LessonSlot? = slot(LocalTime(8, 20)),
        after: LessonSlot? = slot(LocalTime(8, 20), room = "2202")
    ) = ScheduleChange(
        id = "visual", detectedAt = Instant.parse("2026-09-07T06:00:00Z"), kind = kind, fields = fields,
        subjectName = lesson().subjectName, typeId = 1, flowName = "ФИЗ ПИИКТ 3.2", before = before, after = after,
        read = false, notified = true
    )

    private fun slot(
        start: LocalTime,
        room: String = "1506",
        building: String = "Кронверкский проспект, 49",
        formatId: Int = 1,
        format: String = "Очный"
    ) = LessonSlot(
        pairId = 1, date = LocalDate(2026, 9, 7), start = start,
        end = start.plusMinutes(90), room = room,
        building = building, formatId = formatId, format = format, teacherIsu = null,
        teacherName = "Тестовый преподаватель с длинным именем"
    )

    private fun frame(scenario: ActivityScenario<ScheduleLifecycleTestActivity>, name: String) {
        TestUi.settle(if (Screenshots.enabled) 500 else 80)
        lateinit var activity: ScheduleLifecycleTestActivity
        scenario.onActivity { activity = it }
        TestUi.awaitFrameCommit(activity)
        Screenshots.capture("lesson-details-screenshots", name)
    }

    private fun settle() = TestUi.settle(400)

    private fun pendingBooking(): PendingSportBooking {
        val start = LocalDate(2026, 9, 7).atTime(16, 0).toInstant(UtcOffset(hours = 3))
        return PendingSportBooking(
            queueId = 1, queueKind = PendingSportBooking.QueueKind.AUTO, lessonId = 100,
            sectionName = "Современные танцы", start = start,
            end = start + 90.minutes,
            teacherFio = SettingsNavigationTestActivity.LONG_NAME, roomName = "Кронверкский проспект, 49, зал 1",
            isPrediction = true, teacherIsu = 300002
        )
    }

    private fun lesson() = Lesson(
        pairId = 1, start = LocalTime(8, 20), end = LocalTime(9, 50), type = "Лекция", typeId = Lesson.TypeId(1),
        note = "Организационная информация о занятии", subjectName = "Математический анализ (продвинутый уровень)",
        subjectId = 1, groupName = "ФИЗ ПИИКТ 3.2", flowId = 1, flowTypeId = 2, teacherIsu = null,
        teacherFio = "Тестовый преподаватель с длинным именем", room = Room("1506"),
        building = Building("Кронверкский проспект, 49"), buildingId = 13, mainBuildingId = 13,
        format = "Очный", formatId = 1, zoomUrl = null, zoomPassword = null, zoomInfo = null
    )
}

private val MOSCOW = TimeZone.of("Europe/Moscow")
