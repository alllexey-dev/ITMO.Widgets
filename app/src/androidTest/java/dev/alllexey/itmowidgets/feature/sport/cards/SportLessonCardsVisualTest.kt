package dev.alllexey.itmowidgets.feature.sport.cards

import android.content.Intent
import android.graphics.Rect
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.debug.PreviewAppearance
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.domain.model.FriendSportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFilterCatalog
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFilterOption
import dev.alllexey.itmowidgets.feature.sport.domain.model.UnavailableReason
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignFilters
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignStateFactory
import dev.alllexey.itmowidgets.feature.sport.ui.SportCardsPreviewActivity
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportCommonDetailsBottomSheet
import dev.alllexey.itmowidgets.feature.sport.ui.sign.SportLessonItem
import dev.alllexey.itmowidgets.testing.Appearances
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestUi
import dev.alllexey.itmowidgets.testing.ViewChecks.assertTextFits
import dev.alllexey.itmowidgets.testing.ViewChecks.assertTouchTargets
import dev.alllexey.itmowidgets.testing.ViewChecks.descendants
import dev.alllexey.itmowidgets.testing.toSportCards
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** The `Запись` lesson cards on the debug host's list; LP-5c deletes this file with the View list. */
@RunWith(AndroidJUnit4::class)
class SportLessonCardsVisualTest {
    @Test fun lessonCardsInLightDarkAndNarrowDynamicPalettes() {
        Appearances.default.forEachIndexed { index, spec ->
            preview(spec.toSportCards()) { scenario ->
                val lesson = SportCardFixtures.lesson().copy(friendsBookings = friends(), intersection = true,
                    unavailableReasons = listOf(UnavailableReason.TimeConflict))
                val full = SportCardFixtures.lesson(2).copy(sectionName = SectionName("Плавание"), available = 0,
                    canSignIn = false, unavailableReasons = listOf(UnavailableReason.Full))
                scenario.onActivity { it.showLessons(listOf(SportLessonItem(lesson), SportLessonItem(full))) }
                settle()
                screenshot("lessons-$index")
                scenario.onActivity {
                    assertLessonActionInsets(it.list)
                    assertTextFits(it.list, allowEllipsis = true)
                    it.list.findViewById<View>(R.id.sport_lesson_card_view).performClick()
                }
                settle()
                scenario.onActivity {
                    assertNotNull("A lesson card opens its details",
                        it.supportFragmentManager.findFragmentByTag(SportCommonDetailsBottomSheet.TAG))
                }
            }
        }
    }

    @Test fun onlineAndExternalLocationsRenderAcrossThemesAndFilters() {
        val original = SportCardFixtures.lesson()
        val online = original.copy(lessonId = 101, sectionName = SectionName("Шахматы"), buildingId = null,
            roomId = -1, roomName = "Online")
        val external = original.copy(lessonId = 102, sectionName = SectionName("Плавание"), buildingId = 335,
            roomId = 20013, roomName = "ул. Правды, 11, ФОК «Юность»")
        val unknown = original.copy(lessonId = 103, sectionName = SectionName("Тренировка"), buildingId = null,
            roomId = 99, roomName = "Место уточняется")
        val clock = object : AcademicTimeProvider {
            override val timeZone = TimeZone.of("Europe/Moscow")
            override fun today() = original.start.toLocalDateTime(timeZone).date
            override fun now() = original.start - 1.hours
        }
        val factory = SportSignStateFactory(clock)
        val catalog = SportFilterCatalog(
            buildings = listOf(
                SportFilterOption(-1, "Онлайн"),
                SportFilterOption(0, "Другие объекты")),
            sections = emptyList(), sportTypes = emptyList(), teachers = emptyList())
        Appearances.default.forEachIndexed { index, spec ->
            preview(spec.toSportCards()) { scenario ->
                for ((filter, expected) in listOf("Онлайн" to listOf(online), "Другие объекты" to listOf(external, unknown))) {
                    val state = factory.create(listOf(online, external, unknown), catalog, emptyList(),
                        SportSignFilters(
                            selectedDate = clock.today(), selectedBuildingName = filter), false)
                    assertEquals(expected, state.displayedLessons)
                    scenario.onActivity { it.showLessons(state.displayedLessons.map { row -> SportLessonItem(row) }) }
                    settle()
                    scenario.onActivity { assertTextFits(it.list, allowEllipsis = true); assertTouchTargets(it.list) }
                    screenshot("venue-${if (filter == "Онлайн") "online" else "external"}-$index")
                }
            }
        }
    }

