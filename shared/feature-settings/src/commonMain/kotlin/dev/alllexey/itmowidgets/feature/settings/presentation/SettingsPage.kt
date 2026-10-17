package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.navigation.SettingsScreenArgs
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.settings.SettingsStrings

/**
 * One settings destination per back-stack entry, restored from its navigation argument. [SERVICES] (with [PRIVACY],
 * whose audiences live there now) and [NOTIFICATIONS] are screens of their own, `ConnectionViewModel` and
 * `NotificationSettingsViewModel`; the hosts route them before a `SettingsViewModel` is made. The keys are the routes
 * other screens link to (the profile, the gates, the bell), so they never change.
 */
enum class SettingsPage(val title: UiText) {
    ROOT(UiText.Res(CoreStrings.settings_title)),
    SERVICES(UiText.Res(SettingsStrings.connection_title)),
    PRIVACY(UiText.Res(SettingsStrings.connection_title)),
    NOTIFICATIONS(UiText.Res(SettingsStrings.notifications_title)),
    COMPACT_SCHEDULE_WIDGET(UiText.Res(SettingsStrings.settings_compact_schedule_widget_title)),
    FULL_SCHEDULE_WIDGET(UiText.Res(SettingsStrings.settings_full_schedule_widget_title)),
    QR_WIDGET(UiText.Res(SettingsStrings.settings_group_qr_widget)),
    APPEARANCE(UiText.Res(SettingsStrings.settings_appearance_title)),
    HOME(UiText.Res(SettingsStrings.settings_home_title)),
    SCHEDULE(UiText.Res(SettingsStrings.settings_group_schedule)),
    SPORT(UiText.Res(SettingsStrings.settings_group_sport)),
    MAINTENANCE(UiText.Res(SettingsStrings.settings_group_maintenance));

    companion object {
        const val ARGUMENT = SettingsScreenArgs.PAGE

        fun fromArgument(value: String?): SettingsPage = when (value) {
            "SCHEDULE_WIDGETS" -> COMPACT_SCHEDULE_WIDGET
            // The mark checks moved to «Уведомления» with the schedule changes.
            "RECORDBOOK" -> NOTIFICATIONS
            else -> entries.firstOrNull { it.name == value } ?: ROOT
        }
    }
}
