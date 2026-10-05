package dev.alllexey.itmowidgets.feature.sport.cards

import android.content.Intent
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.debug.PreviewAppearance
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.feature.sport.domain.model.FriendSportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntryStatus
import dev.alllexey.itmowidgets.feature.sport.ui.SportCardsPreviewActivity
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportCommonDetailsBottomSheet
import dev.alllexey.itmowidgets.testing.Appearances
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestUi
import dev.alllexey.itmowidgets.testing.ViewChecks.assertTextFits
import dev.alllexey.itmowidgets.testing.toSportCards
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** The `Мой спорт` booking cards on the debug host's list; LP-4b deletes this file with the View list. */
@RunWith(AndroidJUnit4::class)
class SportBookingCardsVisualTest {
    @Test fun bookingCardsInLightDarkAndNarrowDynamicPalettes() {
        Appearances.default.forEachIndexed { index, spec ->
            preview(spec.toSportCards()) { scenario ->
                val queue = SportCardFixtures.booking(2).copy(signed = false,
                    sectionName = SectionName("""Современные танцы (Клуб парных танцев "Потанцуем")"""),
                    signEntry = SportCardFixtures.entry(SportQueueEntryStatus.NOTIFIED), friendsBookings = friends())
                scenario.onActivity { it.showBookings(listOf(SportCardFixtures.booking(), queue)) }
                settle()
                screenshot("bookings-$index")
                scenario.onActivity { assertTextFits(it.list, allowEllipsis = true); it.onBookingClick(queue) }
                settle()
                scenario.onActivity {
                    assertNotNull("A booking card opens its details",
                        it.supportFragmentManager.findFragmentByTag(SportCommonDetailsBottomSheet.TAG))
                }
            }
        }
    }

    @Test fun allBookingStatesReset() {
        preview(PreviewAppearance(dark = true, widthDp = 320, fontScale = 1.3f)) { scenario ->
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
}
