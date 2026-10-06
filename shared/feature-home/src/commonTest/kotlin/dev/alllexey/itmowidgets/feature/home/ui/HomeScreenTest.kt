package dev.alllexey.itmowidgets.feature.home.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeCardActions
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.home.HomeCardRenderer
import dev.alllexey.itmowidgets.core.home.HomeCardTestTags
import dev.alllexey.itmowidgets.core.home.HomeHint
import dev.alllexey.itmowidgets.core.home.HomeScheduleRow
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.toDetailsArgs
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.testing.scheduleChange
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.home.homeScheduleCard
import dev.alllexey.itmowidgets.feature.home.presentation.HomeUiState
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeScreenTest {

    @Test
    fun everyStateShowsItsAreaAndKeepsTouchTargets() = runComposeUiTest {
        var state by mutableStateOf<HomeUiState>(HomeUiState.Loading)
        setContent { ItmoTheme(platformStyle = ItmoPlatformStyle.Material) { HomeScreen(state, HomeActions(), Renderers) } }

        onNodeWithTag(HomeTestTags.LOADING).assertExists()
        onNodeWithTag(HomeTestTags.FEED).assertDoesNotExist()
        assertTouchTargets()

        state = HomeUiState.Content(emptyList(), refreshing = false)
        waitForIdle()
        onNodeWithTag(HomeTestTags.EMPTY).assertExists()
        onNodeWithText("Пока пусто").assertExists()
        onNodeWithTag(HomeTestTags.LOADING).assertDoesNotExist()
        assertTouchTargets()

        for (refreshing in listOf(false, true)) {
            state = HomeUiState.Content(EveryKind, refreshing)
            waitForIdle()
            onNodeWithTag(HomeTestTags.FEED).assertExists()
            onNodeWithTag(HomeTestTags.EMPTY).assertDoesNotExist()
            for (index in EveryKind.indices) {
                onNodeWithTag(HomeTestTags.FEED).performScrollToIndex(index)
                waitForIdle()
                onNodeWithTag(HomeTestTags.card(EveryKind[index].kind)).assertExists()
                assertTouchTargets()
            }
            onNodeWithTag(HomeTestTags.FEED).performScrollToIndex(0)
        }
    }

    @Test
    fun eachCardIsDrawnByTheRendererOfItsKindAndUnclaimedKindsAreLeftOut() = runComposeUiTest {
        var renderers by mutableStateOf(Renderers)
        setContent {
            ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
                Box(Modifier.requiredSize(WideWidth, TallWindow)) {
                    HomeScreen(HomeUiState.Content(EveryKind, refreshing = false), HomeActions(), renderers)
                }
            }
        }

        for (card in EveryKind.filterNot { it is HomeCard.Hint }) {
            onNodeWithTag(HomeTestTags.card(card.kind)).assertExists()
            onNodeWithTag(FakeCards.tag(card.kind)).assertExists()
        }
        onNodeWithText("Добавьте виджет").assertExists()

        renderers = listOf(HintHomeCardRenderer)
        waitForIdle()
        onNodeWithTag(HomeTestTags.card(HomeCardKind.SCHEDULE)).assertDoesNotExist()
        onNodeWithTag(HomeTestTags.card(HomeCardKind.HINT_WIDGETS)).assertExists()

        renderers = emptyList()
        waitForIdle()
        onNodeWithTag(HomeTestTags.EMPTY).assertExists()
    }

    @Test
    fun theButtonsNeverCoverTheLastCard() = runComposeUiTest {
        var width by mutableStateOf(NarrowWidth)
        setContent {
            ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
                Box(Modifier.requiredSize(width, WindowHeight)) {
                    HomeScreen(HomeUiState.Content(EveryKind, refreshing = false), HomeActions(), Renderers)
                }
            }
        }

        for (window in listOf(NarrowWidth, WideWidth)) {
            width = window
            waitForIdle()
            // The list cannot put its last card at the top, so it stops at its end.
            onNodeWithTag(HomeTestTags.FEED).performScrollToIndex(EveryKind.lastIndex)
            waitForIdle()
            val last = onNodeWithTag(HomeTestTags.card(EveryKind.last().kind)).getBoundsInRoot()
            val webFab = onNodeWithTag(HomeTestTags.WEB_FAB).getBoundsInRoot()
            val qrFab = onNodeWithTag(HomeTestTags.QR_FAB).getBoundsInRoot()
            assertTrue(last.bottom <= webFab.top, "the last card at $window ends at ${last.bottom}, the button at ${webFab.top}")
            assertTrue(webFab.bottom <= qrFab.top, "the buttons overlap at $window")
        }
    }

    @Test
    fun theCardsGetTheHostsActionsAndTheButtonsOpenWebAndQr() = runComposeUiTest {
        val calls = mutableListOf<String>()
        var lesson: String? = null
        var pending: PendingSportDetailsArgs? = null
        var user = 0
        var hint: HomeHint? = null
        val dismissed = mutableListOf<HomeCardKind>()
        val actions = HomeActions(
            onLesson = { lesson = it.subjectName },
            onPendingSport = { pending = it },
            onOpenSport = { calls += "sport" },
            onOpenFriends = { calls += "friends" },
            onOpenUser = { user = it },
            onHint = { hint = it },
            onOpenScheduleChanges = { calls += "changes" },
            onOpenMarks = { calls += "marks" },
            onDismiss = { dismissed += it },
            onOpenWeb = { calls += "web" },
            onOpenQr = { calls += "qr" },
        )
        val cards = listOf(homeScheduleCard(), HomeCard.Hint(HomeHint.WIDGETS))
        setContent {
            ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
                Box(Modifier.requiredSize(WideWidth, TallWindow)) {
                    HomeScreen(HomeUiState.Content(cards, refreshing = false), actions, listOf(ActionCard, HintHomeCardRenderer))
                }
            }
        }

        ActionCard.buttons.forEach { onNodeWithTag(it).performClick() }
        assertEquals("Предмет 1", lesson)
        assertEquals(PendingBooking.toDetailsArgs(TimeZone.UTC), pending)
        assertEquals(300001, user)
        onNodeWithTag(HomeCardTestTags.HINT_ACTION).performClick()
        assertEquals(HomeHint.WIDGETS, hint)
        onNodeWithContentDescription("Скрыть подсказку").performClick()
        onNodeWithContentDescription("Открыть My ITMO").performClick()
        onNodeWithContentDescription("Открыть QR-пропуск").performClick()

        assertEquals(listOf(HomeCardKind.MARKS, HomeCardKind.HINT_WIDGETS), dismissed)
        assertEquals(listOf("sport", "friends", "changes", "marks", "web", "qr"), calls)
    }

    @Test
    fun aDismissedKindNamesItsHintOnlyForHintKinds() {
        HomeHint.entries.forEach { assertEquals(it, HomeHint.of(it.kind)) }
        assertNull(HomeHint.of(HomeCardKind.MARKS))
    }

    /** Plain cards of a fixed height for every kind but the hints, which home draws itself. */
    private object FakeCards : HomeCardRenderer {
        override val kinds: Set<HomeCardKind> = HomeCardKind.entries.toSet() - HintHomeCardRenderer.kinds

        fun tag(kind: HomeCardKind) = "fake_${kind.name}"

        @Composable
        override fun Content(card: HomeCard, actions: HomeCardActions, modifier: Modifier) {
            Box(modifier.fillMaxWidth().height(FakeCardHeight)) { Box(Modifier.fillMaxSize().testTag(tag(card.kind))) }
        }
    }

    /** A schedule card whose buttons call every card action but the hint's once. */
    private object ActionCard : HomeCardRenderer {
        override val kinds: Set<HomeCardKind> = setOf(HomeCardKind.SCHEDULE)

        val buttons = listOf("lesson", "pending", "sport", "friends", "user", "changes", "marks", "dismiss-marks")

        @Composable
        override fun Content(card: HomeCard, actions: HomeCardActions, modifier: Modifier) {
            val row = (card as HomeCard.Schedule).rows.first() as HomeScheduleRow.Lesson
            val calls: List<() -> Unit> = listOf(
                { actions.onLesson(row.args) },
                { actions.onPendingSport(PendingBooking.toDetailsArgs(TimeZone.UTC)) },
                actions.onOpenSport,
                actions.onOpenFriends,
                { actions.onOpenUser(300001) },
                actions.onOpenScheduleChanges,
                actions.onOpenMarks,
                { actions.onDismiss(HomeCardKind.MARKS) },
            )
            Column(modifier) {
                buttons.zip(calls).forEach { (tag, call) ->
                    Box(Modifier.fillMaxWidth().height(ButtonHeight).testTag(tag).clickable(onClick = call))
                }
            }
        }
    }

    private companion object {
        val NarrowWidth: Dp = 320.dp
        val WideWidth: Dp = 600.dp
        val WindowHeight: Dp = 891.dp
        val TallWindow: Dp = 2400.dp
        val FakeCardHeight: Dp = 240.dp
        val ButtonHeight: Dp = 48.dp

        val Renderers: List<HomeCardRenderer> = listOf(FakeCards, HintHomeCardRenderer)

        val PendingBooking = PendingSportBooking(
            queueId = 1, queueKind = PendingSportBooking.QueueKind.FREE, lessonId = 101, sectionName = "Плавание",
            start = Instant.parse("2026-09-07T13:00:00Z"), end = Instant.parse("2026-09-07T14:30:00Z"),
            teacherFio = "Тренер", roomName = "Бассейн", isPrediction = false,
        )

        /** One card of every kind, in feed order. */
        val EveryKind: List<HomeCard> = listOf(
            homeScheduleCard(),
            HomeCard.ScheduleChanges(unread = 1, latest = scheduleChange()),
            HomeCard.Marks(listOf("Физика")),
            HomeCard.Sport(score = null, queue = listOf(PendingBooking)),
            HomeCard.FriendRequests(emptyList()),
        ) + HomeHint.entries.map(HomeCard::Hint)
    }
}
