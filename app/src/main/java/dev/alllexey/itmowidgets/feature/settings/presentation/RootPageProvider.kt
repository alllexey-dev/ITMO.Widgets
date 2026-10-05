package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
import javax.inject.Inject

/** The catalogue: one row per detail page, plus the notification permission. */
class RootPageProvider @Inject constructor() : SettingsPageProvider {

    override val pages = setOf(SettingsPage.ROOT)

    override val rows = setOf(SettingRowId.NOTIFICATIONS)

    override fun sections(page: SettingsPage, state: SettingsPageState) = listOf(
        SettingSection(
            title = UiText.Resource(R.string.settings_group_access),
            items = listOf(
                SettingRows.navigation(
                    SettingsPage.SERVICES,
                    value = UiText.Resource(
                        if (state.local.customServicesEnabled) {
                            R.string.settings_services_enabled
                        } else {
                            R.string.settings_services_disabled
                        }
                    )
                ),
                SettingRows.navigation(SettingsPage.PRIVACY),
                SettingItem.Action(
                    id = SettingRowId.NOTIFICATIONS,
                    title = UiText.Resource(R.string.settings_notifications_title),
                    value = UiText.Resource(
                        when (state.notificationsGranted) {
                            true -> R.string.settings_notifications_allowed
                            false -> R.string.settings_notifications_blocked
                            null -> R.string.settings_notifications_checking
                        }
                    ),
                    trailingIcon = AppIcon.CHEVRON_RIGHT
                )
            )
        ),
        SettingSection(
            title = UiText.Resource(R.string.settings_group_widgets),
            items = listOf(
                SettingRows.navigation(SettingsPage.COMPACT_SCHEDULE_WIDGET),
                SettingRows.navigation(SettingsPage.FULL_SCHEDULE_WIDGET),
                SettingRows.navigation(
                    SettingsPage.QR_WIDGET,
                    title = UiText.Resource(R.string.settings_qr_short_title)
                )
            )
        ),
        SettingSection(
            title = UiText.Resource(R.string.me_group_app),
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
