package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetFormat
import dev.alllexey.itmowidgets.core.settings.WidgetFormat
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.domain.QrTileAddResult
import dev.alllexey.itmowidgets.feature.settings.domain.QuickSettingsTileAccess
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.settings.SettingsStrings
import kotlinx.coroutines.CancellationException

/**
 * The widgets gallery (`Виджеты`): a card per format and the settings of each format in its sheet, applied at once to
 * every widget of the format; below the cards `Виджеты в цвет темы`. The QR tile, the spoiler animation and the custom
 * spoiler image are listed where the platform offers them.
 */
class WidgetsPageProvider(
    private val repository: SettingsRepository,
    private val tileAccess: QuickSettingsTileAccess,
    private val capabilities: PlatformCapabilities = EveryPlatformCapability
) : SettingsPageProvider {

    override val pages = setOf(SettingsPage.WIDGETS)

    override val rows = setOf(
        SettingRowId.COMPACT_WIDGET_NEXT_LESSON_EARLY,
        SettingRowId.COMPACT_WIDGET_TEXT_SIZE,
        SettingRowId.FULL_WIDGET_SHOW_TEACHER,
        SettingRowId.FULL_WIDGET_HIDE_PAST,
        SettingRowId.FULL_WIDGET_SHOW_TOMORROW,
        SettingRowId.FULL_WIDGET_TEXT_SIZE,
        SettingRowId.QR_TILE,
        SettingRowId.QR_DYNAMIC_COLORS,
        SettingRowId.QR_SPOILER,
        SettingRowId.QR_ANIMATION,
        SettingRowId.QR_CUSTOM_IMAGE,
        SettingRowId.QR_RESET_IMAGE,
        SettingRowId.WIDGETS_FOLLOW_THEME
    )

    override fun sections(page: SettingsPage, state: SettingsPageState): List<SettingSection> = rows().page(state)

    override fun gallery(page: SettingsPage, state: SettingsPageState): WidgetGallery = rows().gallery(state)

    private fun rows() = WidgetGalleryRows(capabilities, canAddTile = tileAccess.canRequestAdd())

    override fun onToggleChanged(scope: SettingsPageScope, id: SettingRowId, checked: Boolean) {
        when (id) {
            SettingRowId.COMPACT_WIDGET_NEXT_LESSON_EARLY -> scope.updateWidgetSetting {
                repository.setCompactWidgetNextLessonEarlyEnabled(checked)
            }
            SettingRowId.FULL_WIDGET_SHOW_TEACHER -> scope.updateWidgetSetting {
                repository.setFullWidgetTeacherHidden(!checked)
            }
            SettingRowId.FULL_WIDGET_HIDE_PAST -> scope.updateWidgetSetting {
                repository.setFullWidgetPastLessonsHidden(checked)
            }
            SettingRowId.FULL_WIDGET_SHOW_TOMORROW -> scope.updateWidgetSetting {
                repository.setFullWidgetTomorrowEnabled(checked)
            }
            SettingRowId.QR_DYNAMIC_COLORS -> scope.updateWidgetSetting {
                repository.setQrDynamicColorsEnabled(checked)
            }
            SettingRowId.QR_SPOILER -> scope.updateWidgetSetting {
                repository.setQrSpoilerEnabled(checked)
            }
            SettingRowId.WIDGETS_FOLLOW_THEME -> scope.updateWidgetSetting { repository.setWidgetsFollowTheme(checked) }
            else -> Unit
        }
    }

    override fun onChoiceChanged(scope: SettingsPageScope, id: SettingRowId, optionKey: String) {
        when (id) {
            SettingRowId.QR_ANIMATION -> {
                val animation = QrAnimationType.entries.firstOrNull { it.name == optionKey } ?: return
                scope.updateWidgetSetting { repository.setQrAnimationType(animation) }
            }
            SettingRowId.COMPACT_WIDGET_TEXT_SIZE, SettingRowId.FULL_WIDGET_TEXT_SIZE -> {
                val size = WidgetTextSize.entries.firstOrNull { it.name == optionKey } ?: return
                scope.updateWidgetSetting {
                    if (id == SettingRowId.COMPACT_WIDGET_TEXT_SIZE) {
                        repository.setCompactWidgetTextSize(size)
                    } else {
                        repository.setFullWidgetTextSize(size)
                    }
                }
            }
            else -> Unit
        }
    }

    override fun onAction(scope: SettingsPageScope, id: SettingRowId) {
        when (id) {
            SettingRowId.QR_CUSTOM_IMAGE -> scope.send(SettingsEvent.ChooseCustomSpoiler)
            SettingRowId.QR_RESET_IMAGE -> scope.send(SettingsEvent.ResetCustomSpoiler)
            SettingRowId.QR_TILE -> scope.send(SettingsEvent.RequestQrTile)
            else -> Unit
        }
    }

    /** The system's answer to the add request: the row leaves once the tile is known to be in the quick settings. */
    fun onQrTileResult(scope: SettingsPageScope, result: QrTileAddResult) {
        when (result) {
            QrTileAddResult.ADDED -> rememberQrTileAdded(scope)
            QrTileAddResult.ALREADY_ADDED ->
                rememberQrTileAdded(scope, UiText.Res(SettingsStrings.settings_qr_tile_already_added))
            QrTileAddResult.NOT_ADDED, QrTileAddResult.IN_PROGRESS -> Unit
            QrTileAddResult.FAILED ->
                scope.send(SettingsEvent.ShowMessage(UiText.Res(SettingsStrings.settings_qr_tile_failed)))
        }
    }

    private fun rememberQrTileAdded(scope: SettingsPageScope, message: UiText? = null) {
        scope.launch {
            try {
                repository.setQrTileAdded(true)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                scope.emit(SettingsEvent.ShowError(AppError.Unknown(error)))
                return@launch
            }
            message?.let { scope.emit(SettingsEvent.ShowMessage(it)) }
        }
    }
}

