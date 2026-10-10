package dev.alllexey.itmowidgets.feature.settings.ui.preview

import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.settings.QrWidgetSettings
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetFormat
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.SharingVisibility
import dev.alllexey.itmowidgets.feature.settings.presentation.ChoiceOption
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingItem
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingRowId
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingSection
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPage
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsUiState
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.core.app_unofficial_notice
import dev.alllexey.itmowidgets.shared.core.me_group_app
import dev.alllexey.itmowidgets.shared.core.settings_qr_custom_image_default
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
import dev.alllexey.itmowidgets.shared.feature.settings.settings_accent_color_amber
import dev.alllexey.itmowidgets.shared.feature.settings.settings_accent_color_brand
import dev.alllexey.itmowidgets.shared.feature.settings.settings_accent_color_custom
import dev.alllexey.itmowidgets.shared.feature.settings.settings_accent_color_green
import dev.alllexey.itmowidgets.shared.feature.settings.settings_accent_color_pink
import dev.alllexey.itmowidgets.shared.feature.settings.settings_accent_color_purple
import dev.alllexey.itmowidgets.shared.feature.settings.settings_accent_color_red
import dev.alllexey.itmowidgets.shared.feature.settings.settings_accent_color_teal
import dev.alllexey.itmowidgets.shared.feature.settings.settings_accent_color_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_accent_color_wallpaper
import dev.alllexey.itmowidgets.shared.feature.settings.settings_calendar_sync_description
import dev.alllexey.itmowidgets.shared.feature.settings.settings_calendar_sync_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_custom_services_toggle
import dev.alllexey.itmowidgets.shared.feature.settings.settings_delete_account_description
import dev.alllexey.itmowidgets.shared.feature.settings.settings_delete_account_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_diagnostics_count
import dev.alllexey.itmowidgets.shared.feature.settings.settings_diagnostics_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_friends_sharing_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_group_access
import dev.alllexey.itmowidgets.shared.feature.settings.settings_group_spoiler
import dev.alllexey.itmowidgets.shared.feature.settings.settings_group_widgets
import dev.alllexey.itmowidgets.shared.feature.settings.settings_home_card_friends_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_home_card_marks_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_home_card_schedule_changes_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_home_card_schedule_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_home_card_sport_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_ics_export_description
import dev.alllexey.itmowidgets.shared.feature.settings.settings_ics_export_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_marks_footer
import dev.alllexey.itmowidgets.shared.feature.settings.settings_marks_myitmo_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_marks_sheets_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_notifications_allowed
import dev.alllexey.itmowidgets.shared.feature.settings.settings_notifications_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_privacy_all
import dev.alllexey.itmowidgets.shared.feature.settings.settings_privacy_footer
import dev.alllexey.itmowidgets.shared.feature.settings.settings_privacy_friends
import dev.alllexey.itmowidgets.shared.feature.settings.settings_privacy_load_error
import dev.alllexey.itmowidgets.shared.feature.settings.settings_privacy_nobody
import dev.alllexey.itmowidgets.shared.feature.settings.settings_privacy_policy_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_privacy_retry
import dev.alllexey.itmowidgets.shared.feature.settings.settings_privacy_services_required
import dev.alllexey.itmowidgets.shared.feature.settings.settings_privacy_unknown
import dev.alllexey.itmowidgets.shared.feature.settings.settings_qr_animation_circle
import dev.alllexey.itmowidgets.shared.feature.settings.settings_qr_animation_fade
import dev.alllexey.itmowidgets.shared.feature.settings.settings_qr_animation_none
import dev.alllexey.itmowidgets.shared.feature.settings.settings_qr_animation_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_qr_reset_image_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_qr_short_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_refresh_widgets_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_restart_onboarding_description
import dev.alllexey.itmowidgets.shared.feature.settings.settings_restart_onboarding_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_schedule_changes_description
import dev.alllexey.itmowidgets.shared.feature.settings.settings_schedule_changes_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_schedule_footer
import dev.alllexey.itmowidgets.shared.feature.settings.settings_schedule_sharing_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_schedule_sport_auto_sign_description
import dev.alllexey.itmowidgets.shared.feature.settings.settings_schedule_sport_auto_sign_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_services_enabled
import dev.alllexey.itmowidgets.shared.feature.settings.settings_services_footer
import dev.alllexey.itmowidgets.shared.feature.settings.settings_sport_sharing_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_sport_teacher_filter_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_sport_time_filter_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_version_title
import org.jetbrains.compose.resources.StringResource

