package dev.alllexey.itmowidgets.feature.settings.ui.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.components.services.ServicesConsentContent
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.settings.ui.preview.ConnectionSamples

// «Сервер ITMO.Widgets» at the design lab's axes, the whole page in one frame, and its two sheets.

internal class ConnectionScreenOnPreview {
    @Preview(name = "on", heightDp = 1100)
    @Composable
    fun ConnectionScreen() = ItmoPreview { ConnectionScreen(ConnectionSamples.state("on"), ConnectionActions()) }
}

internal class ConnectionScreenOffPreview {
    @Preview(name = "off", heightDp = 1100)
    @Composable
    fun ConnectionScreen() = ItmoPreview { ConnectionScreen(ConnectionSamples.state("off"), ConnectionActions()) }
}

internal class ConnectionScreenFailedPreview {
    @Preview(name = "failed", heightDp = 1000)
    @Composable
    fun ConnectionScreen() = ItmoPreview { ConnectionScreen(ConnectionSamples.state("failed"), ConnectionActions()) }
}

internal class ConnectionConsentPreview {
    @Preview(name = "consent", heightDp = 900)
    @Composable
    fun ConnectionConsent() = ItmoPreview {
        Box(Modifier.background(ItmoTheme.colorScheme.surfaceContainerLow)) { ServicesConsentContent(onConnect = {}, onDismiss = {}) }
    }
}

internal class ConnectionStoredPreview {
    @Preview(name = "stored", heightDp = 900)
    @Composable
    fun ConnectionStored() = ItmoPreview {
        Box(Modifier.background(ItmoTheme.colorScheme.surfaceContainerLow)) { StoredDataContent(connected = true, onSource = {}, onDismiss = {}) }
    }
}
