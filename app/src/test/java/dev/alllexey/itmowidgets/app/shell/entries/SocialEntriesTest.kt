package dev.alllexey.itmowidgets.app.shell.entries

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation3.runtime.result.ResultEffect
import dev.alllexey.itmowidgets.app.shell.EntryRegistry
import dev.alllexey.itmowidgets.app.shell.Nav3AppNavigator
import dev.alllexey.itmowidgets.app.shell.ShellContent
import dev.alllexey.itmowidgets.app.shell.entryRegistry
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.FriendSelectionContract
import dev.alllexey.itmowidgets.core.navigation.ShellBackStack
import dev.alllexey.itmowidgets.core.navigation.ShellSurface
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.di.bridge.StopKoinRule
import dev.alllexey.itmowidgets.feature.debug.ui.PreviewHostApplication
import dev.alllexey.itmowidgets.feature.friendselector.ui.FriendSelectorTestTags
import dev.alllexey.itmowidgets.feature.social.ui.friends.FriendsTestTags
import dev.alllexey.itmowidgets.feature.social.ui.list.UserListTestTags
import dev.alllexey.itmowidgets.feature.social.ui.profile.UserProfileTestTags
import dev.alllexey.itmowidgets.feature.social.ui.search.UserSearchTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.KoinContext
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The social keys in the Compose shell with Koin ViewModels on synthetic data: the friends, another user's friends,
 * people search, the profile and the friend picker render their screens under the real [shellEntries]; a profile
 * opens its person's screens under the page's name, an ISU outside `1..Int.MAX_VALUE` opens nothing, the picker's
 * choice reaches the schedule's opener once, and reselecting the schedule tab closes the picker.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = PreviewHostApplication::class)
class SocialEntriesTest {

    @get:Rule(order = 0)
    val stopKoin = StopKoinRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val navigator = Nav3AppNavigator(ShellBackStack(AppTab.SCHEDULE))
    private val history = RecordingHistory()
    private lateinit var koin: Koin

    @Before
    fun startGraph() {
        koin = startKoin { modules(socialTestModule(history), scheduleTestModule()) }.koin
    }

    @Test
    fun theSocialScreensRenderAndCloseThemselves() {
        show()

        assertRendersAndCloses(AppRoutes.Friends, FriendsTestTags.TABS)
        assertRendersAndCloses(AppRoutes.UserSearch, UserSearchTestTags.FIELD)
        assertRendersAndCloses(AppRoutes.UserFriends(FRIEND_ISU, FRIEND_NAME), UserListTestTags.row(FRIEND_ISU))
        assertRendersAndCloses(AppRoutes.UserProfile(FRIEND_ISU), UserProfileTestTags.HERO)
    }

    @Test
    fun theProfileOpensThePersonsFriendsUnderThePagesName() {
        show()
        act { open(AppRoutes.UserProfile(FRIEND_ISU)) }

        compose.onNodeWithTag(UserProfileTestTags.LIST).performScrollToNode(hasTestTag(UserProfileTestTags.FRIENDS))
        compose.onNodeWithTag(UserProfileTestTags.FRIENDS).performClick()
        compose.waitForIdle()
        assertEquals(
            listOf(AppRoutes.UserProfile(FRIEND_ISU), AppRoutes.UserFriends(FRIEND_ISU, FRIEND_NAME)),
            navigator.state.overlays,
        )
    }

    @Test
    fun aFriendRowOpensTheProfileAndAnIsuOutOfRangeOpensNothing() {
        show()
        act { open(AppRoutes.Friends) }

        compose.onNodeWithTag(UserListTestTags.row(FRIEND_ISU)).performClick()
        compose.waitForIdle()
        assertEquals(listOf(AppRoutes.Friends, AppRoutes.UserProfile(FRIEND_ISU)), navigator.state.overlays)

        act {
            dismissOverlays()
            openUserProfile(0)
            openUserProfile(-1)
        }
        assertTrue(navigator.state.overlays.isEmpty())
    }

    @Test
    fun theFriendPickerRendersAndASelectionReachesTheSchedulesOpenerOnce() {
        val received = mutableListOf<FriendSelection>()
        show(
            entryRegistry {
                entry<AppRoutes.TabRoot> { _, _ ->
                    ResultEffect<FriendSelection> { received += it }
                }
                socialEntries()
            },
        )
        act { open(AppRoutes.FriendSelector()) }

        compose.onNodeWithTag(FriendSelectorTestTags.row(FRIEND_ISU)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(FriendSelectorTestTags.APPLY).performClick()
        compose.waitForIdle()

        assertTrue(navigator.state.floating.isEmpty())
        assertEquals(listOf(FriendSelection(useMySchedule = false, FRIEND_ISU, FRIEND_NAME, pictureUrl = "")), received)
        assertEquals(listOf(FRIEND_ISU), history.recorded)
    }

    @Test
    fun theOwnScheduleIsDeliveredAsNoUser() {
        val received = mutableListOf<FriendSelection>()
        show(
            entryRegistry {
                entry<AppRoutes.TabRoot> { _, _ ->
                    ResultEffect<FriendSelection> { received += it }
                }
                socialEntries()
            },
        )
        act { open(AppRoutes.FriendSelector(selectedIsu = FRIEND_ISU)) }

        compose.onNodeWithTag(FriendSelectorTestTags.OWN_CHIP).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(FriendSelectorTestTags.APPLY).performClick()
        compose.waitForIdle()

        assertEquals(
            listOf(FriendSelection(useMySchedule = true, FriendSelectionContract.NO_USER_ISU, name = "", pictureUrl = "")),
            received,
        )
    }

    @Test
    fun reselectingTheScheduleTabClosesThePicker() {
        show()
        act { open(AppRoutes.FriendSelector()) }
        compose.onNodeWithTag(FriendSelectorTestTags.APPLY).assertExists()
        compose.onNodeWithTag(EntryRegistry.placeholderTag(AppRoutes.FriendSelector())).assertDoesNotExist()

        act { select(AppTab.SCHEDULE) }
        assertTrue(navigator.state.floating.isEmpty())
        compose.onNodeWithTag(FriendSelectorTestTags.APPLY).assertDoesNotExist()
    }

    private fun show(registry: EntryRegistry = shellEntries()) {
        compose.setContent {
            // Koin Compose caches the first graph it reads for the JVM; this test's graph replaces the stopped one.
            KoinContext(koin) {
                ItmoTheme {
                    ShellContent(navigator, registry, ShellSurface.Tabs(demoBanner = false), onDemoSignIn = {})
                }
            }
        }
        compose.waitForIdle()
    }

    private fun act(block: Nav3AppNavigator.() -> Unit) {
        compose.runOnIdle { navigator.block() }
        compose.waitForIdle()
    }

    /** [route] shows its screen's [tag] instead of the placeholder, and its back button closes it. */
    private fun assertRendersAndCloses(route: AppRoute, tag: String) {
        act { open(route) }
        compose.onNodeWithTag(tag).assertIsDisplayed()
        compose.onNodeWithTag(EntryRegistry.placeholderTag(route)).assertDoesNotExist()
        compose.onNodeWithContentDescription(BACK).performClick()
        compose.waitForIdle()
        assertTrue("$route", navigator.state.overlays.isEmpty())
    }

    private companion object {
        const val BACK = "Назад"
        const val FRIEND_ISU = SocialSamples.FRIEND_ISU
        const val FRIEND_NAME = SocialSamples.FRIEND_NAME
    }
}
