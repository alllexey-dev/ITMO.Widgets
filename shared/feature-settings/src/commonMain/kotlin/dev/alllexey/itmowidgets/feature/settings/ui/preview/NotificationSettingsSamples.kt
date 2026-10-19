package dev.alllexey.itmowidgets.feature.settings.ui.preview

import dev.alllexey.itmowidgets.feature.settings.presentation.notifications.MarkCheckSource
import dev.alllexey.itmowidgets.feature.settings.presentation.notifications.MarkCheckSwitch
import dev.alllexey.itmowidgets.feature.settings.presentation.notifications.MarksNotifications
import dev.alllexey.itmowidgets.feature.settings.presentation.notifications.NotificationPermission
import dev.alllexey.itmowidgets.feature.settings.presentation.notifications.NotificationSettingsUiState
import dev.alllexey.itmowidgets.feature.settings.presentation.notifications.PushKind
import dev.alllexey.itmowidgets.feature.settings.presentation.notifications.PushNotifications

/** «Уведомления» at the design lab's axes, for the previews of both platforms. */
object NotificationSettingsSamples {
    /**
     * [state] is `on` (allowed, connected, one push off), `off` (denied by the system), `channels` (a system channel of
     * a type that is on is off) or `offline` (not connected, the background restricted). Without [pushSwitches]
     * (`PlatformCapabilities.pushSwitches`, iOS) the push types are not listed.
     */
    fun state(state: String, pushSwitches: Boolean = true): NotificationSettingsUiState = when (state) {
        "off" -> on.copy(permission = NotificationPermission.DENIED)
        "channels" -> on.copy(systemChannelsOff = true)
        "offline" -> on.copy(connected = false, backgroundWorkRestricted = true)
        else -> on
    }.let { if (pushSwitches) it else it.copy(pushes = emptyList()) }

    private val on = NotificationSettingsUiState(
        loaded = true,
        permission = NotificationPermission.ALLOWED,
        marks = MarksNotifications(
            listOf(
                MarkCheckSwitch(MarkCheckSource.MY_ITMO, true),
                MarkCheckSwitch(MarkCheckSource.BARS, true),
                MarkCheckSwitch(MarkCheckSource.SHEETS, false),
            ),
        ),
        scheduleChanges = true,
        pushes = listOf(
            PushNotifications(PushKind.SPORT, enabled = true),
            PushNotifications(PushKind.FRIENDS, enabled = true),
            PushNotifications(PushKind.DEVICES, enabled = false),
        ),
        connected = true,
    )
}
