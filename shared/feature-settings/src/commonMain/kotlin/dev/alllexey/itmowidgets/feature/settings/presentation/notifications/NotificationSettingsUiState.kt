package dev.alllexey.itmowidgets.feature.settings.presentation.notifications

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.CorePlurals
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.settings.SettingsStrings

/**
 * «Уведомления» (DECISIONS.md, Screens: one settings page): each type with how it arrives and its switch. [loaded]
 * turns true with the first stored values, so no switch animates from a made-up default.
 */
data class NotificationSettingsUiState(
    val loaded: Boolean = false,
    val permission: NotificationPermission = NotificationPermission.UNKNOWN,
    /** Null where the platform has no mark checks. */
    val marks: MarksNotifications? = null,
    val scheduleChanges: Boolean = true,
    /** The pushes of ITMO.Widgets; the system and the connection decide them, the app has no switch of its own. */
    val pushes: List<PushNotifications> = emptyList(),
    /** Android restricts the app in the background (iOS: Background App Refresh is off) while a check is on. */
    val backgroundWorkRestricted: Boolean = false,
) {
    /** `запрещены в системе`, or how many of the local checks are on. */
    val subtitle: UiText?
        get() = when {
            !loaded -> null
            permission == NotificationPermission.DENIED -> UiText.Res(SettingsStrings.notifications_subtitle_denied)
            else -> {
                val switches = listOfNotNull(marks?.enabled, scheduleChanges)
                UiText.Res(SettingsStrings.notifications_subtitle_count, listOf(switches.count { it }, switches.size))
            }
        }
}

enum class NotificationPermission { UNKNOWN, ALLOWED, DENIED }

/** `Баллы`: on while any source is checked; the sources follow under it. */
data class MarksNotifications(val sources: List<MarkSourceSwitch>) {
    val enabled: Boolean get() = sources.any { it.enabled }
}

/** BARS appears with the account's first BARS answer; before it there is nothing to check. */
enum class MarkSource { MY_ITMO, BARS, SHEETS }

data class MarkSourceSwitch(val source: MarkSource, val enabled: Boolean)

/** A kind of push Backend sends. */
enum class PushKind(val channel: String) {
    SPORT("sport"),
    FRIENDS("friends"),
    DEVICES("connections"),
}

/**
 * One push type. [connected] false: nothing arrives until the connection to ITMO.Widgets is on. [systemEnabled] is the
 * system's switch of its channel where the platform has one per type (Android), null elsewhere.
 */
data class PushNotifications(val kind: PushKind, val connected: Boolean, val systemEnabled: Boolean?) {
    val preview: NotificationPreview get() = NotificationPreviews.push(kind)
}

/** How a notification of one type arrives: the system title and its line, with the sample values of a preview. */
data class NotificationPreview(val title: UiText, val text: UiText?)

/** The previews of each type, in the texts the notifiers use. */
object NotificationPreviews {
    val marks = NotificationPreview(
        UiText.Res(SettingsStrings.notifications_preview_marks_title),
        UiText.Res(SettingsStrings.notifications_preview_marks_text),
    )

    val scheduleChanges = NotificationPreview(
        UiText.Plural(CorePlurals.schedule_changes_notification_title, 1, listOf(1)),
        UiText.Res(SettingsStrings.notifications_preview_changes_text),
    )

    fun push(kind: PushKind): NotificationPreview = when (kind) {
        PushKind.SPORT -> NotificationPreview(
            UiText.Res(CoreStrings.notification_sport_success),
            UiText.Res(SettingsStrings.notifications_preview_sport_text),
        )
        PushKind.FRIENDS -> NotificationPreview(
            UiText.Res(CoreStrings.notification_friend_request, listOf(UiText.Res(SettingsStrings.notifications_preview_friend))),
            null,
        )
        PushKind.DEVICES -> NotificationPreview(
            UiText.Res(CoreStrings.notification_device_connected_title),
            UiText.Res(CoreStrings.notification_device_connected_web),
        )
    }
}

sealed interface NotificationSettingsEvent {
    /** Asks for the permission, or opens the app's system page when the system will not ask any more. */
    data object RequestPermission : NotificationSettingsEvent

    /** The app's notification page of the system (`Звук и вибрация`). */
    data object OpenSystemSettings : NotificationSettingsEvent

    /** The system page of one Android channel. */
    data class OpenChannelSettings(val channel: String) : NotificationSettingsEvent

    /** «Подключение и данные»: a push type that needs the connection. */
    data object OpenConnection : NotificationSettingsEvent

    /** The once-per-device offer to let the app work in the background. */
    data object ShowBackgroundWorkHint : NotificationSettingsEvent

    /** The system page where the background restriction is lifted. */
    data object OpenBackgroundWorkSettings : NotificationSettingsEvent

    data class ShowError(val error: AppError) : NotificationSettingsEvent
}
