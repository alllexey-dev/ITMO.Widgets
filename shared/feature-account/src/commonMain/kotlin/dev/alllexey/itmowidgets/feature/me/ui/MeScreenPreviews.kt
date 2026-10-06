package dev.alllexey.itmowidgets.feature.me.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.feature.me.presentation.MeUiState
import dev.alllexey.itmowidgets.feature.me.ui.preview.MePreviewData

/*
 * The harness names a baseline `<function>_<@Preview name>`, and LA-1c recorded the XML references as
 * `MeScreen_<state>` from a debug build, so the developer tools row shows. Each state therefore is a function called
 * `MeScreen` in a holder class of its own; the scanner instantiates each holder by reflection.
 */

@Composable
private fun MePreview(state: MeUiState) = ItmoPreview {
    MeScreen(state, showDebugTools = true, actions = MeActions())
}

internal class MeScreenGroupRequestsPreview {
    @Preview(name = "group-requests")
    @Composable
    fun MeScreen() = MePreview(MePreviewData.GroupAndRequests)
}

internal class MeScreenServicesOffPreview {
    @Preview(name = "services-off")
    @Composable
    fun MeScreen() = MePreview(MePreviewData.ServicesOff)
}

internal class MeScreenFriendsLoadingPreview {
    @Preview(name = "friends-loading")
    @Composable
    fun MeScreen() = MePreview(MePreviewData.FriendsLoading)
}

internal class MeScreenFriendsErrorPreview {
    @Preview(name = "friends-error")
    @Composable
    fun MeScreen() = MePreview(MePreviewData.FriendsError)
}

internal class MeScreenLongNamePreview {
    @Preview(name = "long-name")
    @Composable
    fun MeScreen() = MePreview(MePreviewData.LongName)
}
