package dev.alllexey.itmowidgets.feature.settings.presentation.notifications

import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.feature.settings.SettingsStrings

/**
 * «Уведомления» (DECISIONS.md, Screens: one settings page): every type with its own switch. [loaded] turns true with
 * the first stored values, so no switch animates from a made-up default.
 */
data class NotificationSettingsUiState(
    val loaded: Boolean = false,
    val permission: NotificationPermission = NotificationPermission.UNKNOWN,
    /** Null where the platform has no mark checks. */
    val marks: MarksNotifications? = null,
    val scheduleChanges: Boolean = true,
    /** The pushes of ITMO.Widgets, each with the app's switch. */
    val pushes: List<PushNotifications> = emptyList(),
    /** The connection to ITMO.Widgets is on; without it Backend sends no push, so their switches wait for it. */
    val connected: Boolean = false,
    /** Android: the system channel of a type that is on here is off, so that type does not arrive. */
    val systemChannelsOff: Boolean = false,
    /** Android restricts the app in the background (iOS: Background App Refresh is off) while a check is on. */
    val backgroundWorkRestricted: Boolean = false,
) {
    /** `запрещены в системе`, or how many of the types that can arrive are on. */
    val subtitle: UiText?
        get() = when {
            !loaded -> null
            permission == NotificationPermission.DENIED -> UiText.Res(SettingsStrings.notifications_subtitle_denied)
            else -> {
                val pushSwitches = if (connected) pushes.map { it.enabled } else emptyList()
                val switches = listOfNotNull(marks?.enabled, scheduleChanges) + pushSwitches
                UiText.Res(SettingsStrings.notifications_subtitle_count, listOf(switches.count { it }, switches.size))
            }
        }
}

enum class NotificationPermission { UNKNOWN, ALLOWED, DENIED }

/** `Баллы`: on while any source is checked; the sources follow under it. */
data class MarksNotifications(val sources: List<MarkCheckSwitch>) {
    val enabled: Boolean get() = sources.any { it.enabled }
}

/** BARS appears with the account's first BARS answer; before it there is nothing to check. */
enum class MarkCheckSource { MY_ITMO, BARS, SHEETS }

data class MarkCheckSwitch(val source: MarkCheckSource, val enabled: Boolean)

/** A kind of push Backend sends, by its channel. */
enum class PushKind(val channel: String) {
    SPORT(AppNotificationChannels.SPORT),
    FRIENDS(AppNotificationChannels.FRIENDS),
    DEVICES(AppNotificationChannels.CONNECTIONS),
}

/** One push type and the app's switch of it. */
data class PushNotifications(val kind: PushKind, val enabled: Boolean)

sealed interface NotificationSettingsEvent {
    /** Asks for the permission, or opens the app's system page when the system will not ask any more. */
    data object RequestPermission : NotificationSettingsEvent

    /** The app's notification page of the system: its channels, sound and vibration. */
    data object OpenSystemSettings : NotificationSettingsEvent

    /** «Подключение и данные»: the pushes need the connection. */
    data object OpenConnection : NotificationSettingsEvent

    /** The once-per-device offer to let the app work in the background. */
    data object ShowBackgroundWorkHint : NotificationSettingsEvent

    /** The system page where the background restriction is lifted. */
    data object OpenBackgroundWorkSettings : NotificationSettingsEvent

    data class ShowError(val error: AppError) : NotificationSettingsEvent
}
