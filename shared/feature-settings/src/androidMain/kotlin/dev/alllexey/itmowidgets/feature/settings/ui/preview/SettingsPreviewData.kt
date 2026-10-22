package dev.alllexey.itmowidgets.feature.settings.ui.preview

import dev.alllexey.itmowidgets.core.platform.AppBuild
import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.core.settings.WidgetFormat
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.presentation.EveryPlatformCapability
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPageState
import dev.alllexey.itmowidgets.feature.settings.presentation.WidgetGalleryRows
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingItem
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingRowId
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingSection
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPage
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsUiState
import dev.alllexey.itmowidgets.feature.settings.presentation.versionSummary
import dev.alllexey.itmowidgets.shared.compose.strings.app_unofficial_notice
import dev.alllexey.itmowidgets.shared.compose.strings.me_group_app
import dev.alllexey.itmowidgets.shared.compose.strings.settings_widget_text_size_extra_large
import dev.alllexey.itmowidgets.shared.compose.strings.settings_widget_text_size_large
import dev.alllexey.itmowidgets.shared.compose.strings.settings_widget_text_size_normal
import dev.alllexey.itmowidgets.shared.compose.strings.Res
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.settings.SettingsStrings
import dev.alllexey.itmowidgets.shared.compose.strings.settings_accent_color_wallpaper
import dev.alllexey.itmowidgets.shared.compose.strings.settings_calendar_sync_description
import dev.alllexey.itmowidgets.shared.compose.strings.settings_calendar_sync_title
import dev.alllexey.itmowidgets.shared.compose.strings.settings_diagnostics_title
import dev.alllexey.itmowidgets.shared.compose.strings.settings_ics_export_description
import dev.alllexey.itmowidgets.shared.compose.strings.settings_ics_export_title
import dev.alllexey.itmowidgets.shared.compose.strings.settings_privacy_policy_title
import dev.alllexey.itmowidgets.shared.compose.strings.settings_licenses_data_title
import dev.alllexey.itmowidgets.shared.compose.strings.settings_licenses_license
import dev.alllexey.itmowidgets.shared.compose.strings.settings_licenses_maplibre_text
import dev.alllexey.itmowidgets.shared.compose.strings.settings_licenses_noto_text
import dev.alllexey.itmowidgets.shared.compose.strings.settings_licenses_plans_note
import dev.alllexey.itmowidgets.shared.compose.strings.settings_licenses_plans_title
import dev.alllexey.itmowidgets.shared.compose.strings.settings_licenses_plans_value
import dev.alllexey.itmowidgets.shared.compose.strings.settings_qr_animation_circle
import dev.alllexey.itmowidgets.shared.compose.strings.settings_qr_animation_fade
import dev.alllexey.itmowidgets.shared.compose.strings.settings_qr_animation_none
import dev.alllexey.itmowidgets.shared.compose.strings.settings_refresh_widgets_title
import dev.alllexey.itmowidgets.shared.compose.strings.settings_restart_onboarding_description
import dev.alllexey.itmowidgets.shared.compose.strings.settings_restart_onboarding_title
import dev.alllexey.itmowidgets.shared.compose.strings.settings_schedule_footer
import dev.alllexey.itmowidgets.shared.compose.strings.settings_schedule_sport_auto_sign_description
import dev.alllexey.itmowidgets.shared.compose.strings.settings_schedule_sport_auto_sign_title
import dev.alllexey.itmowidgets.shared.compose.strings.settings_services_enabled
import dev.alllexey.itmowidgets.shared.compose.strings.settings_sport_teacher_filter_title
import dev.alllexey.itmowidgets.shared.compose.strings.settings_sport_time_filter_title
import dev.alllexey.itmowidgets.shared.compose.strings.settings_version_title
import org.jetbrains.compose.resources.StringResource

/**
 * The settings pages of the previews, as the page providers build them over the presentation tests'
 * fixture: custom services on, notifications allowed, no quick-settings tile, no custom
 * spoiler image, no diagnostics records, a fork's build of version `2.1-test`. `SettingsPreviewDataTest` checks every page against
 * the real providers, so a provider change that these previews miss fails a test instead of drifting.
 */
internal object SettingsPreviewData {

    /** The build the presentation tests' fixture reports. */
    private val BUILD = AppBuild("2.1-test", "20100", AppBuild.CUSTOM, "AlexPer777/ITMO.Widgets", "a1b2c3d4e5f6")

