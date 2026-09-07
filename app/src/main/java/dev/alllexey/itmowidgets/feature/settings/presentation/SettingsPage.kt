package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.text.UiText

/** One settings destination per back-stack entry, restored from its navigation argument. */
enum class SettingsPage(val title: UiText) {
    ROOT(UiText.Resource(R.string.settings_title)),
    SERVICES(UiText.Resource(R.string.settings_custom_services_title)),
    PRIVACY(UiText.Resource(R.string.settings_privacy_title)),
    SCHEDULE_WIDGETS(UiText.Resource(R.string.settings_group_schedule_widget)),
    QR_WIDGET(UiText.Resource(R.string.settings_group_qr_widget)),
    SPORT(UiText.Resource(R.string.settings_group_sport)),
    MAINTENANCE(UiText.Resource(R.string.settings_group_maintenance));

    companion object {
        const val ARGUMENT = "settings_page"

        fun fromArgument(value: String?): SettingsPage =
            entries.firstOrNull { it.name == value } ?: ROOT
    }
}
