package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetFormat
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.domain.QrTileAddResult
import dev.alllexey.itmowidgets.feature.settings.domain.QuickSettingsTileAccess
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.core.settings_qr_custom_image_default
import dev.alllexey.itmowidgets.shared.core.settings_qr_custom_image_selected
import dev.alllexey.itmowidgets.shared.core.settings_qr_custom_image_title
import dev.alllexey.itmowidgets.shared.core.settings_qr_dynamic_colors_title
import dev.alllexey.itmowidgets.shared.core.settings_qr_spoiler_description
import dev.alllexey.itmowidgets.shared.core.settings_qr_spoiler_title
import dev.alllexey.itmowidgets.shared.core.settings_widget_hide_past_title
import dev.alllexey.itmowidgets.shared.core.settings_widget_hide_teacher_title
import dev.alllexey.itmowidgets.shared.core.settings_widget_next_early_description
import dev.alllexey.itmowidgets.shared.core.settings_widget_next_early_title
import dev.alllexey.itmowidgets.shared.core.settings_widget_text_size_extra_large
import dev.alllexey.itmowidgets.shared.core.settings_widget_text_size_large
import dev.alllexey.itmowidgets.shared.core.settings_widget_text_size_normal
import dev.alllexey.itmowidgets.shared.core.settings_widget_text_size_title
import dev.alllexey.itmowidgets.shared.core.settings_widget_tomorrow_description
import dev.alllexey.itmowidgets.shared.core.settings_widget_tomorrow_title
import dev.alllexey.itmowidgets.shared.feature.settings.Res
import dev.alllexey.itmowidgets.shared.feature.settings.settings_group_spoiler
import dev.alllexey.itmowidgets.shared.feature.settings.settings_qr_animation_circle
import dev.alllexey.itmowidgets.shared.feature.settings.settings_qr_animation_fade
import dev.alllexey.itmowidgets.shared.feature.settings.settings_qr_animation_none
import dev.alllexey.itmowidgets.shared.feature.settings.settings_qr_animation_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_qr_reset_image_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_qr_tile_already_added
import dev.alllexey.itmowidgets.shared.feature.settings.settings_qr_tile_description
import dev.alllexey.itmowidgets.shared.feature.settings.settings_qr_tile_failed
import dev.alllexey.itmowidgets.shared.feature.settings.settings_qr_tile_title
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.StringResource

/**
 * The compact and full schedule widgets and the QR pass widget with its tile; each page draws its widget above. The
 * tile, the spoiler animation and the custom spoiler image are listed where the platform offers them.
 */