/**
 * The settings pages of LT-3a's XML references, as the page providers build them over the presentation tests'
 * fixture: custom services on, notifications allowed, no background restriction, no quick-settings tile, no custom
 * spoiler image, no diagnostics records, version `2.1-test`. `SettingsPreviewDataTest` checks every page against
 * the real providers, so a provider change that these previews miss fails a test instead of drifting.
 */
internal object SettingsPreviewData {

    /** The version the presentation tests' fixture reports. */
    const val VERSION = "2.1-test"

    val Root = state(
        SettingsPage.ROOT,
        SettingSection(
            title = res(Res.string.settings_group_access),
            items = listOf(
                navigation(SettingsPage.SERVICES, value = res(Res.string.settings_services_enabled)),
                navigation(SettingsPage.PRIVACY),
                SettingItem.Action(
                    id = SettingRowId.NOTIFICATIONS,
                    title = res(Res.string.settings_notifications_title),
                    value = res(Res.string.settings_notifications_allowed),
                    trailingIcon = AppIcon.CHEVRON_RIGHT
                )
            )
        ),
        SettingSection(
            title = res(Res.string.settings_group_widgets),
            items = listOf(
                navigation(SettingsPage.COMPACT_SCHEDULE_WIDGET),
                navigation(SettingsPage.FULL_SCHEDULE_WIDGET),
                navigation(SettingsPage.QR_WIDGET, title = res(Res.string.settings_qr_short_title))
            )
        ),
        SettingSection(
            title = res(CoreRes.string.me_group_app),
            items = listOf(
                SettingItem.Choice(
                    id = SettingRowId.ACCENT_COLOR,
                    title = res(Res.string.settings_accent_color_title),
                    value = AccentColor.WALLPAPER.label(),
                    options = AccentColor.entries.map { ChoiceOption(it.name, it.label()) },
                    selectedOptionKey = AccentColor.WALLPAPER.name
                ),
                navigation(SettingsPage.HOME),
                navigation(SettingsPage.SCHEDULE),
                navigation(SettingsPage.RECORDBOOK),
                navigation(SettingsPage.SPORT),
                navigation(SettingsPage.MAINTENANCE)
            )
        )
    )

    val Services = state(
        SettingsPage.SERVICES,
        SettingSection(
            title = null,
            items = listOf(
                SettingItem.Toggle(
                    id = SettingRowId.CUSTOM_SERVICES,
                    title = res(Res.string.settings_custom_services_toggle),
                    checked = true
                )
            ),
            footer = res(Res.string.settings_services_footer)
        ),
        SettingSection(
            title = null,
            items = listOf(
                SettingItem.Action(
                    id = SettingRowId.DELETE_ACCOUNT,
                    title = res(Res.string.settings_delete_account_title),
                    description = res(Res.string.settings_delete_account_description),
                    trailingIcon = AppIcon.OPEN_IN_NEW
                )
            )
        )
    )

    /** Backend's default audiences: schedule and sport to friends, friends to everyone. */
    val Privacy = privacy(
        footer = res(Res.string.settings_privacy_footer),
        schedule = SharingVisibility.FRIENDS,
        sport = SharingVisibility.FRIENDS,
        friends = SharingVisibility.ALL
    )

    /** Backend did not answer: the rows say so, stay locked, and a retry row follows them. */
    val PrivacyError = privacy(
        footer = res(Res.string.settings_privacy_load_error),
        extra = SettingItem.Action(SettingRowId.RETRY_PRIVACY, res(Res.string.settings_privacy_retry))
    )

    /** Custom services off: nothing to load, the rows lead to the connection page. */
    val PrivacyDisabled = privacy(
        footer = res(Res.string.settings_privacy_services_required),
        extra = navigation(SettingsPage.SERVICES)
    )

    /** The minimum loading time of privacy: the page is loaded, its rows are not. */
    val PrivacyLoading = SettingsUiState(page = SettingsPage.PRIVACY, loaded = true)

    val CompactScheduleWidget = state(
        SettingsPage.COMPACT_SCHEDULE_WIDGET,
        SettingSection(
            title = null,
            items = listOf(
                SettingItem.Toggle(
                    id = SettingRowId.COMPACT_WIDGET_NEXT_LESSON_EARLY,
                    title = res(CoreRes.string.settings_widget_next_early_title),
                    description = res(CoreRes.string.settings_widget_next_early_description),
                    checked = true
                ),
                SettingItem.Toggle(
                    id = SettingRowId.COMPACT_WIDGET_HIDE_TEACHER,
                    title = res(CoreRes.string.settings_widget_hide_teacher_title),
                    checked = false
                ),
                textSize(SettingRowId.COMPACT_WIDGET_TEXT_SIZE)
            )
        ),
        preview = WidgetPreviewSettings.Schedule(ScheduleWidgetSettings(), ScheduleWidgetFormat.COMPACT)
    )

