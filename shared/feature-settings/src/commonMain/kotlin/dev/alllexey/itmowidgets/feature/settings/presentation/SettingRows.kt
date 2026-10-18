package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.text.UiText

/** Rows more than one page shows. */
internal object SettingRows {

    fun navigation(
        page: SettingsPage,
        title: UiText = page.title,
        value: UiText? = null
    ) = SettingItem.Navigation(
        id = SettingRowId.navigation(page),
        title = title,
        value = value,
        page = page
    )
}
