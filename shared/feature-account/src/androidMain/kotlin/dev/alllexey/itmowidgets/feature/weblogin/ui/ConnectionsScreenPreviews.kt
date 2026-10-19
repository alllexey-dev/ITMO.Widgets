package dev.alllexey.itmowidgets.feature.weblogin.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.weblogin.presentation.ConnectionsUiState

/*
 * Baselines `ConnectionsScreen_<state>` and `ConnectionsDialog_revoke`: each state is a function of that name in a
 * holder class of its own. Synthetic devices only ([ConnectionsSamples]).
 */

@Composable
private fun ConnectionsPreview(state: ConnectionsUiState) = ItmoPreview {
    ConnectionsScreen(state, ConnectionsActions())
}

/** An agent with the shared session, a browser, an agent with the longest name and no activity yet. */
internal class ConnectionsScreenListPreview {
    @Preview(name = "list")
    @Composable
    fun ConnectionsScreen() = ConnectionsPreview(
        ConnectionsUiState.Content(
            listOf(ConnectionsSamples.agent, ConnectionsSamples.web, ConnectionsSamples.longAgent),
        ),
    )
}

internal class ConnectionsScreenEmptyPreview {
    @Preview(name = "empty")
    @Composable
    fun ConnectionsScreen() = ConnectionsPreview(ConnectionsUiState.Content(emptyList()))
}

internal class ConnectionsScreenErrorPreview {
    @Preview(name = "error")
    @Composable
    fun ConnectionsScreen() = ConnectionsPreview(ConnectionsUiState.Error(AppError.Network.toUiText()))
}

/** The confirmation of a device with the shared session, without its window. */
internal class ConnectionsDialogRevokePreview {
    @Preview(name = "revoke")
    @Composable
    fun ConnectionsDialog() = ItmoPreview {
        Box(Modifier.padding(ItmoTheme.spacing.section)) {
            RevokeDialog(ConnectionsSamples.agent, ConnectionsActions(), windowed = false)
        }
    }
}
