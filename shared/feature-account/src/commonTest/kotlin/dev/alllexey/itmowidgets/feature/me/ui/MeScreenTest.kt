package dev.alllexey.itmowidgets.feature.me.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.me.presentation.MeFriendsSummary
import dev.alllexey.itmowidgets.feature.me.presentation.MeUiState
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class MeScreenTest {

    @Test
    fun everyStateKeepsRowsAtLeast48DpAndEachRowIsOneTarget() = runComposeUiTest {
        var state by mutableStateOf(Signed)
        setContent { ItmoTheme { MeScreen(state, showDebugTools = true, actions = MeActions()) } }

        for (value in States) {
            state = value
            waitForIdle()
            assertTouchTargets()
            // Each row merges its icon, texts and badge into one target: share, the rows and three buttons.
            val rows = if (value.friends == MeFriendsSummary.Disabled) 1 else 3
            val appRows = 2 + if (value.webLoginAvailable) 1 else 0
            assertEquals(1 + rows + appRows + 3, onAllNodes(hasClickAction()).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun theFriendsRowReadsItsCountAndTheRequestsBadgeOnce() = runComposeUiTest {
        setContent { ItmoTheme { MeScreen(Signed, showDebugTools = false, actions = MeActions()) } }

        val row = onNodeWithTag(MeTestTags.FRIENDS_ROW).fetchSemanticsNode()
        assertEquals(listOf("Друзья", "3"), row.config[SemanticsProperties.Text].map { it.text })
        assertEquals(listOf("Входящих заявок: 2"), row.config[SemanticsProperties.ContentDescription])
        onNodeWithTag(MeTestTags.REQUESTS_BADGE, useUnmergedTree = true)
            .assertContentDescriptionEquals("Входящих заявок: 2")
    }

    @Test
    fun noRequestsMeansNoBadge() = runComposeUiTest {
        val state = Signed.copy(friends = MeFriendsSummary.Content(friends = 0, incomingRequests = 0))
        setContent { ItmoTheme { MeScreen(state, showDebugTools = false, actions = MeActions()) } }

        onNodeWithText("Пока никого", useUnmergedTree = true).assertExists()
        onNodeWithTag(MeTestTags.REQUESTS_BADGE, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theWebSignInRowAndItsDividerShowOnlyWithTheConnection() = runComposeUiTest {
        var state by mutableStateOf(Signed)
        setContent { ItmoTheme { MeScreen(state, showDebugTools = false, actions = MeActions()) } }

        val web = onNodeWithTag(MeTestTags.WEB_LOGIN_ROW).getUnclippedBoundsInRoot()
        val settingsWithWeb = onNodeWithTag(MeTestTags.SETTINGS_ROW).getUnclippedBoundsInRoot()
        // The 1 dp divider separates the two rows.
        assertEquals(1.dp, settingsWithWeb.top - web.bottom)

        state = Signed.copy(webLoginAvailable = false)
        waitForIdle()

        onNodeWithTag(MeTestTags.WEB_LOGIN_ROW).assertDoesNotExist()
        // The settings row takes the web row's place: neither the row nor its divider is left behind.
        assertEquals(web.top, onNodeWithTag(MeTestTags.SETTINGS_ROW).getUnclippedBoundsInRoot().top)
    }

    @Test
    fun servicesOffReplacesTheSocialRowsWithOneRowToTheSettings() = runComposeUiTest {
        var opened = 0
        setContent {
            ItmoTheme {
                MeScreen(Offline, showDebugTools = false, actions = MeActions(onOpenServices = { opened++ }))
            }
        }

        for (tag in listOf(MeTestTags.FRIENDS_ROW, MeTestTags.FIND_PEOPLE_ROW, MeTestTags.PRIVACY_ROW)) {
            onNodeWithTag(tag).assertDoesNotExist()
        }
        onNodeWithTag(MeTestTags.SERVICES_DISABLED_ROW).performClick()
        assertEquals(1, opened)
    }

    @Test
    fun theDebugToolsRowFollowsTheHost() = runComposeUiTest {
        var debug by mutableStateOf(false)
        setContent { ItmoTheme { MeScreen(Signed, showDebugTools = debug, actions = MeActions()) } }

        onNodeWithTag(MeTestTags.DEBUG_TOOLS_ROW).assertDoesNotExist()
        debug = true
        waitForIdle()
        onNodeWithTag(MeTestTags.DEBUG_TOOLS_ROW).assertExists()
    }

    @Test
    fun everyRowAndButtonReachesTheHost() = runComposeUiTest {
        val calls = mutableListOf<String>()
        val actions = MeActions(
            onOpenFriends = { calls += "friends" },
            onFindPeople = { calls += "search" },
            onOpenPrivacy = { calls += "privacy" },
            onOpenWebLogin = { calls += "web" },
            onOpenSettings = { calls += "settings" },
            onOpenDebugTools = { calls += "debug" },
            onShareProfile = { name, isu -> calls += "share $name $isu" },
            onOpenProjectLink = { calls += it.name; true },
        )
        setContent { ItmoTheme { MeScreen(Signed, showDebugTools = true, actions = actions) } }

        for (tag in listOf(
            MeTestTags.SHARE, MeTestTags.FRIENDS_ROW, MeTestTags.FIND_PEOPLE_ROW, MeTestTags.PRIVACY_ROW,
            MeTestTags.WEB_LOGIN_ROW, MeTestTags.SETTINGS_ROW, MeTestTags.DEBUG_TOOLS_ROW, MeTestTags.GITHUB,
            MeTestTags.TELEGRAM,
        )) {
            onNodeWithTag(tag).performScrollTo().performClick()
        }

        assertEquals(
            listOf(
                "share $NAME $ISU", "friends", "search", "privacy", "web", "settings", "debug", "GITHUB", "TELEGRAM",
            ),
            calls,
        )
    }

    @Test
    fun signOutAsksFirstAndIsInertWhileItRuns() = runComposeUiTest {
        var state by mutableStateOf(Signed)
        var signOuts = 0
        setContent {
            ItmoTheme { MeScreen(state, showDebugTools = false, actions = MeActions(onSignOut = { signOuts++ })) }
        }

        onNodeWithTag(MeTestTags.SIGN_OUT).performScrollTo().assertIsEnabled().performClick()
        onNodeWithText("Выйти из аккаунта?").assertExists()
        onNodeWithText("Отмена").performClick()
        assertEquals(0, signOuts)

        onNodeWithTag(MeTestTags.SIGN_OUT).performClick()
        onNode(hasText("Выйти") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(1, signOuts)
        onNode(isDialog()).assertDoesNotExist()

        state = Signed.copy(signOutInProgress = true)
        waitForIdle()
        onNodeWithTag(MeTestTags.SIGN_OUT).assertIsNotEnabled()
    }

    @Test
    fun anUnknownProfileSaysSoAndOffersNoShare() = runComposeUiTest {
        setContent { ItmoTheme { MeScreen(MeUiState(), showDebugTools = false, actions = MeActions()) } }

        onNodeWithText("Профиль не загрузился").assertExists()
        onNodeWithTag(MeTestTags.PROFILE_META).assertDoesNotExist()
        onNodeWithTag(MeTestTags.PROFILE_GROUP).assertDoesNotExist()
        onNodeWithTag(MeTestTags.SHARE).assertDoesNotExist()
    }

    private companion object {
        const val ISU = 123456
        const val NAME = "Александрова Мария Александровна"

        val Signed = MeUiState(
            user = CurrentUser(ISU, NAME, null),
            backendUser = UserSummary(
                ISU, NAME, null, listOf(UserGroup("M3205", 2, "ФИТиП")), UserSharing(true, true, true),
            ),
            friends = MeFriendsSummary.Content(friends = 3, incomingRequests = 2),
            webLoginAvailable = true,
        )

        val Offline = MeUiState(user = CurrentUser(ISU, NAME, null), friends = MeFriendsSummary.Disabled)

        val States = listOf(
            Signed,
            Offline,
            Signed.copy(friends = MeFriendsSummary.Loading, backendUser = null),
            Signed.copy(friends = MeFriendsSummary.Error, backendUser = null),
            Signed.copy(signOutInProgress = true),
        )
    }
}
