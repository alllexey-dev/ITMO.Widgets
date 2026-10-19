package dev.alllexey.itmowidgets.feature.social.ui.friends

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.feature.social.presentation.FriendsUiState
import dev.alllexey.itmowidgets.feature.social.ui.preview.SocialSamples

/*
 * The harness names a baseline `<function>_<@Preview name>`, and the baselines are named `FriendsScreen_<state>`.
 * Each state therefore is a function called `FriendsScreen` in a holder class of its own.
 */

@Composable
private fun FriendsPreview(state: FriendsUiState) = ItmoPreview { FriendsScreen(state, FriendsActions()) }

internal class FriendsScreenListPreview {
    @Preview(name = "list", heightDp = 1000)
    @Composable
    fun FriendsScreen() = FriendsPreview(SocialSamples.friends("list"))
}

internal class FriendsScreenEmptyPreview {
    @Preview(name = "empty")
    @Composable
    fun FriendsScreen() = FriendsPreview(SocialSamples.friends("empty"))
}

internal class FriendsScreenSearchPreview {
    @Preview(name = "search")
    @Composable
    fun FriendsScreen() = FriendsPreview(SocialSamples.search)
}

internal class FriendsScreenDisabledPreview {
    @Preview(name = "disabled")
    @Composable
    fun FriendsScreen() = FriendsPreview(SocialSamples.friendsDisabled)
}
