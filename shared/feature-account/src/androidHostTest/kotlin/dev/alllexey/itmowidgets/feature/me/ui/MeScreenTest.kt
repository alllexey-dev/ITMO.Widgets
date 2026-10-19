package dev.alllexey.itmowidgets.feature.me.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.testing.FakeCustomServicesRepository
import dev.alllexey.itmowidgets.core.testing.FakeSessionRepository
import dev.alllexey.itmowidgets.core.testing.FakeSocialRepository
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.me.presentation.MeFriendsSummary
import dev.alllexey.itmowidgets.feature.me.presentation.MeUiState
import dev.alllexey.itmowidgets.feature.me.presentation.MeViewModel
import dev.alllexey.itmowidgets.feature.me.ui.preview.MeSamples
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class MeScreenTest {

    @Test
    fun everyStateKeepsTouchTargets() = runComposeUiTest {
        var state by mutableStateOf(Connected)
        setContent { ItmoTheme { MeScreen(state, showDebugTools = true, actions = MeActions()) } }

        for (value in listOf(Connected, Offline, MeSamples.loading, MeSamples.longName)) {
            state = value
            waitForIdle()
            assertTouchTargets()
        }
    }

    @Test
    fun aReturnToTheTabRefreshesNothingAfterTheFirstShow() = runComposeUiTest {
        val social = FakeSocialRepository()
        val viewModel = MeViewModel(FakeSessionRepository(SessionState.SignedIn(null)), social, FakeCustomServicesRepository(true))
        var shown by mutableStateOf(true)
        setContent { ItmoTheme { if (shown) MeRoute(MeActions(), showDebugTools = false, viewModel = viewModel) } }
        waitForIdle()

        // A pop to the tab root composes it again, as a tab switch does.
        repeat(2) {
            shown = false
            waitForIdle()
            shown = true
            waitForIdle()
        }

        assertEquals(1, social.refreshes)
    }

    @Test
    fun theFriendsRowCountsFriendsAndReadsTheRequests() = runComposeUiTest {
        setContent { ItmoTheme { MeScreen(Connected, showDebugTools = false, actions = MeActions()) } }

        val row = onNodeWithTag(MeTestTags.FRIENDS_ROW).fetchSemanticsNode()
        assertEquals(listOf("Друзья", "3 друга, 1 заявка", "1"), row.config[SemanticsProperties.Text].map { it.text })
        assertEquals(listOf("Входящих заявок: 1"), row.config[SemanticsProperties.ContentDescription])
    }

    @Test
    fun withoutTheConnectionTheGateStandsInPlaceOfThePeopleRows() = runComposeUiTest {
        var opened = 0
        setContent {
            ItmoTheme { MeScreen(Offline, showDebugTools = false, actions = MeActions(onOpenConnection = { opened++ })) }
        }

        onNodeWithTag(MeTestTags.FRIENDS_ROW).assertDoesNotExist()
        onNodeWithText("Друзья недоступны").assertExists()
        onNodeWithText("Подключить").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun theDebugToolsRowFollowsTheHost() = runComposeUiTest {
        var debug by mutableStateOf(false)
        setContent { ItmoTheme { MeScreen(Connected, showDebugTools = debug, actions = MeActions()) } }

        onNodeWithTag(MeTestTags.DEBUG_TOOLS_ROW).assertDoesNotExist()
        debug = true
        waitForIdle()
        scrollTo(MeTestTags.DEBUG_TOOLS_ROW)
        onNodeWithTag(MeTestTags.DEBUG_TOOLS_ROW).assertExists()
    }

    @Test
    fun everyRowReachesTheHost() = runComposeUiTest {
        val calls = mutableListOf<String>()
        val actions = MeActions(
            onOpenFriends = { calls += "friends" },
            onOpenWidgets = { calls += "widgets" },
            onOpenMap = { calls += "map" },
            onOpenMyItmo = { calls += "my itmo" },
            onOpenSettings = { calls += "settings" },
            onOpenDebugTools = { calls += "debug" },
            onShareProfile = { name, isu -> calls += "share $name $isu" },
            onOpenProjectLink = { calls += it.name; true },
        )
        setContent { ItmoTheme { MeScreen(Connected, showDebugTools = true, actions = actions) } }

        onNodeWithTag(MeTestTags.SHARE).performClick()
        for (tag in listOf(
            MeTestTags.FRIENDS_ROW, MeTestTags.WIDGETS_ROW, MeTestTags.SETTINGS_ROW, MeTestTags.DEBUG_TOOLS_ROW,
            MeTestTags.MAP_ROW, MeTestTags.MY_ITMO_ROW, MeTestTags.TELEGRAM, MeTestTags.GITHUB,
        )) {
            scrollTo(tag)
            onNodeWithTag(tag).performClick()
        }

        assertEquals(
            listOf(
                "share Александрова Мария 284731", "friends", "widgets", "settings", "debug", "map", "my itmo", "TELEGRAM", "GITHUB",
            ),
            calls,
        )
    }

    @Test
    fun signOutAsksFirstAndIsInertWhileItRuns() = runComposeUiTest {
        var state by mutableStateOf(Connected)
        var signOuts = 0
        setContent {
            ItmoTheme { MeScreen(state, showDebugTools = false, actions = MeActions(onSignOut = { signOuts++ })) }
        }

        scrollTo(MeTestTags.SIGN_OUT)
        onNodeWithTag(MeTestTags.SIGN_OUT).performClick()
        onNodeWithText("Выйти из аккаунта?").assertExists()
        onNodeWithText("Отмена").performClick()
        assertEquals(0, signOuts)

        onNodeWithTag(MeTestTags.SIGN_OUT).performClick()
        onNode(hasText("Выйти") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(1, signOuts)
        onNode(isDialog()).assertDoesNotExist()

        state = Connected.copy(signOutInProgress = true)
        waitForIdle()
        onNodeWithTag(MeTestTags.SIGN_OUT).performClick()
        onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun anUnknownProfileSaysSoAndOffersNoShare() = runComposeUiTest {
        setContent { ItmoTheme { MeScreen(MeUiState(), showDebugTools = false, actions = MeActions()) } }

        onNodeWithText("Профиль не загрузился").assertExists()
        onNodeWithTag(MeTestTags.PROFILE_META).assertDoesNotExist()
        onNodeWithTag(MeTestTags.PROFILE_GROUP).assertDoesNotExist()
        onNodeWithTag(MeTestTags.SHARE).assertDoesNotExist()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.scrollTo(tag: String) {
        onNodeWithTag(MeTestTags.ROOT).performScrollToNode(hasTestTag(tag))
    }

    private companion object {
        val Connected = MeSamples.state(connection = "on", requests = "yes")
        val Offline = MeSamples.state(connection = "off")
            .copy(friends = MeFriendsSummary.Disabled)
    }
}
