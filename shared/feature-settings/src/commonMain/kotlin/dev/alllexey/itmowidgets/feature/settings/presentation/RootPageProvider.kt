package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.core.me_group_app
import dev.alllexey.itmowidgets.shared.feature.settings.Res
import dev.alllexey.itmowidgets.shared.feature.settings.settings_group_access
import dev.alllexey.itmowidgets.shared.feature.settings.settings_group_widgets
import dev.alllexey.itmowidgets.shared.feature.settings.settings_notifications_allowed
import dev.alllexey.itmowidgets.shared.feature.settings.settings_notifications_blocked
import dev.alllexey.itmowidgets.shared.feature.settings.settings_notifications_checking
import dev.alllexey.itmowidgets.shared.feature.settings.settings_notifications_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_qr_short_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_services_disabled
import dev.alllexey.itmowidgets.shared.feature.settings.settings_services_enabled

/** The catalogue: one row per detail page, plus the notification permission. */
class RootPageProvider() : SettingsPageProvider {

    override val pages = setOf(SettingsPage.ROOT)

    override val rows = setOf(SettingRowId.NOTIFICATIONS)

    override fun sections(page: SettingsPage, state: SettingsPageState) = listOf(
        SettingSection(
            title = UiText.Res(Res.string.settings_group_access),
            items = listOf(
                SettingRows.navigation(
                    SettingsPage.SERVICES,
                    value = UiText.Res(
                        if (state.local.customServicesEnabled) {
                            Res.string.settings_services_enabled
                        } else {
                            Res.string.settings_services_disabled
                        }
                    )
                ),
                SettingRows.navigation(SettingsPage.PRIVACY),
                SettingItem.Action(
                    id = SettingRowId.NOTIFICATIONS,
                    title = UiText.Res(Res.string.settings_notifications_title),
                    value = UiText.Res(
                        when (state.notificationsGranted) {
                            true -> Res.string.settings_notifications_allowed
                            false -> Res.string.settings_notifications_blocked
                            null -> Res.string.settings_notifications_checking
                        }
                    ),
                    trailingIcon = AppIcon.CHEVRON_RIGHT
                )
            )
        ),
        SettingSection(
            title = UiText.Res(Res.string.settings_group_widgets),
            items = listOf(
                SettingRows.navigation(SettingsPage.COMPACT_SCHEDULE_WIDGET),
                SettingRows.navigation(SettingsPage.FULL_SCHEDULE_WIDGET),
                SettingRows.navigation(
                    SettingsPage.QR_WIDGET,
                    title = UiText.Res(Res.string.settings_qr_short_title)
                )
            )
        ),
        SettingSection(
            title = UiText.Res(CoreRes.string.me_group_app),
            items = listOf(
                SettingRows.navigation(SettingsPage.HOME),
                SettingRows.navigation(SettingsPage.SCHEDULE),
                SettingRows.navigation(SettingsPage.RECORDBOOK),
                SettingRows.navigation(SettingsPage.SPORT),
                SettingRows.navigation(SettingsPage.MAINTENANCE)
            )
        )
    )

    override fun onAction(scope: SettingsPageScope, id: SettingRowId) {
        if (id == SettingRowId.NOTIFICATIONS) scope.send(SettingsEvent.OpenNotificationSettings)
    }
}
