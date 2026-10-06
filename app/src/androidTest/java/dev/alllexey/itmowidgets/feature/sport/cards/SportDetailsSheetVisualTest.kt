package dev.alllexey.itmowidgets.feature.sport.cards

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.DialogFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.SettingsNavigationTestActivity
import dev.alllexey.itmowidgets.core.debug.PreviewAppearance
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.ui.ConditionTone
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.feature.sport.domain.model.FriendSportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportCommon
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntryStatus
import dev.alllexey.itmowidgets.feature.sport.domain.model.UnavailableReason
import dev.alllexey.itmowidgets.feature.sport.ui.SportCardsPreviewActivity
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportCommonDetailsBottomSheet
import dev.alllexey.itmowidgets.testing.Appearances
import dev.alllexey.itmowidgets.testing.Appearances.assertEffective
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestUi
import dev.alllexey.itmowidgets.testing.ViewChecks.assertTextFits
import dev.alllexey.itmowidgets.testing.ViewChecks.assertTouchTargets
import dev.alllexey.itmowidgets.testing.ViewChecks.descendants
import dev.alllexey.itmowidgets.testing.toSportCards
import kotlin.time.Duration.Companion.days
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** The sport details sheet on its own, over the debug host; LP-3 deletes this file with the View sheet. */
@RunWith(AndroidJUnit4::class)
class SportDetailsSheetVisualTest {
    @Test fun teacherInSportDetailsOpensProfileAfterClosingTheSheet() {
        val defaultPrimary = mutableMapOf<Boolean, Int>()
        Appearances.default.forEachIndexed { index, spec ->
            preview(spec.toSportCards()) { scenario ->
                scenario.onActivity { activity ->
                    spec.assertEffective(activity.list, defaultPrimary)
                    activity.showDetails(SportCardFixtures.lesson().copy(teacherIsu = 100, teacherFio = SettingsNavigationTestActivity.LONG_NAME))
                }
                settle()
                scenario.onActivity { activity ->
                    val details = sheet(activity)
                    val root = details.requireView()
                    spec.assertEffective(root, defaultPrimary)
                    val teacher = root.findViewById<View>(R.id.teacher_fact)
                    assertEquals(SettingsNavigationTestActivity.LONG_NAME, teacher.findViewById<TextView>(R.id.fact_value).text.toString())
                    assertTrue(teacher.isClickable)
                    assertTrue(teacher.isFocusable)
                    assertEquals(View.VISIBLE, teacher.findViewById<View>(R.id.fact_trailing).visibility)
                    assertEquals(activity.getString(R.string.teacher_open_profile),
                        teacher.createAccessibilityNodeInfo().actionList.single { it.id == AccessibilityActionCompat.ACTION_CLICK.id }.label)
                    if (spec.widthDp > 0) assertEquals((spec.widthDp * root.resources.displayMetrics.density).toInt(), details.dialog!!.window!!.decorView.width)
                    assertTextFits(root)
                    assertTouchTargets(root)
                }
                TestUi.settle(if (Screenshots.enabled) 500 else 80)
                lateinit var activity: SportCardsPreviewActivity
                scenario.onActivity { activity = it }
                TestUi.awaitFrameCommit(activity)
                screenshot("teacher-$index")
                scenario.onActivity { sheet(it).requireView().findViewById<View>(R.id.teacher_fact).performClick() }
                settle()
                scenario.onActivity {
                    assertNull(it.supportFragmentManager.findFragmentByTag(SportCommonDetailsBottomSheet.TAG))
                    val navigation = it.openedScreens.single()
                    assertEquals(AppScreen.USER_PROFILE, navigation.first)
                    assertEquals(100, navigation.second?.getInt(UserScreenArgs.ISU))
                }
            }
        }
    }

