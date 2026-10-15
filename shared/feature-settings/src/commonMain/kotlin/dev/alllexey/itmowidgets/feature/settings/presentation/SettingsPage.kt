package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.navigation.SettingsScreenArgs
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.settings.SettingsStrings

/** One settings destination per back-stack entry, restored from its navigation argument. */
enum class SettingsPage(val title: UiText) {
    ROOT(UiText.Res(CoreStrings.settings_title)),
    SERVICES(UiText.Res(SettingsStrings.settings_custom_services_title)),
    PRIVACY(UiText.Res(SettingsStrings.settings_privacy_title)),
    COMPACT_SCHEDULE_WIDGET(UiText.Res(SettingsStrings.settings_compact_schedule_widget_title)),
    FULL_SCHEDULE_WIDGET(UiText.Res(SettingsStrings.settings_full_schedule_widget_title)),
    QR_WIDGET(UiText.Res(SettingsStrings.settings_group_qr_widget)),
    APPEARANCE(UiText.Res(SettingsStrings.settings_appearance_title)),
    HOME(UiText.Res(SettingsStrings.settings_home_title)),
    SCHEDULE(UiText.Res(SettingsStrings.settings_group_schedule)),
    RECORDBOOK(UiText.Res(CoreStrings.title_recordbook)),
    SPORT(UiText.Res(SettingsStrings.settings_group_sport)),
    MAINTENANCE(UiText.Res(SettingsStrings.settings_group_maintenance));

    companion object {
        const val ARGUMENT = SettingsScreenArgs.PAGE

        fun fromArgument(value: String?): SettingsPage =
            if (value == "SCHEDULE_WIDGETS") COMPACT_SCHEDULE_WIDGET
            else entries.firstOrNull { it.name == value } ?: ROOT
    }
}
