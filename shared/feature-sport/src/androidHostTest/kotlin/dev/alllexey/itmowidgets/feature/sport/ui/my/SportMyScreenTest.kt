package dev.alllexey.itmowidgets.feature.sport.ui.my

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAttempts
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportMyUiState
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The `Мой спорт` page: its states, the cancellation flow, the stale-result guard of the details sheet, and the two
 * cases of the View `SportScoreCollapseTest` that only the page has (content after the first load, content emptied
 * under a collapsed card); the card's own collapse is `SportScoreCollapseTest`.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class SportMyScreenTest {

    private val listState = LazyListState()
    private var state: SportMyUiState by mutableStateOf(SportMyUiState.Loading)
    private var pendingCancel: SportBooking? by mutableStateOf(null)
    private val calls = mutableListOf<String>()
    private val confirmed = mutableListOf<Long>()
    private val actions = SportMyActions(
        onRefresh = { calls += "refresh" },
        onRetry = { calls += "retry" },
        onOpenSign = { calls += "sign" },
        onOpenDetails = { calls += "details ${it.lessonId}" },
        onOpenMap = { calls += "map ${it.lessonId}" },
        onRequestCancel = { pendingCancel = it },
        onConfirmCancel = {
            pendingCancel = null
            confirmed += it.lessonId
        },
        onDismissCancel = { pendingCancel = null },
    )

    @Test
    fun theFirstLoadShowsPlaceholdersOnly() = runComposeUiTest {
        setContent { Screen() }

        onNodeWithTag(SportMyScreenTestTags.SKELETON).assertExists()
        onNodeWithTag(SportScoreCardTestTags.CARD).assertDoesNotExist()
        onNodeWithTag(SportMyScreenTestTags.LIST).assertDoesNotExist()
    }

    @Test
    fun anErrorWithoutContentOffersARetry() = runComposeUiTest {
        state = SportMyUiState.Error(AppError.Network)
        setContent { Screen() }

        onNodeWithTag(SportMyScreenTestTags.ERROR).assertExists()
        onNodeWithTag(SportScoreCardTestTags.CARD).assertDoesNotExist()
        assertTouchTargets()
        onNodeWithText("Повторить").performClick()
        assertEquals(listOf("retry"), calls)
    }

    @Test
    fun withoutBookingsTheCardStaysAboveTheWayToSignUp() = runComposeUiTest {
        state = content(emptyList())
        setContent { Screen() }

        val card = onNodeWithTag(SportScoreCardTestTags.CARD).fetchSemanticsNode().boundsInRoot
        val empty = onNodeWithTag(SportMyScreenTestTags.EMPTY).fetchSemanticsNode().boundsInRoot
        assertTrue(empty.top >= card.bottom, "empty ${empty.top} under card ${card.bottom}")
        assertTouchTargets()
        onNodeWithText("Открыть расписание").performClick()
        assertEquals(listOf("sign"), calls)
    }

    @Test
    fun aPullRefreshesAndABookingOpensItsDetails() = runComposeUiTest {
        state = content(SportBookingSamples.content)
        setContent { Screen() }

        onNodeWithTag(SportMyScreenTestTags.LIST).performTouchInput { swipeDown() }
        waitForIdle()
        assertEquals(listOf("refresh"), calls)

        onAllNodesWithTag(SportBookingCardTestTags.CARD)[1].performClick()
        assertEquals("details ${SportBookingSamples.signed.lessonId}", calls.last())
    }

    @Test
    fun aPartialFailureKeepsTheContentAndOffersARetry() = runComposeUiTest {
        state = content(SportBookingSamples.content).copy(hasPartialError = true)
        setContent { Screen() }

        onNodeWithTag(SportScoreCardTestTags.CARD).assertExists()
        onNodeWithText("Часть данных не загрузилась").assertExists()
        onNodeWithText("Повторить").performClick()
        waitForIdle()
        assertEquals(listOf("retry"), calls)
    }

    @Test
    fun cancellingAsksFirstAndOnlyAConfirmationCancels() = runComposeUiTest {
        state = content(SportBookingSamples.content)
        setContent { Screen() }
        val booking = SportBookingSamples.signed

        // Back leaves the booking alone.
        openCancel(index = 1)
        onNodeWithText("Отменить запись на это занятие?").assertExists()
        onNodeWithText("Назад").performClick()
        waitForIdle()
        onNodeWithText("Отменить запись на это занятие?").assertDoesNotExist()
        assertTrue(confirmed.isEmpty())

        openCancel(index = 1)
        onNodeWithText("Отменить запись").performClick()
        waitForIdle()
        assertEquals(listOf(booking.lessonId), confirmed)
        onNodeWithText("Отменить запись на это занятие?").assertDoesNotExist()
    }

    @Test
    fun aSheetResultCancelsOnlyWhileItsOfferStillHolds() {
        val now = SportBookingSamples.time.now()
        val queued = SportBookingSamples.queued
        val signed = SportBookingSamples.signed
        val shown = content(listOf(queued, signed))

        assertSame(signed, shown.cancelCandidate(signed.lessonId, SportBookingAction.CANCEL.name, now))
        assertSame(queued, shown.cancelCandidate(queued.lessonId, SportBookingAction.CANCEL_AUTO.name, now))
        // The queue signed the user in after the sheet offered to leave it: the stale result does nothing.
        val nowSigned = content(listOf(queued.copy(signed = true, signEntry = null), signed))
        assertNull(nowSigned.cancelCandidate(queued.lessonId, SportBookingAction.CANCEL_AUTO.name, now))
        // Gone from the list, never booked, or no content at all.
        assertNull(content(listOf(signed)).cancelCandidate(queued.lessonId, SportBookingAction.CANCEL_AUTO.name, now))
        assertNull(content(listOf(SportBookingSamples.notSigned)).cancelCandidate(SportBookingSamples.notSigned.lessonId, "NONE", now))
        assertNull(SportMyUiState.Loading.cancelCandidate(signed.lessonId, SportBookingAction.CANCEL.name, now))
        assertNull(shown.cancelCandidate(signed.lessonId, null, now))
    }

    /** View `listReservesTheCardWhenContentArrivesAfterLoading`. */
    @Test
    fun theListReservesTheCardWhenContentArrivesAfterLoading() = runComposeUiTest {
        setContent { Screen() }
        onNodeWithTag(SportMyScreenTestTags.SKELETON).assertExists()

        mainClock.autoAdvance = false
        state = content(bookings(12))
        // Every frame that shows the card keeps the first booking under it, and a layout is not a scroll.
        var frames = 0
        repeat(ENTRY_FRAMES) {
            mainClock.advanceTimeByFrame()
            val card = onAllNodesWithTag(SportScoreCardTestTags.CARD).fetchSemanticsNodes().singleOrNull()
                ?: return@repeat
            val row = onAllNodesWithTag(SportBookingCardTestTags.CARD).fetchSemanticsNodes().firstOrNull()
            assertTrue(row != null && row.boundsInRoot.top >= card.boundsInRoot.bottom, "frame $it: row under the card")
            assertEquals(0, listState.firstItemScrollOffset(), "frame $it: the card starts expanded")
            frames++
        }
        assertTrue(frames > 0, "the content drew")
        mainClock.autoAdvance = true
        val card = onNodeWithTag(SportScoreCardTestTags.CARD).fetchSemanticsNode()

        // A small scroll moves the rows one to one and does not measure the card again.
        val before = onAllNodesWithTag(SportBookingCardTestTags.CARD)[0].fetchSemanticsNode().boundsInRoot.top
        runOnIdle { listState.dispatchRawDelta(SMALL_SCROLL) }
        waitForIdle()
        val after = onAllNodesWithTag(SportBookingCardTestTags.CARD)[0].fetchSemanticsNode().boundsInRoot.top
        assertEquals(before - SMALL_SCROLL, after, 1f)
        assertEquals(SMALL_SCROLL.toInt(), listState.firstItemScrollOffset())
        assertEquals(card.size.height, onNodeWithTag(SportScoreCardTestTags.CARD).fetchSemanticsNode().size.height)
        // The collapse range is the measured card, not a stale zero: the details are taller than the small scroll.
        val details = onNodeWithTag(SportScoreCardTestTags.DETAILS).fetchSemanticsNode().size.height
        assertTrue(details > SMALL_SCROLL, "details $details")
    }

    /** View `emptyContentExpandsCardAndBookingsReturnBelowIt`. */
    @Test
    fun emptyContentExpandsTheCardAndBookingsReturnBelowIt() = runComposeUiTest {
        state = content(bookings(12))
        setContent { Screen() }
        runOnIdle { listState.dispatchRawDelta(COLLAPSING_SCROLL) }
        waitForIdle()
        assertTrue(listState.firstItemScrollOffset() > 0, "the card collapsed")

        state = content(emptyList())
        waitForIdle()
        assertEquals(0, listState.firstItemScrollOffset(), "the empty state has the expanded card")
        val card = onNodeWithTag(SportScoreCardTestTags.CARD).fetchSemanticsNode().boundsInRoot
        assertTrue(onNodeWithTag(SportMyScreenTestTags.EMPTY).fetchSemanticsNode().boundsInRoot.top >= card.bottom)

        state = content(bookings(12))
        waitForIdle()
        val restored = onAllNodesWithTag(SportBookingCardTestTags.CARD)[0].fetchSemanticsNode().boundsInRoot
        assertTrue(restored.top >= card.bottom, "new bookings do not overlap the card")
        assertEquals(0, listState.firstItemScrollOffset())
    }

    private fun ComposeUiTest.openCancel(index: Int) {
        onAllNodesWithTag(SportBookingCardTestTags.MORE)[index].performClick()
        onNodeWithText("Отменить запись").performClick()
        waitForIdle()
    }

    @Composable
    private fun Screen() {
        ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
            SportMyScreen(
                state = state,
                time = SportBookingSamples.time,
                actions = actions,
                modifier = Modifier.size(SCREEN_WIDTH, SCREEN_HEIGHT),
                pendingCancel = pendingCancel,
                listState = listState,
                animateScore = false,
            )
        }
    }

    private fun content(bookings: List<SportBooking>) = SportMyUiState.Content(
        SportAttempts(total = 3, used = 1, free = 2, canSignIn = true),
        SportBookingSamples.score,
        bookings,
    )

    private fun bookings(count: Int): List<SportBooking> =
        (1L..count).map { SportBookingSamples.signed.copy(lessonId = 1000 + it) }

    private companion object {
        val SCREEN_WIDTH = 411.dp
        val SCREEN_HEIGHT = 700.dp
        const val SMALL_SCROLL = 8f
        const val COLLAPSING_SCROLL = 3000f
        const val ENTRY_FRAMES = 4
    }
}