    @Test fun queueAndLessonDetailsInLightDarkAndNarrowDynamicPalettes() {
        Appearances.default.forEachIndexed { index, spec ->
            preview(spec.toSportCards()) { scenario ->
                val queue = SportCardFixtures.booking(2).copy(signed = false,
                    sectionName = SectionName("""Современные танцы (Клуб парных танцев "Потанцуем")"""),
                    signEntry = SportCardFixtures.entry(SportQueueEntryStatus.NOTIFIED), friendsBookings = friends())
                scenario.onActivity {
                    ConditionTone.entries.forEach { tone ->
                        assertTrue("Contrast $tone", ColorUtils.calculateContrast(tone.accent(it), tone.container(it)) >= 4.5)
                    }
                    it.showDetails(queue)
                }
                settle()
                scenario.onActivity { activity ->
                    val root = sheet(activity).requireView()
                    assertTextFits(root)
                    assertTouchTargets(sheet(activity).dialog!!.window!!.decorView)
                    assertEquals(View.GONE, root.findViewById<View>(R.id.capacity_group).visibility)
                    assertEquals("2 / 5", root.findViewById<TextView>(R.id.attempts).text.toString())
                    assertEquals(queue.sectionName.raw, root.findViewById<TextView>(R.id.section_name).text.toString())
                }
                screenshot("queue-details-$index")
                scenario.onActivity { sheet(it).requireView().findViewById<NestedScrollView>(R.id.details_scroll).fullScroll(View.FOCUS_DOWN) }
                settle()
                screenshot("queue-history-$index")
                scenario.onActivity { assertTextFits(sheet(it).requireView()); sheet(it).dismiss() }
                settle()

                val lesson = SportCardFixtures.lesson().copy(friendsBookings = friends(), intersection = true,
                    unavailableReasons = listOf(UnavailableReason.TimeConflict))
                scenario.onActivity { it.showDetails(lesson) }
                settle()
                scenario.onActivity {
                    assertTextFits(sheet(it).requireView())
                    assertEquals(View.VISIBLE, sheet(it).requireView().findViewById<View>(R.id.capacity_group).visibility)
                    assertEquals(13, sheet(it).requireView().findViewById<CircularProgressIndicator>(R.id.capacity_progress).progress)
                    assertEquals("90 мин", sheet(it).requireView().findViewById<TextView>(R.id.duration).text.toString())
                }
                screenshot("lesson-details-$index")
                scenario.onActivity { sheet(it).requireView().findViewById<NestedScrollView>(R.id.details_scroll).fullScroll(View.FOCUS_DOWN) }
                settle()
                screenshot("lesson-notes-$index")
                scenario.onActivity { assertTextFits(sheet(it).requireView()) }
                scenario.recreate()
                settle()
                scenario.onActivity { assertEquals(lesson.sectionName.raw, sheet(it).requireView().findViewById<TextView>(R.id.section_name).text.toString()) }
            }
        }
    }

    @Test fun freePlaceLabelFollowsRussianPluralForms() {
        preview(PreviewAppearance()) { scenario ->
            listOf(1 to "Свободное место", 2 to "Свободных места", 4 to "Свободных места",
                5 to "Свободных мест", 11 to "Свободных мест", 21 to "Свободное место",
                0 to "Свободных мест").forEach { (available, expected) ->
                scenario.onActivity { it.showDetails(SportCardFixtures.lesson().copy(available = available, limit = 40)) }
                settle()
                scenario.onActivity {
                    val root = sheet(it).requireView()
                    assertEquals(expected, root.findViewById<TextView>(R.id.capacity_label).text.toString())
                    assertEquals(if (available == 0) "нет" else available.toString(),
                        root.findViewById<TextView>(R.id.capacity).text.toString())
                    assertTextFits(root)
                    sheet(it).dismiss()
                }
                settle()
            }
        }
    }

    @Test fun detailsActionsAreAdditiveDispatchOnceAndSurviveRecreation() {
        val lesson = SportCardFixtures.lesson()
        val cases: List<Triple<SportCommon, Int, String>> = listOf(
            Triple(lesson, R.string.sport_lesson_sign_up, "sign"),
            Triple(lesson.copy(signed = true), R.string.sport_lesson_sign_out, "unsign"),
            Triple(lesson.copy(available = 0, canSignIn = false, unavailableReasons = listOf(UnavailableReason.Full)), R.string.sport_auto_sign_title, "auto"),
            Triple(lesson.copy(available = 0, canSignIn = false, signEntry = SportCardFixtures.entry()), R.string.sport_card_cancel_auto, "unauto"),
            Triple(SportCardFixtures.booking(), R.string.sport_lesson_sign_out, "unsign"),
            Triple(SportCardFixtures.booking().copy(signed = false, signEntry = SportCardFixtures.entry()), R.string.sport_card_cancel_auto, "unauto")
        )
        preview(PreviewAppearance(widthDp = 320, fontScale = 1.3f)) { scenario ->
            cases.forEach { (item, label, expected) ->
                scenario.onActivity { it.showDetails(item) }
                settle()
                scenario.onActivity { activity ->
                    val button = sheet(activity).requireView().findViewById<MaterialButton>(R.id.booking_action)
                    assertTrue(button.isShown)
                    assertEquals(activity.getString(label), button.text.toString())
                    assertTrue(button.height >= 48 * activity.resources.displayMetrics.density - 1)
                    val before = activity.actionCount
                    button.performClick()
                    button.performClick()
                    assertEquals(before + 1, activity.actionCount)
                    assertEquals(expected, activity.lastAction)
                }
                settle()
            }
            scenario.onActivity { it.showDetails(lesson) }
            settle()
            scenario.recreate()
            settle()
            scenario.onActivity {
                sheet(it).requireView().findViewById<View>(R.id.booking_action).performClick()
                assertEquals("sign", it.lastAction)
                assertEquals(1, it.actionCount)
            }
        }
    }

