package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.SettingsScreenArgs
import dev.alllexey.itmowidgets.core.text.UiText

/** One settings destination per back-stack entry, restored from its navigation argument. */
enum class SettingsPage(val title: UiText) {
    ROOT(UiText.Resource(R.string.settings_title)),
    SERVICES(UiText.Resource(R.string.settings_custom_services_title)),
    PRIVACY(UiText.Resource(R.string.settings_privacy_title)),
    COMPACT_SCHEDULE_WIDGET(UiText.Resource(R.string.settings_compact_schedule_widget_title)),
    FULL_SCHEDULE_WIDGET(UiText.Resource(R.string.settings_full_schedule_widget_title)),
    QR_WIDGET(UiText.Resource(R.string.settings_group_qr_widget)),
    HOME(UiText.Resource(R.string.settings_group_home)),
    SCHEDULE(UiText.Resource(R.string.settings_group_schedule)),
    SPORT(UiText.Resource(R.string.settings_group_sport)),
    MAINTENANCE(UiText.Resource(R.string.settings_group_maintenance));

    companion object {
        const val ARGUMENT = SettingsScreenArgs.PAGE

        fun fromArgument(value: String?): SettingsPage =
            if (value == "SCHEDULE_WIDGETS") COMPACT_SCHEDULE_WIDGET
            else entries.firstOrNull { it.name == value } ?: ROOT
    }
}
