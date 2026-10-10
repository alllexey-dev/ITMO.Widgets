package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.navigation.SettingsScreenArgs
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.core.settings_title
import dev.alllexey.itmowidgets.shared.core.title_recordbook
import dev.alllexey.itmowidgets.shared.feature.settings.Res
import dev.alllexey.itmowidgets.shared.feature.settings.settings_appearance_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_compact_schedule_widget_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_custom_services_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_full_schedule_widget_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_group_home
import dev.alllexey.itmowidgets.shared.feature.settings.settings_group_maintenance
import dev.alllexey.itmowidgets.shared.feature.settings.settings_group_qr_widget
import dev.alllexey.itmowidgets.shared.feature.settings.settings_group_schedule
import dev.alllexey.itmowidgets.shared.feature.settings.settings_group_sport
import dev.alllexey.itmowidgets.shared.feature.settings.settings_privacy_title

/** One settings destination per back-stack entry, restored from its navigation argument. */
enum class SettingsPage(val title: UiText) {
    ROOT(UiText.Res(CoreRes.string.settings_title)),
    SERVICES(UiText.Res(Res.string.settings_custom_services_title)),
    PRIVACY(UiText.Res(Res.string.settings_privacy_title)),
    COMPACT_SCHEDULE_WIDGET(UiText.Res(Res.string.settings_compact_schedule_widget_title)),
    FULL_SCHEDULE_WIDGET(UiText.Res(Res.string.settings_full_schedule_widget_title)),
    QR_WIDGET(UiText.Res(Res.string.settings_group_qr_widget)),
    APPEARANCE(UiText.Res(Res.string.settings_appearance_title)),
    HOME(UiText.Res(Res.string.settings_group_home)),
    SCHEDULE(UiText.Res(Res.string.settings_group_schedule)),
    RECORDBOOK(UiText.Res(CoreRes.string.title_recordbook)),
    SPORT(UiText.Res(Res.string.settings_group_sport)),
    MAINTENANCE(UiText.Res(Res.string.settings_group_maintenance));

    companion object {
        const val ARGUMENT = SettingsScreenArgs.PAGE

        fun fromArgument(value: String?): SettingsPage =
            if (value == "SCHEDULE_WIDGETS") COMPACT_SCHEDULE_WIDGET
            else entries.firstOrNull { it.name == value } ?: ROOT
    }
}