    @Test fun detailsRespectReadOnlyBusyAndChangedDeadline() {
        preview(PreviewAppearance(dark = true)) { scenario ->
            val lesson = SportCardFixtures.lesson()
            for ((enabled, busy) in listOf(false to false, true to true)) {
                scenario.onActivity {
                    SportCommonDetailsBottomSheet.newInstance(lesson, actionsEnabled = enabled, busy = busy)
                        .show(it.supportFragmentManager, SportCommonDetailsBottomSheet.TAG)
                }
                settle()
                scenario.onActivity {
                    val button = sheet(it).requireView().findViewById<View>(R.id.booking_action)
                    assertEquals(if (enabled) View.VISIBLE else View.GONE, button.visibility)
                    if (busy) { assertFalse(button.isEnabled); button.performClick() }
                    assertEquals(0, it.actionCount)
                    sheet(it).dismiss()
                }
                settle()
            }
            scenario.onActivity { it.showDetails(lesson) }
            settle()
            scenario.onActivity {
                val details = sheet(it) as SportCommonDetailsBottomSheet
                details.timeProvider = object : AcademicTimeProvider {
                    override val timeZone = TimeZone.of("Europe/Moscow")
                    override fun today() = lesson.start.toLocalDateTime(timeZone).date
                    override fun now() = lesson.start
                }
                details.requireView().findViewById<View>(R.id.booking_action).performClick()
                assertEquals(View.GONE, details.requireView().findViewById<View>(R.id.booking_action).visibility)
                assertEquals(0, it.actionCount)
            }
        }
    }

    @Test fun predictionDetailsDoNotInventPlaces() {
        preview(PreviewAppearance(dark = true, widthDp = 320, fontScale = 1.3f)) { scenario ->
            val predicted = SportCardFixtures.lesson().copy(isLessonReal = false)
            scenario.onActivity { it.showDetails(predicted) }
            settle()
            scenario.onActivity {
                val root = sheet(it).requireView()
                assertEquals(View.GONE, root.findViewById<View>(R.id.capacity_group).visibility)
                assertEquals(View.VISIBLE, root.findViewById<View>(R.id.attention_card).visibility)
                assertTextFits(root)
            }
            screenshot("prediction-details")
        }
    }

    @Test fun shareActionForUpcomingLessonsBookingsAndPredictions() {
        preview(PreviewAppearance()) { scenario ->
            val lesson = SportCardFixtures.lesson().copy(sectionName = SectionName(LONG_SECTION))
            val past = lesson.copy(start = lesson.start - 2.days, end = lesson.end - 2.days)
            val cases = listOf<Pair<SportCommon, Boolean>>(
                lesson to true,
                SportCardFixtures.booking() to true,
                lesson.copy(isLessonReal = false) to true,
                SportCardFixtures.booking(-1).copy(isLessonReal = false, signed = false) to true,
                past to false,
                lesson.copy(lessonId = -5) to false
            )
            cases.forEachIndexed { index, (item, shared) ->
                scenario.onActivity { it.showDetails(item) }
                settle()
                scenario.onActivity {
                    val details = sheet(it)
                    val toolbar = details.requireView().findViewById<MaterialToolbar>(R.id.toolbar)
                    val share = toolbar.menu.findItem(R.id.action_share)
                    assertEquals("case $index", shared, share?.isVisible == true)
                    if (shared) {
                        assertEquals(it.getString(R.string.share_action), share!!.title.toString())
                        val title = toolbar.descendants().filterIsInstance<TextView>()
                            .single { view -> view.text == it.getString(R.string.sport_details_title) }
                        assertTrue("The title keeps its full width beside the action", title.width >= title.paint.measureText(title.text.toString()))
                    }
                    assertTouchTargets(details.dialog!!.window!!.decorView)
                }
                if (index == 0) screenshot("details-share")
                scenario.onActivity { sheet(it).dismiss() }
                settle()
            }
        }
    }