    /** Services on and notifications allowed with the defaults: the checks and the three pushes, every one on. */
    val Root = state(
        SettingsPage.ROOT,
        SettingSection(
            title = UiText.Res(SettingsStrings.settings_group_accounts),
            items = listOf(
                navigation(SettingsPage.SERVICES, value = res(Res.string.settings_services_enabled), icon = AppIcon.CLOUD_DONE),
                SettingItem.Action(
                    id = SettingRowId.BARS,
                    title = UiText.Res(SettingsStrings.settings_bars_title),
                    value = UiText.Res(SettingsStrings.settings_bars_off),
                    trailingIcon = AppIcon.CHEVRON_RIGHT,
                    icon = AppIcon.GRADING
                )
            )
        ),
        SettingSection(
            title = res(Res.string.me_group_app),
            items = listOf(
                navigation(
                    SettingsPage.NOTIFICATIONS,
                    value = UiText.Res(SettingsStrings.notifications_subtitle_count, listOf(5, 5)),
                    icon = AppIcon.NOTIFICATION
                ),
                navigation(SettingsPage.WIDGETS, icon = AppIcon.WIDGETS),
                navigation(SettingsPage.APPEARANCE, value = res(Res.string.settings_accent_color_wallpaper), icon = AppIcon.PALETTE),
                navigation(SettingsPage.SECTIONS, icon = AppIcon.TUNE)
            )
        ),
        SettingSection(
            title = null,
            items = listOf(navigation(SettingsPage.ABOUT, icon = AppIcon.INFO))
        )
    )

    /** The gallery with every format, one placed widget and the pass under its spoiler. */
    val Widgets = widgets(placed = mapOf(WidgetFormat.DAY to 1))

    fun widgets(
        placed: Map<WidgetFormat, Int> = emptyMap(),
        local: LocalSettings = LocalSettings(),
        capabilities: PlatformCapabilities = EveryPlatformCapability,
    ): SettingsUiState {
        val state = SettingsPageState(
            local = local,
            notificationsGranted = true,
            hasCustomSpoiler = false,
            imageBusy = false,
            placedWidgets = placed,
        )
        val rows = WidgetGalleryRows(capabilities, canAddTile = false)
        return SettingsUiState(SettingsPage.WIDGETS, rows.page(state), loaded = true, gallery = rows.gallery(state))
    }

    val Sections = state(
        SettingsPage.SECTIONS,
        SettingSection(
            title = UiText.Res(CoreStrings.tab_today),
            items = listOf(
                SettingItem.Toggle(
                    id = SettingRowId.HOME_PASS_VISIBLE,
                    title = UiText.Res(SettingsStrings.settings_home_pass_title),
                    checked = true,
                    icon = AppIcon.QR_CODE,
                )
            ),
            footer = UiText.Res(SettingsStrings.settings_home_pass_description),
        ),
        SettingSection(
            title = UiText.Res(SettingsStrings.settings_group_schedule),
            items = listOf(
                SettingItem.Toggle(
                    id = SettingRowId.CALENDAR_SYNC,
                    title = res(Res.string.settings_calendar_sync_title),
                    description = res(Res.string.settings_calendar_sync_description),
                    checked = false,
                    icon = AppIcon.EVENT_REPEAT
                ),
                SettingItem.Action(
                    id = SettingRowId.ICS_EXPORT,
                    title = res(Res.string.settings_ics_export_title),
                    description = res(Res.string.settings_ics_export_description),
                    icon = AppIcon.DOWNLOAD
                ),
                SettingItem.Toggle(
                    id = SettingRowId.SCHEDULE_SPORT_AUTO_SIGN,
                    title = res(Res.string.settings_schedule_sport_auto_sign_title),
                    description = res(Res.string.settings_schedule_sport_auto_sign_description),
                    checked = true,
                    icon = AppIcon.SPRINT
                )
            ),
            footer = res(Res.string.settings_schedule_footer)
        ),
        SettingSection(
            title = UiText.Res(SettingsStrings.settings_group_sport),
            items = listOf(
                SettingItem.Toggle(
                    id = SettingRowId.SPORT_TEACHER_FILTER,
                    title = res(Res.string.settings_sport_teacher_filter_title),
                    checked = false,
                    icon = AppIcon.PERSON
                ),
                SettingItem.Toggle(
                    id = SettingRowId.SPORT_TIME_FILTER,
                    title = res(Res.string.settings_sport_time_filter_title),
                    checked = false,
                    icon = AppIcon.SCHEDULE
                )
            )
        )
    )

