package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.recordbook.MarkTracking
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.feature.settings.Res
import dev.alllexey.itmowidgets.shared.feature.settings.settings_marks_bars_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_marks_footer
import dev.alllexey.itmowidgets.shared.feature.settings.settings_marks_myitmo_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_marks_notifications_off
import dev.alllexey.itmowidgets.shared.feature.settings.settings_marks_sheets_title

/** The mark checks of My ITMO, BARS and Google Sheets. */
class RecordbookPageProvider(
    private val markTracking: MarkTracking
) : SettingsPageProvider {

    override val pages = setOf(SettingsPage.RECORDBOOK)

    override val rows = setOf(SettingRowId.MYITMO_MARKS, SettingRowId.BARS_MARKS, SettingRowId.SHEET_MARKS)

    override fun sections(page: SettingsPage, state: SettingsPageState): List<SettingSection> {
        val local = state.local
        return listOf(
            SettingSection(
                title = null,
                items = listOfNotNull(
                    SettingItem.Toggle(
                        id = SettingRowId.MYITMO_MARKS,
                        title = UiText.Res(Res.string.settings_marks_myitmo_title),
                        checked = local.myItmoMarksEnabled
                    ),
                    // BARS appears with the account's first BARS answer; before it there is nothing to check.
                    local.barsMarksEnabled?.let { enabled ->
                        SettingItem.Toggle(
                            id = SettingRowId.BARS_MARKS,
                            title = UiText.Res(Res.string.settings_marks_bars_title),
                            checked = enabled
                        )
                    },
                    SettingItem.Toggle(
                        id = SettingRowId.SHEET_MARKS,
                        title = UiText.Res(Res.string.settings_marks_sheets_title),
                        checked = local.sheetMarksEnabled
                    ),
                    SettingRows.backgroundWork().takeIf {
                        state.backgroundWorkRestricted &&
                            (local.myItmoMarksEnabled || local.barsMarksEnabled == true || local.sheetMarksEnabled)
                    }
                ),
                footer = UiText.Res(
                    if (state.notificationsGranted == false) Res.string.settings_marks_notifications_off
                    else Res.string.settings_marks_footer
                )
            )
        )
    }

    override fun onToggleChanged(scope: SettingsPageScope, id: SettingRowId, checked: Boolean) {
        when (id) {
            SettingRowId.MYITMO_MARKS -> scope.updateBackgroundCheck(checked) { markTracking.setMyItmoEnabled(checked) }
            SettingRowId.BARS_MARKS -> scope.updateBackgroundCheck(checked) { markTracking.setBarsEnabled(checked) }
            SettingRowId.SHEET_MARKS -> scope.updateBackgroundCheck(checked) { markTracking.setSheetsEnabled(checked) }
            else -> Unit
        }
    }
}