    @Test fun predictionDetailsKeepLocationWithoutHistoricalFootnote() {
        Appearances.default.forEachIndexed { index, spec ->
            preview(spec.toSportCards()) { scenario ->
                val predicted = SportCardFixtures.lesson().copy(isLessonReal = false, canSignIn = false)
                scenario.onActivity { it.showDetails(predicted) }
                settle()
                scenario.onActivity { activity ->
                    val details = sheet(activity)
                    val root = details.requireView()
                    val place = root.findViewById<ViewGroup>(R.id.place_card)
                    assertEquals(listOf(R.id.teacher_fact, R.id.flow_fact, R.id.location_fact, R.id.map_button),
                        (0 until place.childCount).map { place.getChildAt(it).id })
                    assertEquals(View.GONE, root.findViewById<View>(R.id.flow_fact).visibility)
                    assertEquals(predicted.roomName, root.findViewById<View>(R.id.location_fact)
                        .findViewById<TextView>(R.id.fact_value).text.toString())
                    assertTrue(root.findViewById<View>(R.id.attention_container).descendants()
                        .filterIsInstance<TextView>().any { it.text == activity.getString(R.string.sport_prediction_hint) })
                    assertTextFits(root)
                    assertTouchTargets(details.dialog!!.window!!.decorView)
                }
                screenshot("prediction-building-$index")
            }
        }
    }

    @Test fun bookingConditionsSeparateWarningsWaitingAndRestrictions() {
        preview(PreviewAppearance(dark = true, widthDp = 320, fontScale = 1.3f)) { scenario ->
            val base = SportCardFixtures.lesson()
            val cases = listOf(
                base.copy(intersection = true) to R.string.sport_booking_allowed,
                base.copy(available = 0, canSignIn = false) to R.string.sport_booking_wait,
                base.copy(canSignIn = false, unavailableReasons = listOf(UnavailableReason.HealthGroupMismatch)) to R.string.sport_booking_no_bypass,
                base.copy(isLessonReal = false, canSignIn = false) to R.string.sport_prediction_waiting,
                base.copy(canSignIn = false) to R.string.sport_booking_uncertain,
                base.copy(typeId = 5, available = 0, canSignIn = false,
                    unavailableReasons = listOf(UnavailableReason.DebtOnly)) to R.string.sport_booking_no_bypass,
                base.copy(canSignIn = false, unavailableReasons = listOf(UnavailableReason.Other("Явный запрет MyITMO"))) to R.string.sport_booking_no_bypass
            )
            cases.forEachIndexed { index, (lesson, title) ->
                scenario.onActivity { it.showDetails(lesson) }
                settle()
                scenario.onActivity {
                    val root = sheet(it).requireView()
                    assertTrue(root.descendants().filterIsInstance<TextView>().any { view -> view.text == it.getString(title) })
                    assertTextFits(root)
                    if (lesson.intersection) assertTrue(root.descendants().filterIsInstance<TextView>()
                        .any { view -> view.text == it.getString(R.string.sport_booking_warning) })
                }
                screenshot("conditions-$index")
                scenario.onActivity { sheet(it).dismiss() }
                settle()
            }
        }
    }

    private companion object {
        const val LONG_SECTION = "Фитнес (функциональная тренировка с элементами кроссфита и растяжкой)"
    }

    private fun friends() = listOf(
        FriendSportBooking(UserSummary(900001, "Тестовый друг с длинным именем", null, emptyList(), UserSharing(true, true)), 1, null),
        FriendSportBooking(UserSummary(900002, "Второй тестовый друг", null, emptyList(), UserSharing(true, true)), 1, SportCardFixtures.entry())
    )

    private fun sheet(activity: SportCardsPreviewActivity) =
        activity.supportFragmentManager.findFragmentByTag(SportCommonDetailsBottomSheet.TAG) as DialogFragment

    private fun preview(appearance: PreviewAppearance, block: (ActivityScenario<SportCardsPreviewActivity>) -> Unit) {
        SportCardsPreviewActivity.appearance = appearance
        try {
            ActivityScenario.launch<SportCardsPreviewActivity>(Intent(ApplicationProvider.getApplicationContext(), SportCardsPreviewActivity::class.java)).use(block)
        } finally {
            SportCardsPreviewActivity.appearance = PreviewAppearance()
        }
    }

    private fun settle() = TestUi.settle(600)

    private fun screenshot(name: String) = Screenshots.capture("sport-cards-screenshots", name)
}
