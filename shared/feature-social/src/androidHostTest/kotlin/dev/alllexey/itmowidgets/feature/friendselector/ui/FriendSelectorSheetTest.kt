package dev.alllexey.itmowidgets.feature.friendselector.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.navigation.FriendSelectionContract
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.result.valueOrNull
import dev.alllexey.itmowidgets.core.social.PeopleSearchPage
import dev.alllexey.itmowidgets.core.social.PeopleSearchRepository
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.friendselector.domain.FriendSelectionHistory
import dev.alllexey.itmowidgets.feature.friendselector.presentation.FriendSelectorBody
import dev.alllexey.itmowidgets.feature.friendselector.presentation.FriendSelectorScope
import dev.alllexey.itmowidgets.feature.friendselector.presentation.FriendSelectorUiState
import dev.alllexey.itmowidgets.feature.friendselector.presentation.FriendSelectorViewModel
import dev.alllexey.itmowidgets.feature.friendselector.ui.FriendSelectorSamples.friends
import dev.alllexey.itmowidgets.feature.friendselector.ui.FriendSelectorSamples.state
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertNoTextOverflow
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlinx.coroutines.flow.MutableStateFlow
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The picker sheet on the JVM. It replaces the instrumented `RecentFriendsStabilityTest` (the chip order across a
 * refresh, a choice and recreation, Apply recorded once) and the picker cases of `SelectionRowsTest` (row and chip
 * semantics, closed schedules, long names at 320 dp and font scale 1.3). Its looks live in the
 * `FriendSelectorSheetContent_*` goldens; dismissing before the profile and the Fragment result stay in the host.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
// A window that holds the whole 802 dp sheet; each test sizes the sheet itself.
@Config(qualifiers = "w600dp-h900dp")
class FriendSelectorSheetTest {

    private val events = mutableListOf<String>()
    private val actions = FriendSelectorActions(
        onQueryChange = { events += "query $it" },
        onScope = { events += "scope $it" },
        onSelect = { events += "select ${it.isu}" },
        onSelectOwn = { events += "own" },
        onOpenProfile = { events += "profile ${it.isu}" },
        onRetry = { events += "retry" },
        onApply = { events += "apply" },
        onClose = { events += "close" },
    )

    @Test
    fun chipsKeepTheirPlacesAcrossARefreshAChoiceAndRecreation() = runComposeUiTest {
        val source = Friends(LoadState.Loading)
        val history = History(friends.take(FriendSelectorSamples.RECENT).map(UserSummary::isu))
        val saved = SavedStateHandle(mapOf(FriendSelectionContract.ARG_SELECTED_ISU to friends[0].isu))
        var viewModel by mutableStateOf(FriendSelectorViewModel(source, history, NoPeople, saved))
        val delivered = mutableListOf<UserSummary?>()
        var generation by mutableIntStateOf(0)
        setContent {
            key(generation) {
                Sheet(WIDE) { FriendSelectorSheetRoute({ delivered += it }, {}, {}, viewModel = viewModel) }
            }
        }

        waitForIdle()
        assertEquals(listOf(FriendSelectorTestTags.OWN_CHIP), chipTags())
        tagged(FriendSelectorTestTags.LOADING).assertIsDisplayed()
        tagged(FriendSelectorTestTags.APPLY).assertIsNotEnabled()

        source.state.value = LoadState.Content(friends)
        waitForIdle()
        val expected = listOf(FriendSelectorTestTags.OWN_CHIP) + friends.take(5).map { FriendSelectorTestTags.chip(it.isu) }
        assertEquals(expected, chipTags())
        tagged(FriendSelectorTestTags.chip(friends[0].isu)).assertIsSelected()
        val bounds = chipBounds()

        repeat(2) {
            tagged(FriendSelectorTestTags.chip(friends[1].isu)).performClick()
            waitForIdle()
            assertEquals(expected, chipTags())
            assertEquals(bounds, chipBounds())
            tagged(FriendSelectorTestTags.chip(friends[1].isu)).assertIsSelected()
            tagged(FriendSelectorTestTags.chip(friends[0].isu)).assertIsNotSelected()
        }
        assertTrue(history.recorded.isEmpty())

        // A late own profile, a reordered list and a moved history change no chip.
        history.recent = history.recent.reversed()
        source.me.value = FriendSelectorSamples.me
        source.state.value = LoadState.Content(friends.reversed())
        waitForIdle()
        assertEquals(expected, chipTags())
        assertEquals(bounds, chipBounds())

        // Recreation: the composition goes, the view model stays.
        generation++
        waitForIdle()
        assertEquals(expected, chipTags())
        tagged(FriendSelectorTestTags.chip(friends[1].isu)).assertIsSelected()

        // Process death: a new view model over the same saved state keeps the order and the pending choice.
        viewModel = FriendSelectorViewModel(source, History(history.recent.reversed()), NoPeople, saved)
        generation++
        waitForIdle()
        assertEquals(expected, chipTags())
        tagged(FriendSelectorTestTags.chip(friends[1].isu)).assertIsSelected()

        tagged(FriendSelectorTestTags.OWN_CHIP).performClick()
        waitForIdle()
        tagged(FriendSelectorTestTags.OWN_CHIP).assertIsSelected()
        tagged(FriendSelectorTestTags.row(friends[6].isu)).performClick()
        waitForIdle()
        assertEquals(expected, chipTags())
        tagged(FriendSelectorTestTags.APPLY).assertTextContains(friends[6].name.substringBefore(" "), substring = true)

        tagged(FriendSelectorTestTags.APPLY).performClick()
        tagged(FriendSelectorTestTags.APPLY).performClick()
        waitForIdle()
        assertEquals(listOf<UserSummary?>(friends[6]), delivered)
    }