/** The gallery and its sheets' rows, built from the page state; [canAddTile] is the system's answer of the moment. */
internal class WidgetGalleryRows(private val capabilities: PlatformCapabilities, private val canAddTile: Boolean) {

    fun page(state: SettingsPageState): List<SettingSection> = listOf(
        SettingSection(
            title = null,
            items = listOf(
                SettingItem.Toggle(
                    id = SettingRowId.WIDGETS_FOLLOW_THEME,
                    title = UiText.Res(SettingsStrings.settings_widgets_theme_title),
                    checked = state.local.widgetsFollowTheme
                )
            ),
            footer = UiText.Res(SettingsStrings.settings_widgets_note)
        )
    )

    fun gallery(state: SettingsPageState): WidgetGallery {
        val local = state.local
        val theme = local.theme.takeIf { local.widgetsFollowTheme }
        val sample = UiText.Res(SettingsStrings.settings_widget_sample_note)
        return WidgetGallery(
            subtitle = placedWidgetsSummary(state.placedWidgets, capabilities),
            cards = listOf(
                card(
                    state, WidgetFormat.NEXT, SettingsStrings.settings_widget_next_title, SettingsStrings.settings_widget_next_description,
                    WidgetPreviewSettings.Schedule(local.scheduleWidget, ScheduleWidgetFormat.COMPACT, theme), nextSections(local), sample
                ),
                card(
                    state, WidgetFormat.DAY, SettingsStrings.settings_widget_day_title, SettingsStrings.settings_widget_day_description,
                    WidgetPreviewSettings.Schedule(local.scheduleWidget, ScheduleWidgetFormat.FULL, theme), daySections(local), sample
                ),
                card(
                    state, WidgetFormat.LIST, SettingsStrings.settings_widget_list_title, SettingsStrings.settings_widget_list_description,
                    WidgetPreviewSettings.Schedule(local.scheduleWidget, ScheduleWidgetFormat.LIST, theme), daySections(local),
                    UiText.Res(SettingsStrings.settings_widget_list_note)
                ),
                card(
                    state, WidgetFormat.SPORT, SettingsStrings.settings_widget_sport_title, SettingsStrings.settings_widget_sport_description,
                    WidgetPreviewSettings.Sport(theme), emptyList(), null
                ),
                card(
                    state, WidgetFormat.PASS, SettingsStrings.settings_widget_pass_title,
                    if (local.qrWidget.spoilerEnabled) SettingsStrings.settings_widget_pass_description
                    else SettingsStrings.settings_widget_pass_open_description,
                    WidgetPreviewSettings.Qr(local.qrWidget, theme), qrSections(state),
                    UiText.Res(SettingsStrings.settings_widget_apply_note)
                ),
            ),
            howToAdd = !capabilities.pinWidgets,
            lockScreen = capabilities.lockScreenWidgets,
        )
    }