class WidgetsPageProvider(
    private val repository: SettingsRepository,
    private val tileAccess: QuickSettingsTileAccess,
    private val capabilities: PlatformCapabilities = EveryPlatformCapability
) : SettingsPageProvider {

    override val pages = setOf(
        SettingsPage.COMPACT_SCHEDULE_WIDGET,
        SettingsPage.FULL_SCHEDULE_WIDGET,
        SettingsPage.QR_WIDGET
    )

    override val rows = setOf(
        SettingRowId.COMPACT_WIDGET_NEXT_LESSON_EARLY,
        SettingRowId.COMPACT_WIDGET_HIDE_TEACHER,
        SettingRowId.COMPACT_WIDGET_TEXT_SIZE,
        SettingRowId.FULL_WIDGET_HIDE_TEACHER,
        SettingRowId.FULL_WIDGET_HIDE_PAST,
        SettingRowId.FULL_WIDGET_SHOW_TOMORROW,
        SettingRowId.FULL_WIDGET_TEXT_SIZE,
        SettingRowId.QR_TILE,
        SettingRowId.QR_DYNAMIC_COLORS,
        SettingRowId.QR_SPOILER,
        SettingRowId.QR_ANIMATION,
        SettingRowId.QR_CUSTOM_IMAGE,
        SettingRowId.QR_RESET_IMAGE
    )

    override fun sections(page: SettingsPage, state: SettingsPageState): List<SettingSection> = when (page) {
        SettingsPage.COMPACT_SCHEDULE_WIDGET -> compactSections(state.local)
        SettingsPage.FULL_SCHEDULE_WIDGET -> fullSections(state.local)
        else -> qrSections(state)
    }

    override fun preview(page: SettingsPage, local: LocalSettings): WidgetPreviewSettings? {
        val theme = local.theme.takeIf { local.widgetsFollowTheme }
        return when (page) {
            SettingsPage.QR_WIDGET -> WidgetPreviewSettings.Qr(local.qrWidget, theme)
            SettingsPage.COMPACT_SCHEDULE_WIDGET ->
                WidgetPreviewSettings.Schedule(local.scheduleWidget, ScheduleWidgetFormat.COMPACT, theme)
            SettingsPage.FULL_SCHEDULE_WIDGET ->
                WidgetPreviewSettings.Schedule(local.scheduleWidget, ScheduleWidgetFormat.FULL, theme)
            else -> null
        }
    }

    override fun onToggleChanged(scope: SettingsPageScope, id: SettingRowId, checked: Boolean) {
        when (id) {
            SettingRowId.COMPACT_WIDGET_NEXT_LESSON_EARLY -> scope.updateWidgetSetting {
                repository.setCompactWidgetNextLessonEarlyEnabled(checked)
            }
            SettingRowId.COMPACT_WIDGET_HIDE_TEACHER -> scope.updateWidgetSetting {
                repository.setCompactWidgetTeacherHidden(checked)
            }
            SettingRowId.FULL_WIDGET_HIDE_TEACHER -> scope.updateWidgetSetting {
                repository.setFullWidgetTeacherHidden(checked)
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
            QrTileAddResult.ALREADY_ADDED -> rememberQrTileAdded(scope, Res.string.settings_qr_tile_already_added)
            QrTileAddResult.NOT_ADDED, QrTileAddResult.IN_PROGRESS -> Unit
            QrTileAddResult.FAILED ->
                scope.send(SettingsEvent.ShowMessage(UiText.Res(Res.string.settings_qr_tile_failed)))
        }
    }

    private fun rememberQrTileAdded(scope: SettingsPageScope, message: StringResource? = null) {
        scope.launch {
            try {
                repository.setQrTileAdded(true)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                scope.emit(SettingsEvent.ShowError(AppError.Unknown(error)))
                return@launch
            }
            message?.let { scope.emit(SettingsEvent.ShowMessage(UiText.Res(it))) }
        }
    }

    private fun compactSections(local: LocalSettings) = listOf(
        SettingSection(
            title = null,
            items = listOf(
                SettingItem.Toggle(
                    id = SettingRowId.COMPACT_WIDGET_NEXT_LESSON_EARLY,
                    title = UiText.Res(CoreRes.string.settings_widget_next_early_title),
                    description = UiText.Res(CoreRes.string.settings_widget_next_early_description),
                    checked = local.scheduleWidget.compact.showNextLessonEarly
                ),
                SettingItem.Toggle(
                    id = SettingRowId.COMPACT_WIDGET_HIDE_TEACHER,
                    title = UiText.Res(CoreRes.string.settings_widget_hide_teacher_title),
                    checked = local.scheduleWidget.compact.hideTeacher
                ),
                textSizeChoice(SettingRowId.COMPACT_WIDGET_TEXT_SIZE, local.scheduleWidget.compact.textSize)
            )
        )
    )

    private fun fullSections(local: LocalSettings) = listOf(
        SettingSection(
            title = null,
            items = listOf(
                SettingItem.Toggle(
                    id = SettingRowId.FULL_WIDGET_HIDE_TEACHER,
                    title = UiText.Res(CoreRes.string.settings_widget_hide_teacher_title),
                    checked = local.scheduleWidget.full.hideTeacher
                ),
                SettingItem.Toggle(
                    id = SettingRowId.FULL_WIDGET_HIDE_PAST,
                    title = UiText.Res(CoreRes.string.settings_widget_hide_past_title),
                    checked = local.scheduleWidget.full.hidePastLessons
                ),
                SettingItem.Toggle(
                    id = SettingRowId.FULL_WIDGET_SHOW_TOMORROW,
                    title = UiText.Res(CoreRes.string.settings_widget_tomorrow_title),
                    description = UiText.Res(CoreRes.string.settings_widget_tomorrow_description),
                    checked = local.scheduleWidget.full.showTomorrowWhenTodayIsOver
                ),
                textSizeChoice(SettingRowId.FULL_WIDGET_TEXT_SIZE, local.scheduleWidget.full.textSize)
            )
        )
    )

    private fun qrSections(state: SettingsPageState): List<SettingSection> {
        val qr = state.local.qrWidget
        return listOfNotNull(
            qrTileSection(state.local),
            SettingSection(
                title = null,
                items = listOf(
                    SettingItem.Toggle(
                        id = SettingRowId.QR_DYNAMIC_COLORS,
                        title = UiText.Res(CoreRes.string.settings_qr_dynamic_colors_title),
                        checked = qr.dynamicColors
                    ),
                    SettingItem.Toggle(
                        id = SettingRowId.QR_SPOILER,
                        title = UiText.Res(CoreRes.string.settings_qr_spoiler_title),
                        description = UiText.Res(CoreRes.string.settings_qr_spoiler_description),
                        checked = qr.spoilerEnabled
                    )
                )
            ),
            spoilerSection(state)
        )
    }

    /** The spoiler's animation and image; null when the platform offers neither. */
    private fun spoilerSection(state: SettingsPageState): SettingSection? {
        val qr = state.local.qrWidget
        val items = listOfNotNull(
            SettingItem.Choice(
                id = SettingRowId.QR_ANIMATION,
                title = UiText.Res(Res.string.settings_qr_animation_title),
                value = qr.animationType.label(),
                options = QrAnimationType.entries.map { animation ->
                    ChoiceOption(animation.name, animation.label())
                },
                selectedOptionKey = qr.animationType.name,
                enabled = qr.spoilerEnabled
            ).takeIf { capabilities.qrWidgetAnimation },
            SettingItem.Action(
                id = SettingRowId.QR_CUSTOM_IMAGE,
                title = UiText.Res(CoreRes.string.settings_qr_custom_image_title),
                value = UiText.Res(
                    if (state.hasCustomSpoiler) {
                        CoreRes.string.settings_qr_custom_image_selected
                    } else {
                        CoreRes.string.settings_qr_custom_image_default
                    }
                ),
                trailingIcon = AppIcon.CHEVRON_RIGHT,
                enabled = qr.spoilerEnabled && !state.imageBusy
            ).takeIf { capabilities.qrCustomSpoiler },
            SettingItem.Action(
                id = SettingRowId.QR_RESET_IMAGE,
                title = UiText.Res(Res.string.settings_qr_reset_image_title),
                enabled = qr.spoilerEnabled && state.hasCustomSpoiler && !state.imageBusy
            ).takeIf { capabilities.qrCustomSpoiler }
        )
        if (items.isEmpty()) return null
        return SettingSection(title = UiText.Res(Res.string.settings_group_spoiler), items = items)
    }

    /** Android 13+ asks the system to add the tile; the row leaves once the tile is known to be added. */
    private fun qrTileSection(local: LocalSettings): SettingSection? {
        if (!capabilities.quickSettingsTile || !tileAccess.canRequestAdd() || local.qrTileAdded) return null
        return SettingSection(
            title = null,
            items = listOf(
                SettingItem.Action(
                    id = SettingRowId.QR_TILE,
                    title = UiText.Res(Res.string.settings_qr_tile_title),
                    description = UiText.Res(Res.string.settings_qr_tile_description)
                )
            )
        )
    }

    private fun textSizeChoice(id: SettingRowId, size: WidgetTextSize) = SettingItem.Choice(
        id = id,
        title = UiText.Res(CoreRes.string.settings_widget_text_size_title),
        value = size.label(),
        options = WidgetTextSize.entries.map { ChoiceOption(it.name, it.label()) },
        selectedOptionKey = size.name
    )

    private fun WidgetTextSize.label() = UiText.Res(
        when (this) {
            WidgetTextSize.NORMAL -> CoreRes.string.settings_widget_text_size_normal
            WidgetTextSize.LARGE -> CoreRes.string.settings_widget_text_size_large
            WidgetTextSize.EXTRA_LARGE -> CoreRes.string.settings_widget_text_size_extra_large
        }
    )

    private fun QrAnimationType.label() = UiText.Res(
        when (this) {
            QrAnimationType.CIRCLE -> Res.string.settings_qr_animation_circle
            QrAnimationType.FADE -> Res.string.settings_qr_animation_fade
            QrAnimationType.NONE -> Res.string.settings_qr_animation_none
        }
    )
}
