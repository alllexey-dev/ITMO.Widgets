package dev.alllexey.itmowidgets.feature.social.ui.home

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.core.home.HomeCardActions
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.home.HomeCardTestTags
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.social.ui.home.preview.FriendRequestsHomePreviewSamples
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class FriendRequestsHomeCardTest {

    @Test
    fun theRendererClaimsTheFriendRequestsCard() {
        assertEquals(setOf(HomeCardKind.FRIEND_REQUESTS), FriendRequestsHomeCardRenderer.kinds)
    }

    @Test
    fun theCardCountsEveryRequestShowsThreePeopleWithTheirGroupAndOpensThem() = runComposeUiTest {
        var user = 0
        var friends = 0
        val actions = HomeCardActions(onOpenUser = { user = it }, onOpenFriends = { friends++ })
        setContent {
            ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
                FriendRequestsHomeCardRenderer.Content(FriendRequestsHomePreviewSamples.longNameCard(), actions, Modifier)
            }
        }
        assertTouchTargets()

        onNodeWithText("4").assertExists()
        onAllNodesWithTag(HomeCardTestTags.FRIEND_ROW).assertCountEquals(FriendRequestsHomeCardRenderer.FRIENDS_LIMIT)
        onAllNodesWithText("M3100").assertCountEquals(FriendRequestsHomeCardRenderer.FRIENDS_LIMIT)
        onNodeWithText("Пётр Сидоров").assertDoesNotExist()

        onAllNodesWithTag(HomeCardTestTags.FRIEND_ROW)[1].performClick()
        onNodeWithTag(HomeCardTestTags.FRIENDS_ALL).performClick()

        assertEquals(300002, user)
        assertEquals(1, friends)
    }
}
