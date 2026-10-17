package dev.alllexey.itmowidgets.feature.settings.ui.preview

import dev.alllexey.itmowidgets.feature.settings.presentation.notifications.MarkSource
import dev.alllexey.itmowidgets.feature.settings.presentation.notifications.MarkSourceSwitch
import dev.alllexey.itmowidgets.feature.settings.presentation.notifications.MarksNotifications
import dev.alllexey.itmowidgets.feature.settings.presentation.notifications.NotificationPermission
import dev.alllexey.itmowidgets.feature.settings.presentation.notifications.NotificationSettingsUiState
import dev.alllexey.itmowidgets.feature.settings.presentation.notifications.PushKind
import dev.alllexey.itmowidgets.feature.settings.presentation.notifications.PushNotifications

/** «Уведомления» at the design lab's axes, for the previews of both platforms. */
object NotificationSettingsSamples {
    /**
     * [state] is `on` (allowed, connected, Android channels known), `off` (denied by the system), `ios` (allowed, no
     * per-type channels) or `offline` (not connected, the background restricted).
     */
    fun state(state: String): NotificationSettingsUiState = when (state) {
        "off" -> on.copy(permission = NotificationPermission.DENIED)
        "ios" -> on.copy(pushes = PushKind.entries.map { PushNotifications(it, connected = true, systemEnabled = null) })
        "offline" -> on.copy(
            pushes = PushKind.entries.map { PushNotifications(it, connected = false, systemEnabled = true) },
            backgroundWorkRestricted = true,
        )
        else -> on
    }

    private val on = NotificationSettingsUiState(
        loaded = true,
        permission = NotificationPermission.ALLOWED,
        marks = MarksNotifications(
            listOf(
                MarkSourceSwitch(MarkSource.MY_ITMO, true),
                MarkSourceSwitch(MarkSource.BARS, true),
                MarkSourceSwitch(MarkSource.SHEETS, false),
            ),
        ),
        scheduleChanges = true,
        pushes = listOf(
            PushNotifications(PushKind.SPORT, connected = true, systemEnabled = true),
            PushNotifications(PushKind.FRIENDS, connected = true, systemEnabled = true),
            PushNotifications(PushKind.DEVICES, connected = true, systemEnabled = false),
        ),
    )
}
