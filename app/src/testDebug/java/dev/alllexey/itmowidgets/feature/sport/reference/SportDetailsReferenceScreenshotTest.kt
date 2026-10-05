package dev.alllexey.itmowidgets.feature.sport.reference

import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.designsystem.AppScreenshotRule
import dev.alllexey.itmowidgets.designsystem.XmlReferenceCapture
import dev.alllexey.itmowidgets.feature.sport.cards.SportCardFixtures
import dev.alllexey.itmowidgets.feature.sport.domain.model.FriendSportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportCommon
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntryStatus
import dev.alllexey.itmowidgets.feature.sport.domain.model.UnavailableReason
import dev.alllexey.itmowidgets.feature.sport.ui.SportCardsPreviewActivity
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportCommonDetailsBottomSheet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The sport details sheet under the names of LP-3's `SportDetailsSheetContent` previews: the real sheet over the
 * debug host, on the host's fixed time and the cases of `SportDetailsSheetVisualTest`. The sheet is a dialog window,
 * so the capture is the sheet itself. LP-3 deletes this class.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class SportDetailsReferenceScreenshotTest {

    @get:Rule
    val shots = AppScreenshotRule(this)

    private val references = XmlReferenceCapture(shots, module = "feature-sport")

    /** A lesson with free places, two friends and a time conflict. */
    @Test
    fun lesson() = capture(
        "SportDetailsSheetContent_lesson",
        SportCardFixtures.lesson().copy(
            friendsBookings = friends(), intersection = true, unavailableReasons = listOf(UnavailableReason.TimeConflict),
        ),
    )

    /** A booking waiting in the free queue, notified, with its history. */
    @Test
    fun queue() = capture(
        "SportDetailsSheetContent_queue",
        SportCardFixtures.booking(2).copy(
            signed = false,
            sectionName = SectionName("""Современные танцы (Клуб парных танцев "Потанцуем")"""),
            signEntry = SportCardFixtures.entry(SportQueueEntryStatus.NOTIFIED),
            friendsBookings = friends(),
        ),
    )

    /** A predicted lesson: no places yet, the prediction hint. */
    @Test
    fun prediction() = capture(
        "SportDetailsSheetContent_prediction",
        SportCardFixtures.lesson().copy(isLessonReal = false, canSignIn = false),
    )

    private fun capture(preview: String, item: SportCommon) = SportReferenceFixtures.withoutAnimations {
        references.host(
            preview,
            SportCardsPreviewActivity::class.java,
            appearance = { SportCardsPreviewActivity.appearance = it },
            ready = { activity ->
                val sheet = activity.sheet()
                if (sheet == null) activity.showDetails(item)
                sheet?.let { it.isResumed && it.requireView().height > 0 } == true
            },
            view = { activity -> SportReferenceFixtures.sheetSurface(activity, checkNotNull(activity.sheet())) },
        )
    }

    private fun SportCardsPreviewActivity.sheet() =
        supportFragmentManager.findFragmentByTag(SportCommonDetailsBottomSheet.TAG) as SportCommonDetailsBottomSheet?

    private fun friends() = listOf(
        FriendSportBooking(UserSummary(900001, "Тестовый друг с длинным именем", null, emptyList(), UserSharing(true, true)), 1, null),
        FriendSportBooking(UserSummary(900002, "Второй тестовый друг", null, emptyList(), UserSharing(true, true)), 1, SportCardFixtures.entry()),
    )
}