    val FullScheduleWidget = state(
        SettingsPage.FULL_SCHEDULE_WIDGET,
        SettingSection(
            title = null,
            items = listOf(
                SettingItem.Toggle(
                    id = SettingRowId.FULL_WIDGET_HIDE_TEACHER,
                    title = res(CoreRes.string.settings_widget_hide_teacher_title),
                    checked = false
                ),
                SettingItem.Toggle(
                    id = SettingRowId.FULL_WIDGET_HIDE_PAST,
                    title = res(CoreRes.string.settings_widget_hide_past_title),
                    checked = false
                ),
                SettingItem.Toggle(
                    id = SettingRowId.FULL_WIDGET_SHOW_TOMORROW,
                    title = res(CoreRes.string.settings_widget_tomorrow_title),
                    description = res(CoreRes.string.settings_widget_tomorrow_description),
                    checked = false
                ),
                textSize(SettingRowId.FULL_WIDGET_TEXT_SIZE)
            )
        ),
        preview = WidgetPreviewSettings.Schedule(ScheduleWidgetSettings(), ScheduleWidgetFormat.FULL)
    )

    val QrWidget = state(
        SettingsPage.QR_WIDGET,
        SettingSection(
            title = null,
            items = listOf(
                SettingItem.Toggle(
                    id = SettingRowId.QR_DYNAMIC_COLORS,
                    title = res(CoreRes.string.settings_qr_dynamic_colors_title),
                    checked = true
                ),
                SettingItem.Toggle(
                    id = SettingRowId.QR_SPOILER,
                    title = res(CoreRes.string.settings_qr_spoiler_title),
                    description = res(CoreRes.string.settings_qr_spoiler_description),
                    checked = true
                )
            )
        ),
        SettingSection(
            title = res(Res.string.settings_group_spoiler),
            items = listOf(
                SettingItem.Choice(
                    id = SettingRowId.QR_ANIMATION,
                    title = res(Res.string.settings_qr_animation_title),
                    value = QrAnimationType.CIRCLE.label(),
                    options = QrAnimationType.entries.map { ChoiceOption(it.name, it.label()) },
                    selectedOptionKey = QrAnimationType.CIRCLE.name
                ),
                SettingItem.Action(
                    id = SettingRowId.QR_CUSTOM_IMAGE,
                    title = res(CoreRes.string.settings_qr_custom_image_title),
                    value = res(CoreRes.string.settings_qr_custom_image_default),
                    trailingIcon = AppIcon.CHEVRON_RIGHT
                ),
                SettingItem.Action(
                    id = SettingRowId.QR_RESET_IMAGE,
                    title = res(Res.string.settings_qr_reset_image_title),
                    enabled = false
                )
            )
        ),
        preview = WidgetPreviewSettings.Qr(QrWidgetSettings())
    )

    val Home = state(
        SettingsPage.HOME,
        SettingSection(
            title = null,
            items = listOf(
                SettingRowId.HOME_CARD_SCHEDULE to Res.string.settings_home_card_schedule_title,
                SettingRowId.HOME_CARD_SCHEDULE_CHANGES to Res.string.settings_home_card_schedule_changes_title,
                SettingRowId.HOME_CARD_MARKS to Res.string.settings_home_card_marks_title,
                SettingRowId.HOME_CARD_SPORT to Res.string.settings_home_card_sport_title,
                SettingRowId.HOME_CARD_FRIENDS to Res.string.settings_home_card_friends_title
            ).map { (id, title) -> SettingItem.Toggle(id, res(title), checked = true) }
        )
    )

    val Schedule = state(
        SettingsPage.SCHEDULE,
        SettingSection(
            title = null,
            items = listOf(
                SettingItem.Toggle(
                    id = SettingRowId.SCHEDULE_CHANGES,
                    title = res(Res.string.settings_schedule_changes_title),
                    description = res(Res.string.settings_schedule_changes_description),
                    checked = true
                )
            )
        ),
        SettingSection(
            title = null,
            items = listOf(
                SettingItem.Toggle(
                    id = SettingRowId.SCHEDULE_SPORT_AUTO_SIGN,
                    title = res(Res.string.settings_schedule_sport_auto_sign_title),
                    description = res(Res.string.settings_schedule_sport_auto_sign_description),
                    checked = false
                )
            ),
            footer = res(Res.string.settings_schedule_footer)
        ),
        SettingSection(
            title = null,
            items = listOf(
                SettingItem.Toggle(
                    id = SettingRowId.CALENDAR_SYNC,
                    title = res(Res.string.settings_calendar_sync_title),
                    description = res(Res.string.settings_calendar_sync_description),
                    checked = false
                ),
                SettingItem.Action(
                    id = SettingRowId.ICS_EXPORT,
                    title = res(Res.string.settings_ics_export_title),
                    description = res(Res.string.settings_ics_export_description),
                    trailingIcon = AppIcon.DOWNLOAD
                )
            )
        )
    )

