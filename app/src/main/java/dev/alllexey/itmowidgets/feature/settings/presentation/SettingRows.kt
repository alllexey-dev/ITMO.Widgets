package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.R
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

    /** The whole row is the button: it opens the system page, and the row leaves once Android lets the app work. */
    fun backgroundWork() = SettingItem.Action(
        id = SettingRowId.BACKGROUND_WORK,
        title = UiText.Resource(R.string.settings_background_work_title),
        description = UiText.Resource(R.string.background_work_hint),
        trailingIconRes = R.drawable.ic_open_in_new
    )
}
