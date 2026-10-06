package dev.alllexey.itmowidgets.feature.home.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.home.HomeHint
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.home.presentation.HomeUiState
import dev.alllexey.itmowidgets.feature.home.ui.preview.HomePreviewSamples
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeScreenTest {

    @Test
    fun everyStateShowsItsAreaAndKeepsTouchTargets() = runComposeUiTest {
        var state by mutableStateOf<HomeUiState>(HomeUiState.Loading)
        setContent { ItmoTheme(platformStyle = ItmoPlatformStyle.Material) { HomeScreen(state, HomeActions()) } }

        onNodeWithTag(HomeTestTags.LOADING).assertExists()
        onNodeWithTag(HomeTestTags.FEED).assertDoesNotExist()
        assertTouchTargets()

        state = HomeUiState.Content(emptyList(), refreshing = false)
        waitForIdle()
        onNodeWithTag(HomeTestTags.EMPTY).assertExists()
        onNodeWithText("Пока пусто").assertExists()
        onNodeWithTag(HomeTestTags.LOADING).assertDoesNotExist()
        assertTouchTargets()

        for (cards in listOf(HomePreviewSamples.cards(), HomePreviewSamples.longNameCards())) {
            for (refreshing in listOf(false, true)) {
                state = HomeUiState.Content(cards, refreshing)
                waitForIdle()
                onNodeWithTag(HomeTestTags.FEED).assertExists()
                onNodeWithTag(HomeTestTags.EMPTY).assertDoesNotExist()
                for (index in cards.indices) {
                    onNodeWithTag(HomeTestTags.FEED).performScrollToIndex(index)
                    waitForIdle()
                    onNodeWithTag(HomeTestTags.card(cards[index].kind)).assertExists()
                    assertTouchTargets()
                }
                onNodeWithTag(HomeTestTags.FEED).performScrollToIndex(0)
            }
        }
    }

    @Test
    fun theButtonsNeverCoverTheLastCard() = runComposeUiTest {
        var cards by mutableStateOf(HomePreviewSamples.cards())
        var width by mutableStateOf(NarrowWidth)
        setContent {
            ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
                Box(Modifier.requiredSize(width, WindowHeight)) {
                    HomeScreen(HomeUiState.Content(cards, refreshing = false), HomeActions())
                }
            }
        }

        for (feed in listOf(HomePreviewSamples.cards(), HomePreviewSamples.longNameCards())) {
            for (window in listOf(NarrowWidth, WideWidth)) {
                cards = feed
                width = window
                waitForIdle()
                // The list cannot put its last card at the top, so it stops at its end.
                onNodeWithTag(HomeTestTags.FEED).performScrollToIndex(feed.lastIndex)
                waitForIdle()
                val last = onNodeWithTag(HomeTestTags.card(feed.last().kind)).getBoundsInRoot()
                val webFab = onNodeWithTag(HomeTestTags.WEB_FAB).getBoundsInRoot()
                val qrFab = onNodeWithTag(HomeTestTags.QR_FAB).getBoundsInRoot()
                assertTrue(last.bottom <= webFab.top, "${feed.last().kind} at $window ends at ${last.bottom}, the button starts at ${webFab.top}")
                assertTrue(webFab.bottom <= qrFab.top, "the buttons overlap at $window")
            }
        }
    }

    @Test
    fun cardsAndRowsCallTheirActions() = runComposeUiTest {
        val calls = mutableListOf<String>()
        var lesson: LessonDetailsArgs? = null
        val pending = mutableListOf<PendingSportDetailsArgs>()
        var user = 0
        var hint: HomeHint? = null
        var dismissedHint: HomeHint? = null
        val actions = HomeActions(
            onLesson = { lesson = it },
            onPendingSport = { pending += it },
            onOpenSport = { calls += "sport" },
            onOpenFriends = { calls += "friends" },
            onOpenUser = { user = it },
            onHint = { hint = it },
            onDismissHint = { dismissedHint = it },
            onOpenScheduleChanges = { calls += "changes" },
            onDismissScheduleChanges = { calls += "changes-read" },
            onOpenMarks = { calls += "marks" },
            onDismissMarks = { calls += "marks-read" },
            onOpenWeb = { calls += "web" },
            onOpenQr = { calls += "qr" },
        )
        setContent {
            ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
                Box(Modifier.requiredSize(WideWidth, TallWindow)) {
                    HomeScreen(HomeUiState.Content(HomePreviewSamples.cards(), refreshing = false), actions)
                }
            }
        }

        onAllNodesWithTag(HomeTestTags.SCHEDULE_ROW).onFirst().performClick()
        assertEquals("Математический анализ", lesson?.subjectName)
        onAllNodesWithTag(HomeTestTags.SCHEDULE_ROW)[2].performClick()
        onAllNodesWithTag(HomeTestTags.SPORT_ROW).onFirst().performClick()
        assertEquals(listOf("Плавание", "Плавание"), pending.map { it.sectionName })

        val changes = onNodeWithTag(HomeTestTags.card(HomeCardKind.SCHEDULE_CHANGES))
        changes.assertContentDescriptionEquals(
            "Изменения в расписании, 3. Математический анализ \u2014 перенесена на ср, 9 сентября, 10:00",
        )
        changes.performClick()
        onNodeWithTag(HomeTestTags.card(HomeCardKind.MARKS))
            .assertContentDescriptionEquals("Новые оценки, 3. Базы данных, Дискретная математика, Алгоритмы и структуры данных")
            .performClick()
        onAllNodesWithTag(HomeTestTags.DISMISS)[0].performClick()
        onAllNodesWithTag(HomeTestTags.DISMISS)[1].performClick()
        // Its centre holds a queue row; the card's own click opens the sport tab.
        onNodeWithTag(HomeTestTags.card(HomeCardKind.SPORT)).performSemanticsAction(SemanticsActions.OnClick)

        onNodeWithTag(HomeTestTags.FEED).performScrollToIndex(5)
        onAllNodesWithTag(HomeTestTags.FRIEND_ROW).onFirst().performClick()
        assertEquals(300001, user)
        onNodeWithTag(HomeTestTags.FRIENDS_ALL).performClick()
        onNodeWithTag(HomeTestTags.HINT_ACTION).performClick()
        assertEquals(HomeHint.WIDGETS, hint)
        onNodeWithContentDescription("Скрыть подсказку").performClick()
        assertEquals(HomeHint.WIDGETS, dismissedHint)

        onNodeWithContentDescription("Открыть My ITMO").performClick()
        onNodeWithContentDescription("Открыть QR-пропуск").performClick()

        assertEquals(
            listOf("changes", "marks", "changes-read", "marks-read", "sport", "friends", "web", "qr"),
            calls,
        )
    }

    private companion object {
        val NarrowWidth: Dp = 320.dp
        val WideWidth: Dp = 600.dp
        val WindowHeight: Dp = 891.dp
        val TallWindow: Dp = 2400.dp
    }
}
