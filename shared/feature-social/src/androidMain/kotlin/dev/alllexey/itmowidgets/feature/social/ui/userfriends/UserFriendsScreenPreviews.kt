package dev.alllexey.itmowidgets.feature.social.ui.userfriends

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.feature.social.presentation.UserFriendsUiState
import dev.alllexey.itmowidgets.feature.social.ui.preview.SocialSamples

/*
 * The harness names a baseline `<function>_<@Preview name>`, and the baselines are named
 * `UserFriendsScreen_<state>`. Each state therefore is a function called `UserFriendsScreen` in a holder class of its
 * own; the scanner instantiates each holder by reflection.
 */

@Composable
private fun UserFriendsPreview(state: UserFriendsUiState) = ItmoPreview {
    UserFriendsScreen(
        state = state,
        ownerName = SocialSamples.USER_FRIENDS_OWNER,
        onRefresh = {},
        onRetry = {},
        onOpenProfile = {},
        onConnect = {},
        onBack = {},
    )
}

internal class UserFriendsScreenContentPreview {
    @Preview(name = "content")
    @Composable
    fun UserFriendsScreen() = UserFriendsPreview(SocialSamples.userFriends)
}

internal class UserFriendsScreenEmptyPreview {
    @Preview(name = "empty")
    @Composable
    fun UserFriendsScreen() = UserFriendsPreview(UserFriendsUiState.Content(emptyList()))
}

internal class UserFriendsScreenErrorPreview {
    @Preview(name = "error")
    @Composable
    fun UserFriendsScreen() = UserFriendsPreview(UserFriendsUiState.Error(AppError.Network))
}