    val Recordbook = state(
        SettingsPage.RECORDBOOK,
        SettingSection(
            title = null,
            items = listOf(
                SettingItem.Toggle(SettingRowId.MYITMO_MARKS, res(Res.string.settings_marks_myitmo_title), checked = true),
                SettingItem.Toggle(SettingRowId.SHEET_MARKS, res(Res.string.settings_marks_sheets_title), checked = true)
            ),
            footer = res(Res.string.settings_marks_footer)
        )
    )

    val Sport = state(
        SettingsPage.SPORT,
        SettingSection(
            title = null,
            items = listOf(
                SettingItem.Toggle(
                    id = SettingRowId.SPORT_TEACHER_FILTER,
                    title = res(Res.string.settings_sport_teacher_filter_title),
                    checked = false
                ),
                SettingItem.Toggle(
                    id = SettingRowId.SPORT_TIME_FILTER,
                    title = res(Res.string.settings_sport_time_filter_title),
                    checked = false
                )
            )
        )
    )

    val Maintenance = state(
        SettingsPage.MAINTENANCE,
        SettingSection(
            title = null,
            items = listOf(
                SettingItem.Action(
                    id = SettingRowId.REFRESH_WIDGETS,
                    title = res(Res.string.settings_refresh_widgets_title),
                    trailingIcon = AppIcon.REFRESH
                ),
                SettingItem.Action(
                    id = SettingRowId.RESTART_ONBOARDING,
                    title = res(Res.string.settings_restart_onboarding_title),
                    description = res(Res.string.settings_restart_onboarding_description),
                    trailingIcon = AppIcon.REFRESH
                ),
                SettingItem.Action(
                    id = SettingRowId.DIAGNOSTICS,
                    title = res(Res.string.settings_diagnostics_title),
                    value = UiText.Res(Res.string.settings_diagnostics_count, listOf(0)),
                    trailingIcon = AppIcon.CHEVRON_RIGHT
                ),
                SettingItem.Action(
                    id = SettingRowId.PRIVACY_POLICY,
                    title = res(Res.string.settings_privacy_policy_title),
                    trailingIcon = AppIcon.OPEN_IN_NEW
                ),
                SettingItem.Info(SettingRowId.VERSION, res(Res.string.settings_version_title), UiText.Dynamic(VERSION))
            ),
            footer = res(CoreRes.string.app_unofficial_notice)
        )
    )

    /** Every row kind with long Russian text, a locked switch, a switch still loading and a disabled row. */
    val LongText = SettingsUiState(
        page = SettingsPage.QR_WIDGET,
        loaded = true,
        sections = listOf(
            SettingSection(
                title = UiText.Dynamic("Очень длинное название группы настроек виджета QR-кода"),
                items = listOf(
                    SettingItem.Choice(
                        id = SettingRowId.QR_ANIMATION,
                        title = UiText.Dynamic("Анимация скрытия и раскрытия изображения QR-кода"),
                        value = UiText.Dynamic("Плавное исчезновение пользовательского изображения"),
                        options = emptyList(),
                        selectedOptionKey = null
                    ),
                    SettingItem.Toggle(
                        id = SettingRowId.FULL_WIDGET_SHOW_TOMORROW,
                        title = UiText.Dynamic(
                            "Показывать расписание следующего дня после окончания сегодняшних занятий"
                        ),
                        description = UiText.Dynamic("Когда сегодняшние пары закончились, виджет покажет завтрашние."),
                        checked = true
                    ),
                    SettingItem.Toggle(
                        id = SettingRowId.QR_SPOILER,
                        title = UiText.Dynamic("Переключатель, значение которого ещё загружается с сервера"),
                        checked = false,
                        stateKnown = false
                    ),
                    SettingItem.Navigation(
                        id = SettingRowId.PAGE_SERVICES,
                        title = UiText.Dynamic("Подключение к ITMO.Widgets и удаление аккаунта"),
                        value = UiText.Dynamic("Не подключено, друзья и автозапись недоступны"),
                        page = SettingsPage.SERVICES
                    ),
                    SettingItem.Action(
                        id = SettingRowId.QR_RESET_IMAGE,
                        title = UiText.Dynamic("Сбросить пользовательское изображение спойлера"),
                        enabled = false
                    ),
                    SettingItem.Info(
                        id = SettingRowId.VERSION,
                        title = UiText.Dynamic("Версия приложения"),
                        value = UiText.Dynamic("2.3.0-SNAPSHOT (сборка для проверки длинной строки)")
                    )
                ),
                footer = UiText.Dynamic(
                    "Пояснение под группой занимает несколько строк и переносится по словам на узком экране."
                )
            )
        )
    )

