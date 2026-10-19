package dev.alllexey.itmowidgets.feature.me.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.feature.me.presentation.MeUiState
import dev.alllexey.itmowidgets.feature.me.ui.preview.MeSamples

/*
 * The harness names a baseline `<function>_<@Preview name>`, and the baselines are named `MeScreen_<state>` and
 * show a debug build, so the developer tools row shows. Each state therefore is a function called `MeScreen` in a
 * holder class of its own; the scanner instantiates each holder by reflection.
 */

@Composable
private fun MePreview(state: MeUiState) = ItmoPreview {
    MeScreen(state, showDebugTools = true, actions = MeActions())
}

internal class MeScreenRequestsPreview {
    @Preview(name = "requests", heightDp = 1200)
    @Composable
    fun MeScreen() = MePreview(MeSamples.state(connection = "on", requests = "yes"))
}

internal class MeScreenNoRequestsPreview {
    @Preview(name = "no-requests", heightDp = 1200)
    @Composable
    fun MeScreen() = MePreview(MeSamples.state(connection = "on", requests = "no"))
}

internal class MeScreenNotConnectedPreview {
    @Preview(name = "not-connected", heightDp = 1300)
    @Composable
    fun MeScreen() = MePreview(MeSamples.state(connection = "off"))
}

internal class MeScreenLongNamePreview {
    @Preview(name = "long-name")
    @Composable
    fun MeScreen() = MePreview(MeSamples.longName)
}
