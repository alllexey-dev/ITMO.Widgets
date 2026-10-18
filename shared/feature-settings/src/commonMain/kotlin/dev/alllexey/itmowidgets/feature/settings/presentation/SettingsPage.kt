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
    /** The widgets gallery; the Profile links to it, so its name is a stable route argument. */
    WIDGETS(UiText.Res(SettingsStrings.settings_group_widgets)),
    APPEARANCE(UiText.Res(SettingsStrings.settings_appearance_title)),
    HOME(UiText.Res(SettingsStrings.settings_home_title)),
    SCHEDULE(UiText.Res(SettingsStrings.settings_group_schedule)),
    RECORDBOOK(UiText.Res(CoreStrings.title_recordbook)),
    SPORT(UiText.Res(SettingsStrings.settings_group_sport)),
    MAINTENANCE(UiText.Res(SettingsStrings.settings_group_maintenance));

    companion object {
        const val ARGUMENT = SettingsScreenArgs.PAGE

        /** The per-widget pages the gallery replaced, which placed shortcuts and old links still name. */
        private val OLD_WIDGET_PAGES = setOf("SCHEDULE_WIDGETS", "COMPACT_SCHEDULE_WIDGET", "FULL_SCHEDULE_WIDGET", "QR_WIDGET")

        fun fromArgument(value: String?): SettingsPage =
            if (value in OLD_WIDGET_PAGES) WIDGETS
            else entries.firstOrNull { it.name == value } ?: ROOT
    }
}