    private fun privacy(
        footer: UiText,
        schedule: SharingVisibility? = null,
        sport: SharingVisibility? = null,
        friends: SharingVisibility? = null,
        extra: SettingItem? = null
    ): SettingsUiState {
        val editable = schedule != null
        val items = listOfNotNull(
            sharing(SettingRowId.SCHEDULE_SHARING, Res.string.settings_schedule_sharing_title, schedule, editable),
            sharing(SettingRowId.SPORT_SHARING, Res.string.settings_sport_sharing_title, sport, editable),
            sharing(SettingRowId.FRIENDS_SHARING, Res.string.settings_friends_sharing_title, friends, editable),
            extra
        )
        return state(SettingsPage.PRIVACY, SettingSection(title = null, items = items, footer = footer))
    }

    private fun sharing(id: SettingRowId, title: StringResource, visibility: SharingVisibility?, enabled: Boolean) =
        SettingItem.Choice(
            id = id,
            title = res(title),
            value = visibility?.label() ?: res(Res.string.settings_privacy_unknown),
            options = SharingVisibility.entries.map { ChoiceOption(it.name, it.label()) },
            selectedOptionKey = visibility?.name,
            enabled = enabled
        )

    private fun textSize(id: SettingRowId) = SettingItem.Choice(
        id = id,
        title = res(CoreRes.string.settings_widget_text_size_title),
        value = WidgetTextSize.NORMAL.label(),
        options = WidgetTextSize.entries.map { ChoiceOption(it.name, it.label()) },
        selectedOptionKey = WidgetTextSize.NORMAL.name
    )

    private fun navigation(page: SettingsPage, title: UiText = page.title, value: UiText? = null) =
        SettingItem.Navigation(SettingRowId.navigation(page), title, value, page = page)

    private fun state(
        page: SettingsPage,
        vararg sections: SettingSection,
        preview: WidgetPreviewSettings? = null
    ) = SettingsUiState(page, sections.toList(), loaded = true, previewSettings = preview)

    private fun res(resource: StringResource) = UiText.Res(resource)

    private fun SharingVisibility.label() = res(
        when (this) {
            SharingVisibility.ALL -> Res.string.settings_privacy_all
            SharingVisibility.FRIENDS -> Res.string.settings_privacy_friends
            SharingVisibility.NOBODY -> Res.string.settings_privacy_nobody
        }
    )

    private fun WidgetTextSize.label() = res(
        when (this) {
            WidgetTextSize.NORMAL -> CoreRes.string.settings_widget_text_size_normal
            WidgetTextSize.LARGE -> CoreRes.string.settings_widget_text_size_large
            WidgetTextSize.EXTRA_LARGE -> CoreRes.string.settings_widget_text_size_extra_large
        }
    )

    private fun AccentColor.label() = res(
        when (this) {
            AccentColor.WALLPAPER -> Res.string.settings_accent_color_wallpaper
            AccentColor.BRAND -> Res.string.settings_accent_color_brand
            AccentColor.TEAL -> Res.string.settings_accent_color_teal
            AccentColor.GREEN -> Res.string.settings_accent_color_green
            AccentColor.AMBER -> Res.string.settings_accent_color_amber
            AccentColor.RED -> Res.string.settings_accent_color_red
            AccentColor.PINK -> Res.string.settings_accent_color_pink
            AccentColor.PURPLE -> Res.string.settings_accent_color_purple
            AccentColor.CUSTOM -> Res.string.settings_accent_color_custom
        }
    )

    private fun QrAnimationType.label() = res(
        when (this) {
            QrAnimationType.CIRCLE -> Res.string.settings_qr_animation_circle
            QrAnimationType.FADE -> Res.string.settings_qr_animation_fade
            QrAnimationType.NONE -> Res.string.settings_qr_animation_none
        }
    )
}