    val About = state(
        SettingsPage.ABOUT,
        SettingSection(
            title = null,
            items = listOf(
                SettingItem.Action(
                    id = SettingRowId.REFRESH_WIDGETS,
                    title = res(Res.string.settings_refresh_widgets_title),
                    icon = AppIcon.REFRESH
                ),
                SettingItem.Action(
                    id = SettingRowId.RESTART_ONBOARDING,
                    title = res(Res.string.settings_restart_onboarding_title),
                    description = res(Res.string.settings_restart_onboarding_description),
                    icon = AppIcon.HISTORY
                ),
                SettingItem.Action(
                    id = SettingRowId.DIAGNOSTICS,
                    title = res(Res.string.settings_diagnostics_title),
                    value = UiText.Res(SettingsStrings.settings_diagnostics_count, listOf(0)),
                    trailingIcon = AppIcon.CHEVRON_RIGHT,
                    icon = AppIcon.ERROR
                ),
                SettingItem.Action(
                    id = SettingRowId.PRIVACY_POLICY,
                    title = res(Res.string.settings_privacy_policy_title),
                    trailingIcon = AppIcon.OPEN_IN_NEW,
                    icon = AppIcon.LOCK
                ),
                navigation(SettingsPage.LICENSES, icon = AppIcon.MENU_BOOK),
                SettingItem.Action(
                    id = SettingRowId.VERSION,
                    title = res(Res.string.settings_version_title),
                    description = versionSummary(BUILD),
                    icon = AppIcon.INFO
                )
            ),
            footer = res(Res.string.app_unofficial_notice)
        )
    )

    val Licenses = state(
        SettingsPage.LICENSES,
        SettingSection(
            title = res(Res.string.settings_licenses_data_title),
            items = listOf(
                SettingItem.Info(
                    SettingRowId.LICENSE_PLANS,
                    res(Res.string.settings_licenses_plans_title),
                    res(Res.string.settings_licenses_plans_value)
                )
            ),
            footer = res(Res.string.settings_licenses_plans_note)
        ),
        SettingSection(
            title = UiText.Dynamic("MapLibre Native"),
            items = listOf(SettingItem.Info(SettingRowId.LICENSE_MAPLIBRE, res(Res.string.settings_licenses_license), UiText.Dynamic("BSD 2-Clause"))),
            footer = res(Res.string.settings_licenses_maplibre_text)
        ),
        SettingSection(
            title = UiText.Dynamic("Noto Sans"),
            items = listOf(SettingItem.Info(SettingRowId.LICENSE_NOTO, res(Res.string.settings_licenses_license), UiText.Dynamic("SIL OFL 1.1"))),
            footer = res(Res.string.settings_licenses_noto_text)
        )
    )

    /** Every row kind with long Russian text, a switch that cannot change now and a disabled row. */
    val LongText = SettingsUiState(
        page = SettingsPage.SECTIONS,
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
                        title = UiText.Dynamic("Выключенный переключатель, который сейчас нельзя изменить"),
                        checked = false,
                        enabled = false,
                        icon = AppIcon.LOCK
                    ),
                    SettingItem.Navigation(
                        id = SettingRowId.PAGE_SERVICES,
                        title = UiText.Dynamic("Подключение к ITMO.Widgets и удаление аккаунта"),
                        value = UiText.Dynamic("Не подключено, друзья и автозапись недоступны"),
                        page = SettingsPage.SERVICES,
                        icon = AppIcon.CLOUD_OFF
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

    private fun navigation(page: SettingsPage, value: UiText? = null, icon: AppIcon? = null) =
        SettingItem.Navigation(SettingRowId.navigation(page), page.title, value, page = page, icon = icon)

    private fun state(page: SettingsPage, vararg sections: SettingSection) =
        SettingsUiState(page, sections.toList(), loaded = true)

    private fun res(resource: StringResource) = UiText.Res(resource.key)

    private fun WidgetTextSize.label() = res(
        when (this) {
            WidgetTextSize.NORMAL -> Res.string.settings_widget_text_size_normal
            WidgetTextSize.LARGE -> Res.string.settings_widget_text_size_large
            WidgetTextSize.EXTRA_LARGE -> Res.string.settings_widget_text_size_extra_large
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