    @Test
    fun failedOrDisabledListsKeepTheChipsAndBlockApply() = runComposeUiTest {
        var state by mutableStateOf(state(FriendSelectorBody.Users(friends)))
        setContent { Sheet { FriendSelectorSheetContent(state, actions) } }
        val expected = chipTags()

        state = state(FriendSelectorBody.Error(AppError.Network))
        waitForIdle()
        assertEquals(expected, chipTags())
        tagged(FriendSelectorTestTags.APPLY).assertIsNotEnabled()
        onNodeWithTag(FriendSelectorTestTags.SCOPE).assertDoesNotExist()
        onNodeWithText("Повторить").performClick()

        state = state(FriendSelectorBody.Disabled)
        waitForIdle()
        assertEquals(expected, chipTags())
        onNodeWithText("Нет подключения к ITMO.Widgets").assertIsDisplayed()
        onNodeWithText("Повторить").assertDoesNotExist()

        state = state(FriendSelectorBody.NoFriends, friends = emptyList())
        waitForIdle()
        assertEquals(listOf(FriendSelectorTestTags.OWN_CHIP), chipTags())
        tagged(FriendSelectorTestTags.APPLY).assertIsEnabled().performClick()

        assertEquals(listOf("retry", "apply"), events)
    }

    @Test
    fun rowsExposeTheChoiceAndClosedSchedulesLeadToTheProfile() = runComposeUiTest {
        val open = friends[0]
        val closed = friends[1].copy(sharing = UserSharing(sport = true, schedule = false))
        val other = friends[2]
        setContent {
            Sheet(WIDE) {
                FriendSelectorSheetContent(
                    state(FriendSelectorBody.Users(listOf(open, closed, other)), selected = open),
                    actions,
                )
            }
        }

        tagged(FriendSelectorTestTags.row(open.isu))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsSelected()
            .assertTextContains(open.name, substring = true)
            .performClick()
            .performTouchInput { longClick() }
        tagged(FriendSelectorTestTags.row(other.isu)).assertIsNotSelected().performClick()
        tagged(FriendSelectorTestTags.row(closed.isu))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ToggleableState))
            .assertTextContains("Расписание скрыто", substring = true)
            .assertHasClickAction()
            .performClick()

        assertEquals(
            listOf("select ${open.isu}", "profile ${open.isu}", "select ${other.isu}", "profile ${closed.isu}"),
            events,
        )
    }

    @Test
    fun chipsReadTheFullNameOrTheOwnScheduleAndSkipClosedSchedules() = runComposeUiTest {
        val closed = friends[1].copy(sharing = UserSharing(sport = true, schedule = false))
        val recent = listOf(friends[0], closed)
        var state by mutableStateOf(
            FriendSelectorUiState(
                body = FriendSelectorBody.Users(recent),
                recentFriends = recent,
                selectedIsu = friends[0].isu,
                applyTarget = friends[0],
            ),
        )
        setContent { Sheet { FriendSelectorSheetContent(state, actions) } }

        assertEquals(listOf(FriendSelectorTestTags.OWN_CHIP, FriendSelectorTestTags.chip(friends[0].isu)), chipTags())
        tagged(FriendSelectorTestTags.chip(friends[0].isu))
            .assertContentDescriptionEquals(friends[0].name)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsSelected()
        tagged(FriendSelectorTestTags.OWN_CHIP).assertContentDescriptionEquals("Моё расписание").assertIsNotSelected()
        tagged(FriendSelectorTestTags.APPLY).assertTextContains("Показать расписание: Александра")

        state = state.copy(selectedIsu = null, applyTarget = null)
        waitForIdle()
        tagged(FriendSelectorTestTags.OWN_CHIP).assertIsSelected().performClick()
        tagged(FriendSelectorTestTags.chip(friends[0].isu)).assertIsNotSelected().performClick()
        tagged(FriendSelectorTestTags.APPLY).assertTextContains("Показать моё расписание")

        assertEquals(listOf("own", "select ${friends[0].isu}"), events)
    }

    @Test
    fun theScopeSwitchesTheHintAndTheFieldReportsAndClearsTheQuery() = runComposeUiTest {
        var scope by mutableStateOf(FriendSelectorScope.FRIENDS)
        var query by mutableStateOf("")
        val typing = FriendSelectorActions(
            onQueryChange = {
                query = it
                events += "query $it"
            },
            onScope = {
                scope = it
                events += "scope $it"
            },
            onClose = { events += "close" },
        )
        setContent {
            Sheet {
                FriendSelectorSheetContent(state(FriendSelectorBody.PeopleIdle, scope = scope), typing, query = query)
            }
        }

        onNodeWithText("Имя, ИСУ или группа", useUnmergedTree = true).assertExists()
        onNodeWithText("Все").performClick()
        waitForIdle()
        onNodeWithText("Имя или фамилия", useUnmergedTree = true).assertExists()
        onNodeWithText("Все").performClick()
        tagged(FriendSelectorTestTags.SEARCH).performTextInput("Со")
        waitForIdle()
        tagged(FriendSelectorTestTags.CLEAR).performClick()
        waitForIdle()
        onNodeWithTag(FriendSelectorTestTags.CLEAR).assertDoesNotExist()
        onNodeWithContentDescription("Закрыть").performClick()

        assertEquals(listOf("scope ALL", "query Со", "query ", "close"), events)
    }

    @Test
    fun longNamesFitAtNarrowWidthAndLargeFontInEveryState() = runComposeUiTest {
        var state by mutableStateOf(state(FriendSelectorBody.Users(friends), selected = friends[0]))
        setContent { Sheet { FriendSelectorSheetContent(state, actions, query = FriendSelectorSamples.PEOPLE_QUERY) } }
        val states = listOf(
            state(FriendSelectorBody.Users(friends), selected = friends[0]),
            state(FriendSelectorBody.Users(FriendSelectorSamples.locked), FriendSelectorSamples.locked),
            state(FriendSelectorBody.Users(FriendSelectorSamples.people), scope = FriendSelectorScope.ALL),
            state(FriendSelectorBody.NoMatches),
            state(FriendSelectorBody.PeopleIdle, scope = FriendSelectorScope.ALL),
            state(FriendSelectorBody.PeopleEmpty, scope = FriendSelectorScope.ALL),
            state(FriendSelectorBody.PeopleError(AppError.Network), scope = FriendSelectorScope.ALL),
            state(FriendSelectorBody.NoFriends, friends = emptyList()),
            state(FriendSelectorBody.Disabled),
            state(FriendSelectorBody.Error(AppError.Network)),
        )
        for (next in states) {
            state = next
            waitForIdle()
            // Chip labels are first names in one line; the ellipsis is theirs by design.
            assertNoTextOverflow(allowed = hasAnyAncestor(hasTestTag(FriendSelectorTestTags.RECENT)))
            assertTouchTargets()
        }
    }

    private fun ComposeUiTest.tagged(tag: String) = onNodeWithTag(tag)

    /** The chips' tags in the order they stand, left to right. */
    private fun ComposeUiTest.chipTags(): List<String> = chips().map { it.first }

    private fun ComposeUiTest.chipBounds(): List<Rect> = chips().map { it.second }

    private fun ComposeUiTest.chips(): List<Pair<String, Rect>> =
        onNodeWithTag(FriendSelectorTestTags.RECENT).fetchSemanticsNode().children
            .mapNotNull { node ->
                node.config.getOrNull(SemanticsProperties.TestTag)?.let { it to node.boundsInRoot }
            }
            .sortedBy { it.second.left }

    @Composable
    private fun Sheet(width: Dp = NARROW_WIDTH, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, LARGE_FONT)) {
            ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
                Box(Modifier.size(width, SHEET_HEIGHT)) { content() }
            }
        }
    }

    private class Friends(initial: LoadState<List<UserSummary>>) : FriendRepository {
        val state = MutableStateFlow(initial)
        val me = MutableStateFlow<UserSummary?>(null)
        override fun observeFriendList() = state
        override fun observeCurrentUser() = me
        override suspend fun refreshFriendList() = Unit
        override val currentFriends: List<UserSummary>? get() = state.value.valueOrNull()
    }

    private class History(var recent: List<Int>) : FriendSelectionHistory {
        val recorded = mutableListOf<Int>()
        override suspend fun getRecentIsu() = recent
        override suspend fun record(isu: Int) {
            recorded += isu
        }
    }

    private object NoPeople : PeopleSearchRepository {
        override suspend fun search(query: String, offset: Int): AppResult<PeopleSearchPage> =
            AppResult.Success(PeopleSearchPage(emptyList(), 0, null))
    }

    private companion object {
        val NARROW_WIDTH = 320.dp
        val WIDE = 600.dp
        val SHEET_HEIGHT = 802.dp
        const val LARGE_FONT = 1.3f
    }
}
