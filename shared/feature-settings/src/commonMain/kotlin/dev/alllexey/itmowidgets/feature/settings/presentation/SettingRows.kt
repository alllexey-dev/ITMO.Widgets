package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.feature.settings.Res
import dev.alllexey.itmowidgets.shared.feature.settings.background_work_hint
import dev.alllexey.itmowidgets.shared.feature.settings.settings_background_work_title

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
        title = UiText.Res(Res.string.settings_background_work_title),
        description = UiText.Res(Res.string.background_work_hint),
        trailingIcon = AppIcon.OPEN_IN_NEW
    )
}
