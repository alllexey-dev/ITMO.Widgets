package dev.alllexey.itmowidgets.feature.connections.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetClose
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.connections.presentation.ConnectionUiState
import dev.alllexey.itmowidgets.feature.connections.presentation.DevicesUiState

/*
 * Baselines `DevicesScreen_<state>`, `ConnectionScreen_<state>` and the sheets' contents: each state is a function
 * of that name in a holder class of its own. Synthetic data only ([ConnectionsSamples]).
 */

@Composable
private fun Devices(state: DevicesUiState) = ItmoPreview { DevicesScreen(state, DevicesActions()) }

@Composable
private fun Client(state: ConnectionUiState) = ItmoPreview { ConnectionScreen(state, ConnectionActions()) }

private val close = SheetClose("Закрыть") {}

internal class DevicesScreenListPreview {
    @Preview(name = "list", heightDp = 1400)
    @Composable
    fun DevicesScreen() = Devices(ConnectionsSamples.devices)
}

internal class DevicesScreenLoadingPreview {
    @Preview(name = "loading")
    @Composable
    fun DevicesScreen() = Devices(ConnectionsSamples.devicesLoading)
}

internal class DevicesScreenServerOffPreview {
    @Preview(name = "server-off")
    @Composable
    fun DevicesScreen() = Devices(ConnectionsSamples.devicesServerOff)
}

internal class DevicesSheetSessionPreview {
    @Preview(name = "session")
    @Composable
    fun DevicesSheet() = ItmoPreview { FactsSheetContent(ConnectionsSamples.sessionSheet, busy = false, DevicesActions()) }
}

internal class ConnectionScreenAgentPreview {
    @Preview(name = "agent", heightDp = 1100)
    @Composable
    fun ConnectionScreen() = Client(ConnectionsSamples.agent)
}

internal class ConnectionScreenMcpClosedPreview {
    @Preview(name = "mcp-closed", heightDp = 1100)
    @Composable
    fun ConnectionScreen() = Client(ConnectionsSamples.mcpClosed)
}

internal class ConnectionScreenWebPreview {
    @Preview(name = "web")
    @Composable
    fun ConnectionScreen() = Client(ConnectionsSamples.web)
}

internal class ConnectionSheetRenewPreview {
    @Preview(name = "renew")
    @Composable
    fun ConnectionSheet() = ItmoPreview { RenewContent(ConnectionsSamples.renew, "nas", ConnectionActions()) }
}

internal class PairingSheetInputPreview {
    @Preview(name = "input")
    @Composable
    fun PairingSheet() = ItmoPreview { PairingInput(ConnectionsSamples.pairInput, close, {}, {}, {}) }
}

internal class PairingSheetConfirmPreview {
    @Preview(name = "confirm", heightDp = 1100)
    @Composable
    fun PairingSheet() = ItmoPreview { PairingConfirm(ConnectionsSamples.pairConfirm, close, {}, { _, _ -> }, {}) }
}

internal class PairingSheetConfirmWebPreview {
    @Preview(name = "confirm-web")
    @Composable
    fun PairingSheet() = ItmoPreview { PairingConfirm(ConnectionsSamples.pairConfirmWeb, close, {}, { _, _ -> }, {}) }
}

internal class PairingSheetDonePreview {
    @Preview(name = "done")
    @Composable
    fun PairingSheet() = ItmoPreview {
        Box(Modifier.padding(bottom = ItmoTheme.spacing.section)) { PairingResultContent(ConnectionsSamples.pairDone, close) {} }
    }
}

internal class PairingSheetOtherAccountPreview {
    @Preview(name = "other-account")
    @Composable
    fun PairingSheet() = ItmoPreview { PairingResultContent(ConnectionsSamples.pairOtherAccount, close) {} }
}
