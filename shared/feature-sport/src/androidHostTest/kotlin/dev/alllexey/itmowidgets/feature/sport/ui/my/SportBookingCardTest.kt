package dev.alllexey.itmowidgets.feature.sport.ui.my

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.cards.SportCardFixtures
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntryStatus
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertNoTextOverflow
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The behaviour of `SportBookingCardsVisualTest` on the Compose card, at 320 dp and font scale 1.3. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class SportBookingCardTest {

    private val opened = mutableListOf<Long>()
    private val cancelled = mutableListOf<Long>()
    private val mapped = mutableListOf<Long>()
    private val actions = SportBookingActions(
        onOpen = { opened += it.lessonId },
        onCancel = { cancelled += it.lessonId },
        onOpenMap = { mapped += it.lessonId },
    )

    /** `allBookingStatesResetAndPredictionDoesNotInventPlaces`: one card bound to every state in turn. */
    @Test
    fun aReusedCardShowsTheStatusOfEveryStateAndAPredictionInventsNoPlaces() = runComposeUiTest {
        var booking by mutableStateOf(SportCardFixtures.booking())
        setContent { NarrowCard(booking) }

        val expected = mapOf(
            SportQueueEntryStatus.WAITING to "Автозапись · 3 из 12",
            SportQueueEntryStatus.NOTIFIED to "Автозапись · 3 из 12",
            SportQueueEntryStatus.GAVE_UP_NOTIFYING to "Записать не удалось",
            SportQueueEntryStatus.SATISFIED to "Автозапись сработала",
            SportQueueEntryStatus.EXPIRED to "Срок заявки истёк",
        )
        assertEquals(SportQueueEntryStatus.entries.toSet(), expected.keys)
        SportQueueEntryStatus.entries.forEach { status ->
            booking = SportCardFixtures.booking().copy(signed = false, signEntry = SportCardFixtures.entry(status))
            waitForIdle()
            val text = onNodeWithTag(SportBookingCardTestTags.STATUS, useUnmergedTree = true)
                .fetchSemanticsNode().config[SemanticsProperties.Text]
                .joinToString { it.text }
            assertTrue(text.isNotBlank(), "$status has a status")
            assertEquals(expected.getValue(status), text, "$status")
            assertNoTextOverflow(allowed = GIVES_WAY)
            assertTouchTargets()
        }

        booking = SportCardFixtures.booking().copy(signed = false)
        waitForIdle()
        onNodeWithTag(SportBookingCardTestTags.STATUS, useUnmergedTree = true).assertTextEquals("Не записаны")

        booking = SportBookingSamples.predicted
        waitForIdle()
        onNodeWithTag(SportBookingCardTestTags.STATUS, useUnmergedTree = true).assertTextEquals("Автозапись · 2 из 5")
        assertEquals(0, onAllNodesWithText("Занято", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, onAllNodesWithText("мест", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun longNamesStayInsideTheCardAtLargeFont() = runComposeUiTest {
        setContent { NarrowCard(SportBookingSamples.longNames) }

        // The date, the time and the status never shorten.
        assertNoTextOverflow(allowed = GIVES_WAY)
        assertTouchTargets()
        onNodeWithTag(SportBookingCardTestTags.TIME, useUnmergedTree = true).assertTextEquals("15:20–16:50")
        onNodeWithText("Друзья · 4").assertExists()
    }

    @Test
    fun theWholeCardOpensTheDetails() = runComposeUiTest {
        setContent { NarrowCard(SportBookingSamples.signed) }

        onNodeWithTag(SportBookingCardTestTags.CARD).performClick()
        assertEquals(listOf(SportBookingSamples.signed.lessonId), opened)
        assertTrue(cancelled.isEmpty())
    }

    @Test
    fun theMoreMenuCancelsAndOpensTheMap() = runComposeUiTest {
        setContent { NarrowCard(SportBookingSamples.signed) }

        onNodeWithTag(SportBookingCardTestTags.MORE).performClick()
        onNodeWithText("Открыть на карте").performClick()
        assertEquals(listOf(SportBookingSamples.signed.lessonId), mapped)

        onNodeWithTag(SportBookingCardTestTags.MORE).performClick()
        onNodeWithText("Отменить запись").performClick()
        assertEquals(listOf(SportBookingSamples.signed.lessonId), cancelled)
        assertTrue(opened.isEmpty(), "the more button does not open the details")
    }

    @Test
    fun aPlaceWithoutAnAddressOffersNoMap() = runComposeUiTest {
        setContent { NarrowCard(SportBookingSamples.queued.copy(roomName = "Online")) }

        onNodeWithTag(SportBookingCardTestTags.MORE).performClick()
        onNodeWithText("Отменить запись").assertExists()
        onNodeWithText("Открыть на карте").assertDoesNotExist()
    }

    @Test
    fun onlyABookingOrAQueueEntryHasTheMoreButton() = runComposeUiTest {
        var booking by mutableStateOf(SportBookingSamples.notSigned)
        setContent { NarrowCard(booking) }

        onNodeWithTag(SportBookingCardTestTags.MORE).assertDoesNotExist()
        booking = SportBookingSamples.queued
        waitForIdle()
        onNodeWithTag(SportBookingCardTestTags.MORE).assertExists()
        booking = SportBookingSamples.signed
        waitForIdle()
        onNodeWithTag(SportBookingCardTestTags.MORE).assertExists()
    }

    @Test
    fun theWeekdayReadsTheFullDateAloud() = runComposeUiTest {
        setContent { NarrowCard(SportBookingSamples.queued) }

        // Tomorrow from the samples' Monday noon.
        onNode(hasText("Завтра") and hasContentDescriptionStartingWith("Завтра · 8 сентября")).assertExists()
    }

    @Composable
    private fun NarrowCard(booking: SportBooking) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, NARROW_FONT_SCALE)) {
            ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
                Box(Modifier.width(NARROW_WINDOW)) { SportBookingCard(booking, SportBookingSamples.time, actions) }
            }
        }
    }

    private fun hasContentDescriptionStartingWith(prefix: String) =
        SemanticsMatcher("contentDescription starts with $prefix") { node ->
            node.config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }
                .orEmpty().any { it.startsWith(prefix) }
        }

    private companion object {
        const val NARROW_FONT_SCALE = 1.3f

        /** The card keeps its screen margins inside the window. */
        val NARROW_WINDOW = 320.dp

        /**
         * The title, the teacher and the place shorten by design. The kit `Avatar` sizes its initials from its own
         * size, so they are not this card's text; at mdpi their glyphs pass the line check by a pixel.
         */
        val GIVES_WAY = hasTestTag(SportBookingCardTestTags.TITLE) or hasTestTag(SportBookingCardTestTags.META) or
            SemanticsMatcher("avatar initials") { node ->
                node.config.getOrElseNullable(SemanticsProperties.Text) { null }.orEmpty().all { it.text.length <= 2 }
            }
    }
}
