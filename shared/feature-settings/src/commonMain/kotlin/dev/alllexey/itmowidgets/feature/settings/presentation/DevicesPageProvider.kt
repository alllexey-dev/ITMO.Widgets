package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.feature.settings.SettingsStrings

/** «Устройства» of «Сервер ITMO.Widgets»: the web sign-in by QR code and the devices with access to the account. */
class DevicesPageProvider : SettingsPageProvider {

    override val pages = setOf(SettingsPage.DEVICES)

    override val rows = setOf(SettingRowId.PAIR_DEVICE, SettingRowId.CONNECTED_DEVICES)

    override fun sections(page: SettingsPage, state: SettingsPageState) = listOf(
        SettingSection(
            title = null,
            items = listOf(
                SettingItem.Action(
                    id = SettingRowId.PAIR_DEVICE,
                    title = UiText.Res(SettingsStrings.connection_pair),
                    description = UiText.Res(SettingsStrings.connection_pair_text),
                    trailingIcon = AppIcon.CHEVRON_RIGHT
                ),
                SettingItem.Action(
                    id = SettingRowId.CONNECTED_DEVICES,
                    title = UiText.Res(SettingsStrings.connection_devices_list),
                    description = UiText.Res(SettingsStrings.connection_devices_list_text),
                    trailingIcon = AppIcon.CHEVRON_RIGHT
                )
            )
        )
    )

    override fun onAction(scope: SettingsPageScope, id: SettingRowId) {
        when (id) {
            SettingRowId.PAIR_DEVICE -> scope.send(SettingsEvent.OpenRoute(AppRoutes.WebLogin()))
            SettingRowId.CONNECTED_DEVICES -> scope.send(SettingsEvent.OpenRoute(AppRoutes.Connections))
            else -> Unit
        }
    }
}
