package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.shared.feature.settings.Res
import dev.alllexey.itmowidgets.shared.feature.settings.settings_sport_teacher_filter_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_sport_time_filter_title

/** The filters the sport screen shows; a switch on means the filter is shown. */
class SportPageProvider(
    private val repository: SettingsRepository
) : SettingsPageProvider {

    override val pages = setOf(SettingsPage.SPORT)

    override val rows = setOf(SettingRowId.SPORT_TEACHER_FILTER, SettingRowId.SPORT_TIME_FILTER)

    override fun sections(page: SettingsPage, state: SettingsPageState) = listOf(
        SettingSection(
            title = null,
            items = listOf(
                SettingItem.Toggle(
                    id = SettingRowId.SPORT_TEACHER_FILTER,
                    title = UiText.Res(Res.string.settings_sport_teacher_filter_title),
                    checked = !state.local.sport.hideTeacherSelector
                ),
                SettingItem.Toggle(
                    id = SettingRowId.SPORT_TIME_FILTER,
                    title = UiText.Res(Res.string.settings_sport_time_filter_title),
                    checked = !state.local.sport.hideTimeSelector
                )
            )
        )
    )

    override fun onToggleChanged(scope: SettingsPageScope, id: SettingRowId, checked: Boolean) {
        when (id) {
            SettingRowId.SPORT_TEACHER_FILTER -> scope.updateLocalSetting { repository.setTeacherSelectorHidden(!checked) }
            SettingRowId.SPORT_TIME_FILTER -> scope.updateLocalSetting { repository.setTimeSelectorHidden(!checked) }
            else -> Unit
        }
    }
}
