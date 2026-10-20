package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.navigation.SettingsScreenArgs
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.settings.SettingsStrings

/**
 * One settings destination per back-stack entry, restored from its navigation argument. [SERVICES] («Сервер
 * ITMO.Widgets»), [NOTIFICATIONS] and [APPEARANCE] are screens of their own, `ConnectionViewModel`,
 * `NotificationSettingsViewModel` and `AppearanceViewModel`; the hosts route them before a `SettingsViewModel` is made. The keys are the routes
 * other screens link to (the profile, the gates, the bell), so they never change; [fromArgument] maps the keys of the
 * pages that were merged away.
 */
enum class SettingsPage(val title: UiText) {
    ROOT(UiText.Res(CoreStrings.settings_title)),
    SERVICES(UiText.Res(SettingsStrings.connection_title)),
    NOTIFICATIONS(UiText.Res(SettingsStrings.notifications_title)),
    /** The widgets gallery; the Profile links to it, so its name is a stable route argument. */
    WIDGETS(UiText.Res(SettingsStrings.settings_group_widgets)),
    APPEARANCE(UiText.Res(SettingsStrings.settings_appearance_title)),
    /** «Разделы»: the settings of Сегодня, Расписание and Спорт. */
    SECTIONS(UiText.Res(SettingsStrings.settings_sections_title)),
    /** The web sign-in and the connected devices, opened from «Сервер ITMO.Widgets». */
    DEVICES(UiText.Res(SettingsStrings.connection_devices)),
    ABOUT(UiText.Res(SettingsStrings.settings_about_title)),
    /** Where the campus plans come from and the licences of the bundled libraries and fonts. */
    LICENSES(UiText.Res(SettingsStrings.settings_licenses_title));

    companion object {
        const val ARGUMENT = SettingsScreenArgs.PAGE

        /** The keys of pages that were merged into another one, which shortcuts, gates and old links still name. */
        private val MERGED_PAGES = mapOf(
            "SCHEDULE_WIDGETS" to WIDGETS,
            "COMPACT_SCHEDULE_WIDGET" to WIDGETS,
            "FULL_SCHEDULE_WIDGET" to WIDGETS,
            "QR_WIDGET" to WIDGETS,
            // The mark checks moved to «Уведомления» with the schedule changes.
            "RECORDBOOK" to NOTIFICATIONS,
            // The audiences of «Кто видит» live on the connection page.
            "PRIVACY" to SERVICES,
            "HOME" to SECTIONS,
            "SCHEDULE" to SECTIONS,
            "SPORT" to SECTIONS,
            "MAINTENANCE" to ABOUT,
        )

        fun fromArgument(value: String?): SettingsPage =
            MERGED_PAGES[value] ?: entries.firstOrNull { it.name == value } ?: ROOT
    }
}
