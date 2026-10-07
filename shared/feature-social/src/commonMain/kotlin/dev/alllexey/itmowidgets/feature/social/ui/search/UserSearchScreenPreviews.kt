package dev.alllexey.itmowidgets.feature.social.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.feature.social.presentation.UserSearchUiState
import dev.alllexey.itmowidgets.feature.social.ui.search.preview.UserSearchPreviewData

/*
 * The harness names a baseline `<function>_<@Preview name>`, and LC-1c recorded the XML references as
 * `UserSearchScreen_<state>`. Each state therefore is a function called `UserSearchScreen` in a holder class of its
 * own; the scanner instantiates each holder by reflection.
 */

@Composable
private fun UserSearchPreview(state: UserSearchUiState, query: String = UserSearchPreviewData.QUERY) = ItmoPreview {
    UserSearchScreen(
        query = query,
        state = state,
        onQueryChange = {},
        onRetry = {},
        onAction = { _, _ -> },
        onLoadMore = {},
        onOpenProfile = {},
        onBack = {},
    )
}

internal class UserSearchScreenIdlePreview {
    @Preview(name = "idle")
    @Composable
    fun UserSearchScreen() = UserSearchPreview(UserSearchUiState.Idle, query = "")
}

/** Registered people (no relationship, a sent request, a friend), then the others, then `Показать ещё`. */
internal class UserSearchScreenResultsPreview {
    @Preview(name = "results")
    @Composable
    fun UserSearchScreen() = UserSearchPreview(UserSearchPreviewData.results())
}

internal class UserSearchScreenBusyPreview {
    @Preview(name = "busy")
    @Composable
    fun UserSearchScreen() = UserSearchPreview(UserSearchPreviewData.results(busyIsu = UserSearchPreviewData.BUSY_ISU))
}

internal class UserSearchScreenLoadingMorePreview {
    @Preview(name = "loading-more")
    @Composable
    fun UserSearchScreen() = UserSearchPreview(UserSearchPreviewData.results(loadingMore = true))
}

internal class UserSearchScreenLoadingPreview {
    @Preview(name = "loading")
    @Composable
    fun UserSearchScreen() = UserSearchPreview(UserSearchUiState.Loading)
}

internal class UserSearchScreenEmptyPreview {
    @Preview(name = "empty")
    @Composable
    fun UserSearchScreen() = UserSearchPreview(UserSearchUiState.Empty)
}

internal class UserSearchScreenErrorPreview {
    @Preview(name = "error")
    @Composable
    fun UserSearchScreen() = UserSearchPreview(UserSearchUiState.Error(AppError.Network))
}
