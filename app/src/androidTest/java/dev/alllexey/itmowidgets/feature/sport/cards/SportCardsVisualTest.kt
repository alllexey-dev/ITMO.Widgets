package dev.alllexey.itmowidgets.feature.sport.cards

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.core.graphics.ColorUtils
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportConditionTone
import androidx.fragment.app.DialogFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.progressindicator.CircularProgressIndicator
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.feature.sport.domain.model.FriendSportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntryStatus
import dev.alllexey.itmowidgets.feature.sport.domain.model.UnavailableReason
import dev.alllexey.itmowidgets.feature.sport.ui.SportCardsPreviewActivity
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportCommonDetailsBottomSheet
import dev.alllexey.itmowidgets.feature.sport.ui.sign.SportLessonItem
import java.io.File
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignStateFactory
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignFilters
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFilterCatalog
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFilterOption
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SportCardsVisualTest {
    @Test fun cardsAndDetailsInLightDarkAndNarrowDynamicPalettes() {
        val appearances = listOf(
            SportCardsPreviewActivity.Appearance(),
            SportCardsPreviewActivity.Appearance(dark = true),
            SportCardsPreviewActivity.Appearance(widthDp = 320, fontScale = 1.3f, colorSeed = 0xff826c24.toInt()),
            SportCardsPreviewActivity.Appearance(widthDp = 320, fontScale = 1.3f, dark = true, colorSeed = 0xff386a20.toInt())
        )
        appearances.forEachIndexed { index, appearance ->
            preview(appearance) { scenario ->
                val queue = SportCardFixtures.booking(2).copy(signed = false,
                    sectionName = SectionName("""Современные танцы (Клуб парных танцев "Потанцуем")"""),
                    signEntry = SportCardFixtures.entry(SportQueueEntryStatus.NOTIFIED), friendsBookings = friends())
                scenario.onActivity {
                    SportConditionTone.entries.forEach { tone ->
                        assertTrue("Contrast $tone", ColorUtils.calculateContrast(tone.accent(it), tone.container(it)) >= 4.5)
                    }
                    it.showBookings(listOf(SportCardFixtures.booking(), queue))
                }
                settle()
                screenshot("bookings-$index")
                scenario.onActivity { assertTextFits(it.list, allowEllipsis = true); it.onBookingClick(queue) }
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

    @Test fun onlineAndExternalLocationsRenderAcrossThemesAndFilters() {
        val appearances = listOf(
            SportCardsPreviewActivity.Appearance(),
            SportCardsPreviewActivity.Appearance(dark = true),
            SportCardsPreviewActivity.Appearance(widthDp = 320, fontScale = 1.3f, colorSeed = 0xff826c24.toInt()),
            SportCardsPreviewActivity.Appearance(widthDp = 320, fontScale = 1.3f, dark = true, colorSeed = 0xff386a20.toInt())
        )
        val original = SportCardFixtures.lesson()
        val online = original.copy(lessonId = 101, sectionName = SectionName("Шахматы"), buildingId = null,
            roomId = -1, roomName = "Online")
        val external = original.copy(lessonId = 102, sectionName = SectionName("Плавание"), buildingId = 335,
            roomId = 20013, roomName = "ул. Правды, 11, ФОК «Юность»")
        val unknown = original.copy(lessonId = 103, sectionName = SectionName("Тренировка"), buildingId = null,
            roomId = 99, roomName = "Место уточняется")
        val clock = object : AcademicTimeProvider {
            override val zoneId = ZoneId.of("Europe/Moscow")
            override fun today() = original.start.toLocalDate()
            override fun now() = original.start.minusHours(1)
        }
        val factory = SportSignStateFactory(clock)
        val catalog = SportFilterCatalog(
            buildings = listOf(
                SportFilterOption(-1, "Онлайн"),
                SportFilterOption(0, "Другие объекты")),
            sections = emptyList(), sportTypes = emptyList(), teachers = emptyList())
        appearances.forEachIndexed { index, appearance ->
            preview(appearance) { scenario ->
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

    @Test fun freePlaceLabelFollowsRussianPluralForms() {
        preview(SportCardsPreviewActivity.Appearance()) { scenario ->
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

    @Test fun recycledLessonActionsStayCorrectAndBusyCannotSubmitAgain() {
        preview(SportCardsPreviewActivity.Appearance(widthDp = 320, fontScale = 1.3f)) { scenario ->
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
                    it.findViewById<MaterialButton>(R.id.sign_up_button).performClick()
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

    @Test fun allBookingStatesResetAndPredictionDoesNotInventPlaces() {
        preview(SportCardsPreviewActivity.Appearance(dark = true, widthDp = 320, fontScale = 1.3f)) { scenario ->
            SportQueueEntryStatus.entries.forEach { state ->
                val item = SportCardFixtures.booking().copy(signed = false, signEntry = SportCardFixtures.entry(state))
                scenario.onActivity { it.showBookings(listOf(item)) }
                settle()
                scenario.onActivity {
                    assertTrue(it.findViewById<TextView>(R.id.status_text_view).text.isNotBlank())
                    assertTextFits(it.list, allowEllipsis = true)
                }
                screenshot("status-${state.name.lowercase()}")
            }
            scenario.onActivity { it.showBookings(listOf(SportCardFixtures.booking().copy(signed = false))) }
            settle()
            scenario.onActivity { assertEquals(it.getString(R.string.sport_status_not_signed), it.findViewById<TextView>(R.id.status_text_view).text.toString()) }
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

    @Test fun predictionDetailsKeepLocationWithoutHistoricalFootnote() {
        val appearances = listOf(
            SportCardsPreviewActivity.Appearance(),
            SportCardsPreviewActivity.Appearance(dark = true),
            SportCardsPreviewActivity.Appearance(widthDp = 320, fontScale = 1.3f, colorSeed = 0xff826c24.toInt()),
            SportCardsPreviewActivity.Appearance(widthDp = 320, fontScale = 1.3f, dark = true, colorSeed = 0xff386a20.toInt())
        )
        appearances.forEachIndexed { index, appearance ->
            preview(appearance) { scenario ->
                val predicted = SportCardFixtures.lesson().copy(isLessonReal = false, canSignIn = false)
                scenario.onActivity { it.showDetails(predicted) }
                settle()
                scenario.onActivity { activity ->
                    val details = sheet(activity)
                    val root = details.requireView()
                    val place = root.findViewById<ViewGroup>(R.id.place_card)
                    assertEquals(listOf(R.id.teacher_fact, R.id.location_fact, R.id.map_button),
                        (0 until place.childCount).map { place.getChildAt(it).id })
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
        preview(SportCardsPreviewActivity.Appearance(dark = true, widthDp = 320, fontScale = 1.3f)) { scenario ->
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
            val blockedQueue = base.copy(available = 0, canSignIn = false,
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
        preview(SportCardsPreviewActivity.Appearance(widthDp = 320, fontScale = 1.3f)) { scenario ->
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

    private fun sheet(activity: SportCardsPreviewActivity) =
        activity.supportFragmentManager.findFragmentByTag(SportCommonDetailsBottomSheet.TAG) as DialogFragment

    private fun preview(appearance: SportCardsPreviewActivity.Appearance, block: (ActivityScenario<SportCardsPreviewActivity>) -> Unit) {
        SportCardsPreviewActivity.appearance = appearance
        try {
            ActivityScenario.launch<SportCardsPreviewActivity>(Intent(ApplicationProvider.getApplicationContext(), SportCardsPreviewActivity::class.java)).use(block)
        } finally {
            SportCardsPreviewActivity.appearance = SportCardsPreviewActivity.Appearance()
        }
    }

    private fun settle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        SystemClock.sleep(600)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.externalCacheDir, "sport-cards-screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun assertTextFits(root: View, allowEllipsis: Boolean = false) {
        root.descendants().filterIsInstance<TextView>().filter { it.isShown && it.text.isNotEmpty() }.forEach { view ->
            val layout = view.layout ?: return@forEach
            assertTrue("Height: ${view.text}", layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
            for (line in 0 until layout.lineCount) {
                if (!allowEllipsis) assertEquals("Ellipsis: ${view.text}", 0, layout.getEllipsisCount(line))
                assertTrue("Width: ${view.text}", layout.getLineMax(line) <= view.width - view.compoundPaddingLeft - view.compoundPaddingRight + 1)
            }
        }
    }

    private fun assertTouchTargets(root: View) {
        val min = 48 * root.resources.displayMetrics.density - 1
        root.descendants().filter { it.isShown && it.isClickable }.forEach {
            assertTrue("Touch target: ${it.javaClass.simpleName}", it.width >= min && it.height >= min)
        }
    }

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

    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) (0 until childCount).forEach { yieldAll(getChildAt(it).descendants()) }
    }
}
