package dev.alllexey.itmowidgets.feature.sport.ui.sign

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.cards.SportCardFixtures
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLessonKind
import dev.alllexey.itmowidgets.feature.sport.domain.model.UnavailableReason
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingAction
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportCardTestTags
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertNoTextOverflow
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlinx.datetime.TimeZone
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** The behaviour of `SportLessonCardsVisualTest` on the Compose card, at 320 dp and font scale 1.3. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class SportLessonCardTest {

    private val dispatched = mutableListOf<Pair<Long, SportBookingAction>>()
    private val opened = mutableListOf<Long>()
    private val actions = SportLessonActions(
        onOpen = { opened += it.lessonId },
        onAction = { lesson, action -> dispatched += lesson.lessonId to action },
    )

    @Test
    fun aRecycledCardAlwaysOffersAndSendsTheActionOfItsCurrentLesson() = runComposeUiTest {
        val original = SportCardFixtures.lesson()
        val full = original.copy(available = 0, canSignIn = false, unavailableReasons = listOf(UnavailableReason.Full))
        val cases = listOf(
            original to (SportBookingAction.SIGN to "Записаться"),
            original.copy(signed = true) to (SportBookingAction.CANCEL to "Отписаться"),
            full to (SportBookingAction.AUTO to "Автозапись"),
            full.copy(signEntry = SportCardFixtures.entry()) to (SportBookingAction.CANCEL_AUTO to "Отменить"),
            original.copy(isLessonReal = false, available = 0, canSignIn = false) to (SportBookingAction.AUTO to "Автозапись"),
        )
        var lesson by mutableStateOf(original)
        setContent { NarrowCard(lesson) }

        cases.forEach { (case, expected) ->
            lesson = case
            waitForIdle()
            assertTouchTargets()
            onNodeWithText(expected.second).assertExists()
            onNodeWithTag(SportLessonCardTestTags.ACTION).assertIsEnabled().performClick()
            assertEquals(case.lessonId to expected.first, dispatched.last(), "$expected")
        }
        assertEquals(cases.size, dispatched.size)
    }

    @Test
    fun aQueuedLessonShowsItsPositionAndAPredictionSaysSo() = runComposeUiTest {
        val queued = SportCardFixtures.lesson().copy(available = 0, canSignIn = false,
            unavailableReasons = listOf(UnavailableReason.Full), signEntry = SportCardFixtures.entry())
        var lesson by mutableStateOf(queued)
        setContent { NarrowCard(lesson) }
        onNodeWithTag(SportLessonCardTestTags.STATUS, useUnmergedTree = true).assertTextEquals("Автозапись · 3 из 12")

        lesson = SportCardFixtures.lesson().copy(isLessonReal = false, available = 0, canSignIn = false)
        waitForIdle()
        onNodeWithTag(SportLessonCardTestTags.STATUS, useUnmergedTree = true).assertTextEquals("Прогноз")
        onNodeWithText("Занято", substring = true).assertDoesNotExist()
    }

    @Test
    fun aBusyLessonCannotBeSubmittedAgain() = runComposeUiTest {
        setContent { NarrowCard(SportCardFixtures.lesson(), busy = true) }

        onNodeWithTag(SportLessonCardTestTags.ACTION).assertIsNotEnabled().performClick()
        assertTrue(dispatched.isEmpty())
        assertTouchTargets()
    }

    @Test
    fun theActionOutlineSitsAsFarFromTheBottomAsFromTheEnd() = runComposeUiTest {
        var busy by mutableStateOf(false)
        setContent { NarrowCard(SportCardFixtures.lesson(), busy = busy, fontScale = 1f) }
        assertOutlineGapsAreEqual()

        busy = true
        waitForIdle()
        assertOutlineGapsAreEqual()
    }

    @Test
    fun aStaleOfferIsReplacedOnTapAndSendsNothing() = runComposeUiTest {
        val lesson = SportCardFixtures.lesson()
        val time = MovingTime(lesson.start - 1.hours)
        setContent { NarrowCard(lesson, time) }
        onNodeWithText("Записаться").assertExists()

        time.current = lesson.start + 1.minutes
        onNodeWithTag(SportLessonCardTestTags.ACTION).performClick()
        waitForIdle()

        assertTrue(dispatched.isEmpty())
        onNodeWithTag(SportLessonCardTestTags.ACTION).assertDoesNotExist()
        onNodeWithTag(SportLessonCardTestTags.STATUS, useUnmergedTree = true).assertTextEquals("Занятие уже началось")
    }

    @Test
    fun aBlockedLessonWithAQueueEntryOffersOnlyLeavingTheQueue() = runComposeUiTest {
        val lesson = SportCardFixtures.lesson().copy(available = 0, canSignIn = false,
            unavailableReasons = listOf(UnavailableReason.HealthGroupMismatch, UnavailableReason.Full),
            signEntry = SportCardFixtures.entry())
        setContent { NarrowCard(lesson) }

        onNodeWithTag(SportLessonCardTestTags.ACTION).performClick()
        assertEquals(listOf(lesson.lessonId to SportBookingAction.CANCEL_AUTO), dispatched)
    }

    @Test
    fun withoutAnOfferTheCardSaysWhyAndStillOpensTheDetails() = runComposeUiTest {
        val lesson = SportCardFixtures.lesson().copy(canSignIn = false,
            unavailableReasons = listOf(UnavailableReason.LessonInPast))
        setContent { NarrowCard(lesson) }

        onNodeWithTag(SportLessonCardTestTags.ACTION).assertDoesNotExist()
        onNodeWithTag(SportLessonCardTestTags.STATUS, useUnmergedTree = true).assertExists()
        onNodeWithTag(SportLessonCardTestTags.CARD).performClick()
        assertEquals(listOf(lesson.lessonId), opened)
        assertTrue(dispatched.isEmpty())
    }

    @Test
    fun everyKindKeepsTheTimeReadable() = runComposeUiTest {
        val kinds = SportLessonSamples.everyKind.map { it.copy(intersection = true) }
        assertEquals(SportLessonKind.entries.toSet(), kinds.map { it.kind }.toSet())
        var lesson by mutableStateOf(kinds.first())
        setContent { NarrowCard(lesson) }

        kinds.forEach { kind ->
            lesson = kind
            waitForIdle()
            // The title, the teacher, the place and the chip give way by design; the time and the offer never do.
            assertNoTextOverflow(
                allowed = hasTestTag(SportLessonCardTestTags.TITLE) or hasTestTag(SportLessonCardTestTags.META) or
                    hasTestTag(SportCardTestTags.KIND),
            )
            onNodeWithTag(SportLessonCardTestTags.TIME, useUnmergedTree = true).assertTextEquals("18:30–20:00")
        }
    }

    @Test
    fun theWholeCardOpensTheDetails() = runComposeUiTest {
        setContent { NarrowCard(SportLessonSamples.signed) }

        onNodeWithTag(SportLessonCardTestTags.CARD).performClick()
        assertEquals(listOf(SportLessonSamples.signed.lessonId), opened)
        onNodeWithText("Друзья · 4").assertExists()
    }

    /**
     * The visible outline of the action (the button's semantics bounds) sits as far from the card's bottom edge as
     * from its end edge, as `item_sport_lesson.xml` aligned it, at font scale 1.0, where the outline is 36 dp high.
     */
    private fun ComposeUiTest.assertOutlineGapsAreEqual() {
        val card = onNodeWithTag(SportLessonCardTestTags.CARD).getBoundsInRoot()
        val outline = onNodeWithTag(SportLessonCardTestTags.ACTION).getBoundsInRoot()
        assertEquals(ACTION_HEIGHT.value, (outline.bottom - outline.top).value, 0.5f)
        val endGap = card.right - outline.right
        val bottomGap = card.bottom - outline.bottom
        assertTrue(abs((endGap - bottomGap).value) < 1f, "end gap $endGap, bottom gap $bottomGap")
    }

    @Composable
    private fun NarrowCard(
        lesson: SportLesson,
        time: AcademicTimeProvider = SportLessonSamples.time,
        busy: Boolean = false,
        fontScale: Float = NARROW_FONT_SCALE,
    ) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
                Box(Modifier.width(NARROW_WINDOW)) { SportLessonCard(lesson, time, actions, busy = busy) }
            }
        }
    }

    private class MovingTime(var current: Instant) : AcademicTimeProvider {
        override val timeZone: TimeZone = SportLessonSamples.time.timeZone
        override fun today() = SportLessonSamples.time.today()
        override fun now(): Instant = current
    }

    private companion object {
        const val NARROW_FONT_SCALE = 1.3f

        /** The card keeps its screen margins inside the window. */
        val NARROW_WINDOW = 320.dp

        /** The action's outline inside its 48 dp touch target. */
        val ACTION_HEIGHT = 36.dp
    }
}