    private fun card(
        state: SettingsPageState,
        format: WidgetFormat,
        title: String,
        description: String,
        preview: WidgetPreviewSettings,
        settings: List<SettingSection>,
        note: UiText?,
    ): WidgetCard {
        val placed = (state.placedWidgets[format] ?: 0) > 0
        return WidgetCard(
            format = format,
            title = UiText.Res(title),
            description = UiText.Res(description),
            beta = format in BETA,
            preview = preview,
            action = when {
                placed -> WidgetCardAction.ON_SCREEN
                capabilities.pinWidgets -> WidgetCardAction.ADD
                else -> WidgetCardAction.NONE
            },
            settings = settings,
            settingsNote = note,
        )
    }

    private fun nextSections(local: LocalSettings) = listOf(
        SettingSection(
            title = null,
            items = listOf(
                SettingItem.Toggle(
                    id = SettingRowId.COMPACT_WIDGET_NEXT_LESSON_EARLY,
                    title = UiText.Res(CoreStrings.settings_widget_next_early_title),
                    description = UiText.Res(CoreStrings.settings_widget_next_early_description),
                    checked = local.scheduleWidget.compact.showNextLessonEarly
                )
            )
        ),
        textSizeSection(SettingRowId.COMPACT_WIDGET_TEXT_SIZE, local.scheduleWidget.compact.textSize)
    )

    private fun daySections(local: LocalSettings) = listOf(
        SettingSection(
            title = null,
            items = listOf(
                SettingItem.Toggle(
                    id = SettingRowId.FULL_WIDGET_HIDE_PAST,
                    title = UiText.Res(CoreStrings.settings_widget_hide_past_title),
                    checked = local.scheduleWidget.full.hidePastLessons
                ),
                SettingItem.Toggle(
                    id = SettingRowId.FULL_WIDGET_SHOW_TEACHER,
                    title = UiText.Res(SettingsStrings.settings_widget_show_teacher_title),
                    checked = !local.scheduleWidget.full.hideTeacher
                ),
                SettingItem.Toggle(
                    id = SettingRowId.FULL_WIDGET_SHOW_TOMORROW,
                    title = UiText.Res(CoreStrings.settings_widget_tomorrow_title),
                    description = UiText.Res(CoreStrings.settings_widget_tomorrow_description),
                    checked = local.scheduleWidget.full.showTomorrowWhenTodayIsOver
                )
            )
        ),
        textSizeSection(SettingRowId.FULL_WIDGET_TEXT_SIZE, local.scheduleWidget.full.textSize)
    )

    private fun qrSections(state: SettingsPageState): List<SettingSection> {
        val qr = state.local.qrWidget
        return listOfNotNull(
            SettingSection(
                title = null,
                items = listOf(
                    SettingItem.Toggle(
                        id = SettingRowId.QR_DYNAMIC_COLORS,
                        title = UiText.Res(CoreStrings.settings_qr_dynamic_colors_title),
                        checked = qr.dynamicColors
                    ),
                    SettingItem.Toggle(
                        id = SettingRowId.QR_SPOILER,
                        title = UiText.Res(CoreStrings.settings_qr_spoiler_title),
                        description = UiText.Res(CoreStrings.settings_qr_spoiler_description),
                        checked = qr.spoilerEnabled
                    )
                )
            ),
            spoilerSection(state),
            qrTileSection(state.local)
        )
    }