    @Test fun recycledLessonActionsStayCorrectAndBusyCannotSubmitAgain() {
        preview(PreviewAppearance(widthDp = 320, fontScale = 1.3f)) { scenario ->
            val original = SportCardFixtures.lesson()
            val cases = listOf(
                SportLessonItem(original) to "sign",
                SportLessonItem(original.copy(signed = true)) to "unsign",
                SportLessonItem(original.copy(available = 0, canSignIn = false, unavailableReasons = listOf(UnavailableReason.Full))) to "auto",
                SportLessonItem(original.copy(available = 0, canSignIn = false, unavailableReasons = listOf(UnavailableReason.Full), signEntry = SportCardFixtures.entry())) to "unauto",
                SportLessonItem(original.copy(isLessonReal = false, available = 0, canSignIn = false)) to "auto"
            )
            cases.forEach { (item, expected) ->
                scenario.onActivity { it.showLessons(listOf(item)) }
                settle()
                scenario.onActivity {
                    assertTouchTargets(it.list)
                    assertLessonActionInsets(it.list)
                    val button = it.findViewById<MaterialButton>(R.id.sign_up_button)
                    assertTrue(button.isShown)
                    assertTrue(button.isEnabled)
                    button.performClick()
                    assertEquals(expected, it.lastAction)
                    val progress = it.findViewById<LinearProgressIndicator>(R.id.occupancy_progress)
                    if (item.lesson.isLessonReal) {
                        assertEquals(item.lesson.limit - item.lesson.available, progress.progress)
                        assertTrue(progress.width >= 240 * it.resources.displayMetrics.density)
                    }
                    assertTextFits(it.list, allowEllipsis = true)
                }
            }
            screenshot("prediction")
            scenario.onActivity { it.showLessons(listOf(SportLessonItem(original, isBusy = true))) }
            settle()
            scenario.onActivity {
                val before = it.actionCount
                val button = it.findViewById<MaterialButton>(R.id.sign_up_button)
                assertFalse(button.isEnabled)
                assertLessonActionInsets(it.list)
                button.performClick()
                assertEquals(before, it.actionCount)
            }
            screenshot("busy")
            scenario.onActivity { it.showLessons(listOf(SportLessonItem(original.copy(canSignIn = false, unavailableReasons = listOf(UnavailableReason.LessonInPast))))) }
            settle()
            scenario.onActivity {
                assertEquals(View.GONE, it.findViewById<View>(R.id.sign_up_button).visibility)
                val content = it.findViewById<View>(R.id.lesson_card_content)
                assertEquals("Cards without a button retain regular padding", content.paddingEnd, content.paddingBottom)
            }
            screenshot("unavailable")
            scenario.onActivity { it.showLessons(emptyList()) }
            settle()
            scenario.onActivity { assertEquals(0, it.list.adapter!!.itemCount) }
        }
    }

    @Test fun blockedLessonWithAQueueEntryOffersOnlyLeavingTheQueue() {
        preview(PreviewAppearance(dark = true, widthDp = 320, fontScale = 1.3f)) { scenario ->
            val blockedQueue = SportCardFixtures.lesson().copy(available = 0, canSignIn = false,
                unavailableReasons = listOf(UnavailableReason.HealthGroupMismatch, UnavailableReason.Full),
                signEntry = SportCardFixtures.entry())
            scenario.onActivity { it.showLessons(listOf(SportLessonItem(blockedQueue))) }
            settle()
            scenario.onActivity {
                it.findViewById<MaterialButton>(R.id.sign_up_button).performClick()
                assertEquals("unauto", it.lastAction)
            }
        }
    }

    @Test fun allLessonKindsKeepTimeReadableWithLargeFont() {
        preview(PreviewAppearance(widthDp = 320, fontScale = 1.3f)) { scenario ->
            val base = SportCardFixtures.lesson().copy(intersection = true)
            val lessons = listOf(1, 2, 5, 6, 7, 8, 99).map { base.copy(typeId = it) } +
                listOf(2, 3, 4).map { base.copy(lessonLevel = it) }
            lessons.forEach { lesson ->
                scenario.onActivity { it.showLessons(listOf(SportLessonItem(lesson))) }
                settle()
                scenario.onActivity {
                    assertTextFits(it.list, allowEllipsis = true)
                    val time = it.findViewById<TextView>(R.id.time_text_view)
                    assertEquals(0, time.layout.getEllipsisCount(0))
                }
            }
        }
    }

    private fun friends() = listOf(
        FriendSportBooking(UserSummary(900001, "Тестовый друг с длинным именем", null, emptyList(), UserSharing(true, true)), 1, null),
        FriendSportBooking(UserSummary(900002, "Второй тестовый друг", null, emptyList(), UserSharing(true, true)), 1, SportCardFixtures.entry())
    )

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

    private fun assertLessonActionInsets(root: View) {
        root.descendants().filterIsInstance<MaterialCardView>().filter { it.id == R.id.sport_lesson_card_view }.forEach { card ->
            val button = card.findViewById<MaterialButton>(R.id.sign_up_button)
            if (!button.isShown) return@forEach
            val bounds = Rect().also(button::getDrawingRect)
            card.offsetDescendantRectToMyCoords(button, bounds)
            val endGap = card.width - bounds.right
            val bottomGap = card.height - bounds.bottom + button.insetBottom
            assertEquals("Visible button outline must have equal end and bottom gaps", endGap, bottomGap)
            val minTarget = 48 * root.resources.displayMetrics.density - 1
            assertTrue("Button must retain its touch target", button.height >= minTarget && button.width >= minTarget)
        }
    }
}
