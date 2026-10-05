package dev.alllexey.itmowidgets.feature.schedule

import android.view.View
import android.view.ViewGroup
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import android.widget.ImageView
import android.widget.TextView
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
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.notification.NotificationDebugEntryPoint
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.schedule.LessonSlot
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import dev.alllexey.itmowidgets.core.ui.TeacherLevelTone
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Room
import dev.alllexey.itmowidgets.feature.schedule.domain.model.toDetailsArgs
import dev.alllexey.itmowidgets.feature.schedule.ui.ScheduleFragment
import dev.alllexey.itmowidgets.feature.schedule.ui.ScheduleLifecycleTestActivity
import dev.alllexey.itmowidgets.feature.schedule.ui.details.LessonDetailsBottomSheet
import dev.alllexey.itmowidgets.feature.schedule.ui.details.PendingSportDetailsBottomSheet
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.Appearances
import dev.alllexey.itmowidgets.testing.Appearances.assertEffective
import dev.alllexey.itmowidgets.testing.toScheduleLifecycle
import dev.alllexey.itmowidgets.testing.TestUi
import dev.alllexey.itmowidgets.testing.ViewChecks
import dev.alllexey.itmowidgets.testing.ViewChecks.descendants
import kotlin.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.time.toKotlinInstant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The real card → sheet path on the lifecycle host; the opt-in is off, so the friends block stays hidden. */
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
    fun changeBlockShowsWasAndNowWithoutMovingTheHeader() {
        Appearances.default.forEach { spec ->
            ScheduleLifecycleTestActivity.appearance = spec.toScheduleLifecycle()
            // The list marks the lesson from the same store the block reads.
            ScheduleLifecycleTestActivity.changes.value = listOf(change(ScheduleChangeKind.UPDATED, setOf(ScheduleChangeField.PLACE)))
            withSchedule { scenario ->
                scenario.onActivity { activity ->
                    val card = activity.recycler().descendants().first { it.id == R.id.card_container && it.isShown }
                    assertEquals(View.VISIBLE, card.findViewById<View>(R.id.change_indicator).visibility)
                    card.performClick()
                }
                settle()
                var anchors: Pair<Int, Int>? = null
                scenario.onActivity { activity ->
                    val root = activity.sheet().requireView()
                    anchors = root.anchors()
                    activity.sheet().showChange(null)
                    assertEquals(View.GONE, root.findViewById<View>(R.id.changes_card).visibility)
                    activity.sheet().showChange(change(ScheduleChangeKind.UPDATED, setOf(ScheduleChangeField.TIME, ScheduleChangeField.PLACE),
                        after = slot(LocalTime.of(10, 0), room = "2202", building = "ул. Ломоносова, 9")))
                }
                settle()
                scenario.onActivity { activity ->
                    val root = activity.sheet().requireView()
                    assertEquals(View.VISIBLE, root.findViewById<View>(R.id.changes_card).visibility)
                    assertEquals("Изменения", root.text(R.id.changes_title))
                    assertEquals(listOf("Время: 08:20–09:50 → 10:00–11:30", "Аудитория: 1506 · Кронва → 2202 · Ломо"), root.changeLines())
                    assertEquals(anchors, root.anchors())
                    val header = root.findViewById<View>(R.id.header)
                    val block = root.findViewById<View>(R.id.changes_card)
                    assertTrue(header.bottom <= block.top)
                    ViewChecks.assertTextFits(root)
                    ViewChecks.assertTouchTargets(root)
                }
                frame(scenario, "change-${spec.name}")
                scenario.onActivity { activity ->
                    val root = activity.sheet().requireView()
                    // One changed field: only its "было → стало" line, no second "Формат: Дистанционный".
                    activity.sheet().showChange(change(ScheduleChangeKind.UPDATED, setOf(ScheduleChangeField.FORMAT),
                        after = slot(LocalTime.of(8, 20), formatId = 3, format = "Дистанционный")))
                    assertEquals(listOf("Формат: Очный → Дистанционный"), root.changeLines())
                    activity.sheet().showChange(change(ScheduleChangeKind.ADDED, emptySet(), before = null))
                    assertEquals(listOf("Добавлена: пн, 7 сентября, 08:20"), root.changeLines())
                    activity.sheet().showChange(null)
                    assertEquals(View.GONE, root.findViewById<View>(R.id.changes_card).visibility)
                }
                settle()
                scenario.onActivity { activity -> assertEquals(anchors, activity.sheet().requireView().anchors()) }
            }
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
                val root = sheet.requireView()
                assertEquals(lesson().subjectName, root.text(R.id.section_name))
                assertEquals("Лекция · Очный", root.text(R.id.kind))
                assertEquals("08:20–09:50", root.text(R.id.time))
                assertEquals("Понедельник, 7 сентября 2026", root.text(R.id.date))
                assertEquals("90 мин", root.text(R.id.duration))
                assertEquals(lesson().teacherFio, root.fact(R.id.teacher_fact))
                assertNoTeacherAction(root.findViewById(R.id.teacher_fact))
                assertFlowRow(root, "ФИЗ ПИИКТ 3.2")
                assertEquals("1506 · Кронверкский проспект, 49", root.fact(R.id.location_fact))
                assertEquals(View.VISIBLE, root.findViewById<View>(R.id.map_button).visibility)
                assertEquals(View.GONE, root.findViewById<View>(R.id.link_button).visibility)
                assertEquals(View.VISIBLE, root.findViewById<View>(R.id.note_card).visibility)
                assertEquals(View.GONE, root.findViewById<View>(R.id.friends_card).visibility)
                ViewChecks.assertTextFits(root)
                ViewChecks.assertTouchTargets(root)
            }
            Screenshots.capture("lesson-details-screenshots", "full") { settle() }

            scenario.recreate()
            settle()
            scenario.onActivity { activity ->
                assertEquals(lesson().subjectName, activity.sheet().requireView().text(R.id.section_name))
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
                assertEquals(activity.getString(R.string.schedule_unknown_subject), root.text(R.id.section_name))
                assertEquals(View.VISIBLE, root.findViewById<View>(R.id.time_card).visibility)
                for (id in listOf(R.id.place_card, R.id.flow_fact, R.id.link_fact, R.id.actions, R.id.note_card, R.id.friends_card)) {
                    assertEquals(View.GONE, root.findViewById<View>(id).visibility)
                }
                ViewChecks.assertTextFits(root)
            }
            Screenshots.capture("lesson-details-screenshots", "minimal") { settle() }
        }
    }

    @Test
    fun longFlowNameWrapsInEveryAppearance() {
        val longFlow = "Тестовый поток с очень длинным названием, которое не помещается в одну строку экрана 12345"
        assertEquals(90, longFlow.length)
        val defaultPrimary = mutableMapOf<Boolean, Int>()
        Appearances.default.forEach { spec ->
            ScheduleLifecycleTestActivity.appearance = spec.toScheduleLifecycle()
            withSchedule(lessons = listOf(lesson().copy(groupName = longFlow))) { scenario ->
                scenario.onActivity { activity ->
                    spec.assertEffective(activity.findViewById(R.id.schedule_test_container), defaultPrimary)
                    activity.recycler().descendants().first { it.id == R.id.card_container && it.isShown }.performClick()
                }
                settle()
                scenario.onActivity { activity ->
                    val root = activity.sheet().requireView()
                    spec.assertEffective(root, defaultPrimary)
                    assertFlowRow(root, longFlow)
                    assertTrue(root.findViewById<View>(R.id.flow_fact).findViewById<TextView>(R.id.fact_value).lineCount > 1)
                    ViewChecks.assertTextFits(root)
                    ViewChecks.assertTouchTargets(root)
                }
                frame(scenario, "flow-${spec.name}")
            }
        }
    }

    @Test
    fun sportSheetsHaveNoFlowRow() {
        withSchedule { scenario ->
            scenario.onActivity {
                PendingSportDetailsBottomSheet.newInstance(pendingBooking(), MOSCOW).show(it.supportFragmentManager, PendingSportDetailsBottomSheet.TAG)
            }
            settle()
            scenario.onActivity { activity ->
                val sheet = activity.supportFragmentManager.findFragmentByTag(PendingSportDetailsBottomSheet.TAG) as PendingSportDetailsBottomSheet
                val root = sheet.requireView()
                assertEquals(View.VISIBLE, root.findViewById<View>(R.id.teacher_fact).visibility)
                assertEquals(View.GONE, root.findViewById<View>(R.id.flow_fact).visibility)
            }
        }
    }

    @Test
    fun aLinkedLessonShowsTheHostAndTheCardMarksIt() {
        val date = LocalDate.of(2026, 9, 7)
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
            scenario.onActivity { activity ->
                val root = activity.sheet().requireView()
                assertEquals("Пароль: 1234", root.fact(R.id.link_fact))
                assertEquals(View.VISIBLE, root.findViewById<View>(R.id.link_button).visibility)
                assertEquals(View.GONE, root.findViewById<View>(R.id.map_button).visibility)
                assertEquals(View.GONE, root.findViewById<View>(R.id.location_fact).visibility)
                ViewChecks.assertTextFits(root)
                ViewChecks.assertTouchTargets(root)
            }
            Screenshots.capture("lesson-details-screenshots", "linked") { settle() }
        }
        assertEquals("2026-09-07", linked.toDetailsArgs(date).date)
    }

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
                    val root = activity.sheet().requireView()
                    spec.assertEffective(root, defaultPrimary)
                    assertEquals(SettingsNavigationTestActivity.LONG_NAME, root.fact(R.id.teacher_fact))
                    assertTeacherAction(root.findViewById(R.id.teacher_fact))
                    if (spec.widthDp > 0) assertEquals((spec.widthDp * root.resources.displayMetrics.density).toInt(),
                        activity.sheet().dialog!!.window!!.decorView.width)
                    ViewChecks.assertTextFits(root)
                    ViewChecks.assertTouchTargets(root)
                }
                frame(scenario, "teacher-$index")
                scenario.onActivity { it.sheet().requireView().findViewById<View>(R.id.teacher_fact).performClick() }
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

    @Test
    fun teacherLevelDotAppearsWithoutMovingTheRow() {
        val defaultPrimary = mutableMapOf<Boolean, Int>()
        val specs = if (Appearances.fullMatrix) Appearances.all else Appearances.all.take(2)
        specs.forEachIndexed { index, spec ->
            ScheduleLifecycleTestActivity.appearance = spec.toScheduleLifecycle()
            withSchedule(lessons = listOf(lesson().copy(teacherIsu = 300001, teacherFio = SettingsNavigationTestActivity.LONG_NAME))) { scenario ->
                scenario.onActivity { activity ->
                    spec.assertEffective(activity.findViewById(R.id.schedule_test_container), defaultPrimary)
                    activity.recycler().descendants().first { it.id == R.id.card_container && it.isShown }.performClick()
                }
                settle()
                var geometry: Triple<Int, Int, Int>? = null
                scenario.onActivity { activity ->
                    val row = activity.sheet().requireView().findViewById<View>(R.id.teacher_fact)
                    val mark = row.findViewById<View>(R.id.fact_mark)
                    // The opt-in is off, so no level arrives; the place is still kept for a teacher with an ISU.
                    assertEquals(View.INVISIBLE, mark.visibility)
                    assertTrue(mark.width > 0)
                    geometry = row.geometry()
                    activity.sheet().showTeacherLevel(TeacherLevel.POSITIVE)
                }
                settle()
                scenario.onActivity { activity ->
                    val root = activity.sheet().requireView()
                    val row = root.findViewById<View>(R.id.teacher_fact)
                    val mark = row.findViewById<ImageView>(R.id.fact_mark)
                    assertEquals(View.VISIBLE, mark.visibility)
                    assertEquals(TeacherLevelTone.POSITIVE.color(activity), mark.imageTintList?.defaultColor)
                    assertEquals(geometry, row.geometry())
                    // The dot sits between the name and the chevron.
                    val value = row.findViewById<View>(R.id.fact_value)
                    val trailing = row.findViewById<View>(R.id.fact_trailing)
                    assertTrue(value.right <= mark.left && mark.right <= trailing.left)
                    assertEquals("Преподаватель: ${SettingsNavigationTestActivity.LONG_NAME}, тон отзывов: скорее положительные",
                        row.findViewById<View>(R.id.fact_value).contentDescription)
                    assertTeacherAction(row)
                    ViewChecks.assertTextFits(root)
                    ViewChecks.assertTouchTargets(root)
                }
                frame(scenario, "teacher-level-$index")
                scenario.onActivity { activity ->
                    activity.sheet().showTeacherLevel(null)
                    val row = activity.sheet().requireView().findViewById<View>(R.id.teacher_fact)
                    assertEquals(View.INVISIBLE, row.findViewById<View>(R.id.fact_mark).visibility)
                    assertEquals("Преподаватель: ${SettingsNavigationTestActivity.LONG_NAME}",
                        row.findViewById<View>(R.id.fact_value).contentDescription)
                }
            }
        }
        ScheduleLifecycleTestActivity.appearance = PreviewAppearance()
        withSchedule { scenario ->
            scenario.onActivity { it.recycler().descendants().first { view -> view.id == R.id.card_container && view.isShown }.performClick() }
            settle()
            scenario.onActivity { activity ->
                activity.sheet().showTeacherLevel(TeacherLevel.POSITIVE)
                activity.sheet().showTeacherLevel(null)
                assertEquals(View.GONE, activity.sheet().requireView().findViewById<View>(R.id.teacher_fact)
                    .findViewById<View>(R.id.fact_mark).visibility)
            }
        }
    }

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
                    val sheet = activity.supportFragmentManager.findFragmentByTag(PendingSportDetailsBottomSheet.TAG) as PendingSportDetailsBottomSheet
                    val root = sheet.requireView()
                    spec.assertEffective(root, defaultPrimary)
                    assertEquals("Современные танцы", root.text(R.id.section_name))
                    assertEquals(activity.getString(R.string.schedule_auto_sign_prediction), root.text(R.id.kind))
                    assertEquals(activity.getString(R.string.sport_prediction_waiting), root.text(R.id.condition_title))
                    assertEquals(activity.getString(R.string.sport_prediction_hint), root.text(R.id.condition_body))
                    assertEquals("16:00–17:30", root.text(R.id.time))
                    assertEquals("Кронверкский проспект, 49, зал 1", root.fact(R.id.location_fact))
                    assertEquals(SettingsNavigationTestActivity.LONG_NAME, root.fact(R.id.teacher_fact))
                    assertTeacherAction(root.findViewById(R.id.teacher_fact))
                    assertEquals(View.VISIBLE, root.findViewById<View>(R.id.map_button).visibility)
                    assertEquals(View.VISIBLE, root.findViewById<View>(R.id.open_sport).visibility)
                    if (spec.widthDp > 0) assertEquals((spec.widthDp * root.resources.displayMetrics.density).toInt(), sheet.dialog!!.window!!.decorView.width)
                    ViewChecks.assertTextFits(root)
                    ViewChecks.assertTouchTargets(root)
                }
                frame(scenario, "pending-$index")
                scenario.onActivity { activity ->
                    val sheet = activity.supportFragmentManager.findFragmentByTag(PendingSportDetailsBottomSheet.TAG) as PendingSportDetailsBottomSheet
                    sheet.requireView().findViewById<View>(R.id.teacher_fact).performClick()
                }
                settle()
                scenario.onActivity { activity ->
                    assertNull(activity.supportFragmentManager.findFragmentByTag(PendingSportDetailsBottomSheet.TAG))
                    val navigation = activity.openedScreens.single()
                    assertEquals(AppScreen.USER_PROFILE, navigation.first)
                    assertEquals(300002, navigation.second?.getInt(UserScreenArgs.ISU))
                }
            }
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

    private fun withSchedule(
        lessons: List<Lesson> = listOf(lesson()),
        block: (ActivityScenario<ScheduleLifecycleTestActivity>) -> Unit
    ) {
        val date = LocalDate.of(2026, 9, 7)
        ScheduleLifecycleTestActivity.days = MutableStateFlow(listOf(DaySchedule(date.dayOfWeek.value, 1, date, null, lessons)))
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

    private fun View.text(id: Int) = findViewById<TextView>(id).text.toString()

    /** On-screen tops of the header and the teacher row: what the change block must not move. */
    private fun View.anchors(): Pair<Int, Int> =
        IntArray(2).also(findViewById<View>(R.id.header)::getLocationOnScreen)[1] to
            IntArray(2).also(findViewById<View>(R.id.teacher_fact)::getLocationOnScreen)[1]

    private fun View.changeLines(): List<String> = findViewById<ViewGroup>(R.id.changes_lines).let { lines ->
        (0 until lines.childCount).map { (lines.getChildAt(it) as TextView).text.toString() }
    }

    private fun change(
        kind: ScheduleChangeKind,
        fields: Set<ScheduleChangeField>,
        before: LessonSlot? = slot(LocalTime.of(8, 20)),
        after: LessonSlot? = slot(LocalTime.of(8, 20), room = "2202")
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
        pairId = 1, date = LocalDate.of(2026, 9, 7).toKotlinLocalDate(), start = start.toKotlinLocalTime(),
        end = start.plusMinutes(90).toKotlinLocalTime(), room = room,
        building = building, formatId = formatId, format = format, teacherIsu = null,
        teacherName = "Тестовый преподаватель с длинным именем"
    )

    /** Height, top on screen and width of the name: what a late dot must not change. */
    private fun View.geometry(): Triple<Int, Int, Int> =
        Triple(height, IntArray(2).also(::getLocationOnScreen)[1], findViewById<View>(R.id.fact_value).width)

    private fun View.fact(id: Int) = findViewById<View>(id).findViewById<TextView>(R.id.fact_value).text.toString()

    private fun assertTeacherAction(row: View) {
        assertTrue(row.isClickable)
        assertTrue(row.isFocusable)
        assertTrue(row.height >= 48 * row.resources.displayMetrics.density - 1)
        assertEquals(View.VISIBLE, row.findViewById<View>(R.id.fact_trailing).visibility)
        assertEquals(row.context.getString(R.string.teacher_open_profile),
            row.createAccessibilityNodeInfo().actionList.single { it.id == AccessibilityActionCompat.ACTION_CLICK.id }.label)
    }

    /** The flow row reads like the other facts, sits between the teacher and the place and does nothing on tap. */
    private fun assertFlowRow(root: View, flow: String) {
        val row = root.findViewById<View>(R.id.flow_fact)
        assertEquals(View.VISIBLE, row.visibility)
        assertEquals(flow, root.fact(R.id.flow_fact))
        assertEquals("Поток: $flow", row.findViewById<View>(R.id.fact_value).contentDescription)
        assertEquals(View.VISIBLE, row.findViewById<View>(R.id.fact_icon).visibility)
        val teacher = root.findViewById<View>(R.id.teacher_fact)
        val location = root.findViewById<View>(R.id.location_fact)
        assertTrue(teacher.bottom <= row.top && row.bottom <= location.top)
        assertNoTeacherAction(row)
    }

    private fun assertNoTeacherAction(row: View) {
        assertFalse(row.isClickable)
        assertFalse(row.isFocusable)
        assertEquals(View.GONE, row.findViewById<View>(R.id.fact_trailing).visibility)
        assertTrue(row.createAccessibilityNodeInfo().actionList.none { it.id == AccessibilityActionCompat.ACTION_CLICK.id })
    }

    private fun frame(scenario: ActivityScenario<ScheduleLifecycleTestActivity>, name: String) {
        TestUi.settle(if (Screenshots.enabled) 500 else 80)
        lateinit var activity: ScheduleLifecycleTestActivity
        scenario.onActivity { activity = it }
        TestUi.awaitFrameCommit(activity)
        Screenshots.capture("lesson-details-screenshots", name)
    }

    private fun settle() = TestUi.settle(400)

    private fun pendingBooking(): PendingSportBooking {
        val start = LocalDate.of(2026, 9, 7).atTime(16, 0).atOffset(java.time.ZoneOffset.ofHours(3))
        return PendingSportBooking(
            queueId = 1, queueKind = PendingSportBooking.QueueKind.AUTO, lessonId = 100,
            sectionName = "Современные танцы", start = start.toInstant().toKotlinInstant(),
            end = start.plusMinutes(90).toInstant().toKotlinInstant(),
            teacherFio = SettingsNavigationTestActivity.LONG_NAME, roomName = "Кронверкский проспект, 49, зал 1",
            isPrediction = true, teacherIsu = 300002
        )
    }

    private fun lesson() = Lesson(
        pairId = 1, start = LocalTime.of(8, 20), end = LocalTime.of(9, 50), type = "Лекция", typeId = Lesson.TypeId(1),
        note = "Организационная информация о занятии", subjectName = "Математический анализ (продвинутый уровень)",
        subjectId = 1, groupName = "ФИЗ ПИИКТ 3.2", flowId = 1, flowTypeId = 2, teacherIsu = null,
        teacherFio = "Тестовый преподаватель с длинным именем", room = Room("1506"),
        building = Building("Кронверкский проспект, 49"), buildingId = 13, mainBuildingId = 13,
        format = "Очный", formatId = 1, zoomUrl = null, zoomPassword = null, zoomInfo = null
    )
}

private val MOSCOW = TimeZone.of("Europe/Moscow")
