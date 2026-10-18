package dev.alllexey.itmowidgets.feature.settings.ui.notifications

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.feature.settings.ui.preview.NotificationSettingsSamples

// «Уведомления» at the design lab's axes, the whole page in one frame.

internal class NotificationSettingsScreenOnPreview {
    @Preview(name = "on", heightDp = 1800)
    @Composable
    fun NotificationSettingsScreen() = ItmoPreview {
        NotificationSettingsScreen(NotificationSettingsSamples.state("on"), NotificationSettingsActions())
    }
}

internal class NotificationSettingsScreenOffPreview {
    @Preview(name = "off", heightDp = 1900)
    @Composable
    fun NotificationSettingsScreen() = ItmoPreview {
        NotificationSettingsScreen(NotificationSettingsSamples.state("off"), NotificationSettingsActions())
    }
}

internal class NotificationSettingsScreenOfflinePreview {
    @Preview(name = "offline", heightDp = 1600)
    @Composable
    fun NotificationSettingsScreen() = ItmoPreview {
        NotificationSettingsScreen(NotificationSettingsSamples.state("offline"), NotificationSettingsActions())
    }
}