    /** The spoiler's animation and image; null when the platform offers neither. */
    private fun spoilerSection(state: SettingsPageState): SettingSection? {
        val qr = state.local.qrWidget
        val items = listOfNotNull(
            SettingItem.Choice(
                id = SettingRowId.QR_ANIMATION,
                title = UiText.Res(SettingsStrings.settings_qr_animation_title),
                value = qr.animationType.label(),
                options = QrAnimationType.entries.map { animation ->
                    ChoiceOption(animation.name, animation.label())
                },
                selectedOptionKey = qr.animationType.name,
                enabled = qr.spoilerEnabled
            ).takeIf { capabilities.qrWidgetAnimation },
            SettingItem.Action(
                id = SettingRowId.QR_CUSTOM_IMAGE,
                title = UiText.Res(CoreStrings.settings_qr_custom_image_title),
                value = UiText.Res(
                    if (state.hasCustomSpoiler) {
                        CoreStrings.settings_qr_custom_image_selected
                    } else {
                        CoreStrings.settings_qr_custom_image_default
                    }
                ),
                trailingIcon = AppIcon.CHEVRON_RIGHT,
                enabled = qr.spoilerEnabled && !state.imageBusy
            ).takeIf { capabilities.qrCustomSpoiler },
            SettingItem.Action(
                id = SettingRowId.QR_RESET_IMAGE,
                title = UiText.Res(SettingsStrings.settings_qr_reset_image_title),
                enabled = qr.spoilerEnabled && state.hasCustomSpoiler && !state.imageBusy
            ).takeIf { capabilities.qrCustomSpoiler }
        )
        if (items.isEmpty()) return null
        return SettingSection(title = UiText.Res(SettingsStrings.settings_group_spoiler), items = items)
    }

    /** Android 13+ asks the system to add the tile; the row leaves once the tile is known to be added. */
    private fun qrTileSection(local: LocalSettings): SettingSection? {
        if (!capabilities.quickSettingsTile || !canAddTile || local.qrTileAdded) return null
        return SettingSection(
            title = null,
            items = listOf(
                SettingItem.Action(
                    id = SettingRowId.QR_TILE,
                    title = UiText.Res(SettingsStrings.settings_qr_tile_title),
                    description = UiText.Res(SettingsStrings.settings_qr_tile_description)
                )
            )
        )
    }

    /** `Размер текста` as its own titled group: the sheet draws its options as a segmented choice. */
    private fun textSizeSection(id: SettingRowId, size: WidgetTextSize) = SettingSection(
        title = UiText.Res(CoreStrings.settings_widget_text_size_title),
        items = listOf(
            SettingItem.Choice(
                id = id,
                title = UiText.Res(CoreStrings.settings_widget_text_size_title),
                value = size.label(),
                options = WidgetTextSize.entries.map { ChoiceOption(it.name, it.label()) },
                selectedOptionKey = size.name
            )
        )
    )

    private fun WidgetTextSize.label() = UiText.Res(
        when (this) {
            WidgetTextSize.NORMAL -> CoreStrings.settings_widget_text_size_normal
            WidgetTextSize.LARGE -> CoreStrings.settings_widget_text_size_large
            WidgetTextSize.EXTRA_LARGE -> CoreStrings.settings_widget_text_size_extra_large
        }
    )

    private fun QrAnimationType.label() = UiText.Res(
        when (this) {
            QrAnimationType.CIRCLE -> SettingsStrings.settings_qr_animation_circle
            QrAnimationType.FADE -> SettingsStrings.settings_qr_animation_fade
            QrAnimationType.NONE -> SettingsStrings.settings_qr_animation_none
        }
    )

    private companion object {
        /** The widgets of the UX overhaul, still being refined; the classic list and the pass are not. */
        val BETA = setOf(WidgetFormat.NEXT, WidgetFormat.DAY, WidgetFormat.SPORT)
    }
}

/** `2 на главном экране` (iOS `на экране «Домой»`), the gallery's subtitle and the root's value; null while none is placed. */
internal fun placedWidgetsSummary(placed: Map<WidgetFormat, Int>, capabilities: PlatformCapabilities): UiText? {
    val count = placed.values.sum()
    if (count == 0) return null
    val key = if (capabilities.pinWidgets) SettingsStrings.settings_widgets_placed else SettingsStrings.settings_widgets_placed_home
    return UiText.Res(key, listOf(count))
}
