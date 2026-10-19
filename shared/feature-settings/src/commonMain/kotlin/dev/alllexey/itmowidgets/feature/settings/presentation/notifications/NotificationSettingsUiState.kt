package dev.alllexey.itmowidgets.feature.settings.presentation.notifications

import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
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
            else -> notificationsSummary(
                denied = permission == NotificationPermission.DENIED,
                switches = listOfNotNull(marks?.enabled, scheduleChanges) + if (connected) pushes.map { it.enabled } else emptyList(),
            )
        }
}

/** `запрещены в системе`, or how many of [switches] (the types that can arrive) are on; the settings root says it too. */
internal fun notificationsSummary(denied: Boolean, switches: List<Boolean>): UiText =
    if (denied) {
        UiText.Res(SettingsStrings.notifications_subtitle_denied)
    } else {
        UiText.Res(SettingsStrings.notifications_subtitle_count, listOf(switches.count { it }, switches.size))
    }

/** The switches of the types that can arrive, from the stored values, as «Уведомления» lists them. */
internal fun notificationSwitches(local: LocalSettings, capabilities: PlatformCapabilities): List<Boolean> {
    val marks = (local.myItmoMarksEnabled || local.barsMarksEnabled == true || local.sheetMarksEnabled).takeIf { capabilities.marks }
    val pushes = if (capabilities.pushSwitches && local.customServicesEnabled) {
        PushKind.entries.map { it.channel !in local.disabledPushes }
    } else {
        emptyList()
    }
    return listOfNotNull(marks, local.scheduleChangesEnabled) + pushes
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

    /** «Сервер ITMO.Widgets»: the pushes need the connection. */
    data object OpenConnection : NotificationSettingsEvent

    /** The once-per-device offer to let the app work in the background. */
    data object ShowBackgroundWorkHint : NotificationSettingsEvent

    /** The system page where the background restriction is lifted. */
    data object OpenBackgroundWorkSettings : NotificationSettingsEvent

    data class ShowError(val error: AppError) : NotificationSettingsEvent
}
