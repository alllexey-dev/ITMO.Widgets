package dev.alllexey.itmowidgets.feature.social.ui.friends

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.feature.social.presentation.FriendsTab
import dev.alllexey.itmowidgets.feature.social.presentation.FriendsUiState
import dev.alllexey.itmowidgets.feature.social.ui.friends.preview.FriendsPreviewData

/*
 * The harness names a baseline `<function>_<@Preview name>`, and LC-1c recorded the XML references as
 * `FriendsScreen_<state>`. Each state therefore is a function called `FriendsScreen` in a holder class of its own;
 * the scanner instantiates each holder by reflection.
 */

@Composable
private fun FriendsPreview(state: FriendsUiState) = ItmoPreview {
    FriendsScreen(
        state = state,
        onSelectTab = {},
        onRefresh = {},
        onRetry = {},
        onAction = { _, _ -> },
        onOpenProfile = {},
        onOpenSearch = {},
        onOpenSettings = {},
        onBack = {},
    )
}

internal class FriendsScreenContentPreview {
    @Preview(name = "content")
    @Composable
    fun FriendsScreen() = FriendsPreview(FriendsPreviewData.friends())
}

internal class FriendsScreenRequestsPreview {
    @Preview(name = "requests")
    @Composable
    fun FriendsScreen() = FriendsPreview(FriendsPreviewData.requests())
}

/** A count past `BadgeDrawable`'s four characters still sits beside «Заявки». */
internal class FriendsScreenBadgePreview {
    @Preview(name = "badge")
    @Composable
    fun FriendsScreen() = FriendsPreview(FriendsPreviewData.friends(incomingCount = 1234))
}

internal class FriendsScreenBusyPreview {
    @Preview(name = "busy")
    @Composable
    fun FriendsScreen() = FriendsPreview(FriendsPreviewData.requests(busyIsu = FriendsPreviewData.BUSY_ISU))
}

internal class FriendsScreenRefreshingPreview {
    @Preview(name = "refreshing")
    @Composable
    fun FriendsScreen() = FriendsPreview(FriendsPreviewData.friends(refreshing = true))
}

internal class FriendsScreenLoadingPreview {
    @Preview(name = "loading")
    @Composable
    fun FriendsScreen() = FriendsPreview(FriendsUiState.Loading)
}

internal class FriendsScreenEmptyPreview {
    @Preview(name = "empty")
    @Composable
    fun FriendsScreen() = FriendsPreview(FriendsPreviewData.empty(FriendsTab.FRIENDS))
}

internal class FriendsScreenEmptyRequestsPreview {
    @Preview(name = "empty-requests")
    @Composable
    fun FriendsScreen() = FriendsPreview(FriendsPreviewData.empty(FriendsTab.REQUESTS))
}

internal class FriendsScreenDisabledPreview {
    @Preview(name = "disabled")
    @Composable
    fun FriendsScreen() = FriendsPreview(FriendsUiState.Disabled)
}

internal class FriendsScreenErrorPreview {
    @Preview(name = "error")
    @Composable
    fun FriendsScreen() = FriendsPreview(FriendsUiState.Error(AppError.Network))
}
