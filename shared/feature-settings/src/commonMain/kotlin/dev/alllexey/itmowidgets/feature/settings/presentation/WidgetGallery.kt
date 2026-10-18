package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.settings.WidgetFormat
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.core.text.UiText

/**
 * `Виджеты` (spec C4): a card per [WidgetFormat] with its live preview, then the gallery's own rows. [subtitle] counts
 * the placed widgets once the screen has counted some; [howToAdd] is iOS, where no app can place a widget, and
 * [lockScreen] lists its Lock Screen widgets and the pass Control.
 */
data class WidgetGallery(
    val subtitle: UiText?,
    val cards: List<WidgetCard>,
    val howToAdd: Boolean,
    val lockScreen: Boolean,
)

/**
 * One format: [preview] draws the real widget on sample data, [action] is `Добавить` or `на экране`, and the sheet
 * behind the settings button shows the preview again above [settings] (no button when empty) and [settingsNote].
 * [beta] marks the widgets of the UX overhaul with `Бета` while they are refined.
 */
data class WidgetCard(
    val format: WidgetFormat,
    val title: UiText,
    val description: UiText,
    val beta: Boolean,
    val preview: WidgetPreviewSettings,
    val action: WidgetCardAction,
    val settings: List<SettingSection>,
    val settingsNote: UiText?,
)

/** [ADD] pins a widget through the launcher; [ON_SCREEN] says one is placed; [NONE] where the app cannot place one. */
enum class WidgetCardAction { ADD, ON_SCREEN, NONE }
