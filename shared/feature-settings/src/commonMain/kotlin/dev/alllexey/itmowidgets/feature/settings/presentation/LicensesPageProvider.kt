package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.feature.settings.SettingsStrings

/**
 * `Лицензии`: the source of the campus plans, which the map no longer captions, and the licence texts the bundled
 * MapLibre Native (BSD 2-Clause) and the map's Noto Sans labels (SIL OFL 1.1) ask to reproduce.
 */
class LicensesPageProvider : SettingsPageProvider {

    override val pages = setOf(SettingsPage.LICENSES)

    override val rows = setOf(SettingRowId.LICENSE_PLANS, SettingRowId.LICENSE_MAPLIBRE, SettingRowId.LICENSE_NOTO)

    override fun sections(page: SettingsPage, state: SettingsPageState) = listOf(
        SettingSection(
            title = UiText.Res(SettingsStrings.settings_licenses_data_title),
            items = listOf(
                SettingItem.Info(
                    id = SettingRowId.LICENSE_PLANS,
                    title = UiText.Res(SettingsStrings.settings_licenses_plans_title),
                    value = UiText.Res(SettingsStrings.settings_licenses_plans_value),
                ),
            ),
            footer = UiText.Res(SettingsStrings.settings_licenses_plans_note),
        ),
        SettingSection(
            title = UiText.Dynamic(MAPLIBRE),
            items = listOf(
                SettingItem.Info(
                    id = SettingRowId.LICENSE_MAPLIBRE,
                    title = UiText.Res(SettingsStrings.settings_licenses_license),
                    value = UiText.Dynamic(BSD_2),
                ),
            ),
            footer = UiText.Res(SettingsStrings.settings_licenses_maplibre_text),
        ),
        SettingSection(
            title = UiText.Dynamic(NOTO),
            items = listOf(
                SettingItem.Info(
                    id = SettingRowId.LICENSE_NOTO,
                    title = UiText.Res(SettingsStrings.settings_licenses_license),
                    value = UiText.Dynamic(OFL),
                ),
            ),
            footer = UiText.Res(SettingsStrings.settings_licenses_noto_text),
        ),
    )

    override fun onAction(scope: SettingsPageScope, id: SettingRowId) = Unit

    private companion object {
        const val MAPLIBRE = "MapLibre Native"
        const val BSD_2 = "BSD 2-Clause"
        const val NOTO = "Noto Sans"
        const val OFL = "SIL OFL 1.1"
    }
}
