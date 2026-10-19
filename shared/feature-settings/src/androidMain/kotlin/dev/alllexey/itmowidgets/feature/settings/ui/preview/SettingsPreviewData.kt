package dev.alllexey.itmowidgets.feature.settings.ui.preview

import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.core.settings.WidgetFormat
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.presentation.EveryPlatformCapability
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPageState
import dev.alllexey.itmowidgets.feature.settings.presentation.WidgetGalleryRows
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.settings.ThemeContrast
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.settings.ThemeStyle
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.presentation.ChoiceOption
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingItem
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingRowId
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingSection
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPage
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsUiState
import dev.alllexey.itmowidgets.shared.compose.strings.app_unofficial_notice
import dev.alllexey.itmowidgets.shared.compose.strings.me_group_app
import dev.alllexey.itmowidgets.shared.compose.strings.settings_widget_text_size_extra_large
import dev.alllexey.itmowidgets.shared.compose.strings.settings_widget_text_size_large
import dev.alllexey.itmowidgets.shared.compose.strings.settings_widget_text_size_normal
import dev.alllexey.itmowidgets.shared.compose.strings.Res
import dev.alllexey.itmowidgets.shared.feature.settings.SettingsStrings
import dev.alllexey.itmowidgets.shared.compose.strings.settings_appearance_group_colors
import dev.alllexey.itmowidgets.shared.compose.strings.settings_appearance_group_contrast
import dev.alllexey.itmowidgets.shared.compose.strings.settings_appearance_wallpaper_footer
import dev.alllexey.itmowidgets.shared.compose.strings.settings_theme_style_title
import dev.alllexey.itmowidgets.shared.compose.strings.settings_theme_contrast_title
import dev.alllexey.itmowidgets.shared.compose.strings.settings_dark_black_title
import dev.alllexey.itmowidgets.shared.compose.strings.settings_dark_black_description
import dev.alllexey.itmowidgets.shared.compose.strings.settings_theme_style_tonal_spot
import dev.alllexey.itmowidgets.shared.compose.strings.settings_theme_style_vibrant
import dev.alllexey.itmowidgets.shared.compose.strings.settings_theme_style_expressive
import dev.alllexey.itmowidgets.shared.compose.strings.settings_theme_style_fidelity
import dev.alllexey.itmowidgets.shared.compose.strings.settings_theme_style_content
import dev.alllexey.itmowidgets.shared.compose.strings.settings_theme_style_neutral
import dev.alllexey.itmowidgets.shared.compose.strings.settings_theme_style_monochrome
import dev.alllexey.itmowidgets.shared.compose.strings.settings_theme_style_tonal_spot_description
import dev.alllexey.itmowidgets.shared.compose.strings.settings_theme_style_vibrant_description
import dev.alllexey.itmowidgets.shared.compose.strings.settings_theme_style_expressive_description
import dev.alllexey.itmowidgets.shared.compose.strings.settings_theme_style_fidelity_description
import dev.alllexey.itmowidgets.shared.compose.strings.settings_theme_style_content_description
import dev.alllexey.itmowidgets.shared.compose.strings.settings_theme_style_neutral_description
import dev.alllexey.itmowidgets.shared.compose.strings.settings_theme_style_monochrome_description
import dev.alllexey.itmowidgets.shared.compose.strings.settings_theme_contrast_standard
import dev.alllexey.itmowidgets.shared.compose.strings.settings_theme_contrast_medium
import dev.alllexey.itmowidgets.shared.compose.strings.settings_theme_contrast_high
import dev.alllexey.itmowidgets.shared.compose.strings.settings_accent_color_amber
import dev.alllexey.itmowidgets.shared.compose.strings.settings_accent_color_brand
import dev.alllexey.itmowidgets.shared.compose.strings.settings_accent_color_custom
import dev.alllexey.itmowidgets.shared.compose.strings.settings_accent_color_green
import dev.alllexey.itmowidgets.shared.compose.strings.settings_accent_color_pink
import dev.alllexey.itmowidgets.shared.compose.strings.settings_accent_color_purple
import dev.alllexey.itmowidgets.shared.compose.strings.settings_accent_color_red
import dev.alllexey.itmowidgets.shared.compose.strings.settings_accent_color_teal
import dev.alllexey.itmowidgets.shared.compose.strings.settings_accent_color_title
import dev.alllexey.itmowidgets.shared.compose.strings.settings_accent_color_wallpaper
import dev.alllexey.itmowidgets.shared.compose.strings.settings_calendar_sync_description
import dev.alllexey.itmowidgets.shared.compose.strings.settings_calendar_sync_title
import dev.alllexey.itmowidgets.shared.compose.strings.settings_diagnostics_title
import dev.alllexey.itmowidgets.shared.compose.strings.settings_group_access
import dev.alllexey.itmowidgets.shared.compose.strings.settings_group_widgets
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
import dev.alllexey.itmowidgets.shared.compose.strings.settings_schedule_notifications_title
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
                navigation(SettingsPage.NOTIFICATIONS)
            )
        ),
        SettingSection(
            title = res(Res.string.settings_group_widgets),
            items = listOf(navigation(SettingsPage.WIDGETS))
        ),
        SettingSection(
            title = res(Res.string.me_group_app),
            items = listOf(
                navigation(SettingsPage.APPEARANCE),
                navigation(SettingsPage.HOME),
                navigation(SettingsPage.SCHEDULE),
                navigation(SettingsPage.SPORT),
                navigation(SettingsPage.MAINTENANCE)
            )
        )
    )

    val Home = state(
        SettingsPage.HOME,
        SettingSection(
            title = null,
            items = listOf(SettingItem.Toggle(
                id = SettingRowId.HOME_PASS_VISIBLE,
                title = UiText.Res(SettingsStrings.settings_home_pass_title),
                checked = true,
            )),
            footer = UiText.Res(SettingsStrings.settings_home_pass_description),
        ),
    )

    val Appearance = appearance(ThemeSpec())

    /** The appearance page for [theme] where the wallpaper has colours, with the sample above its rows. */
    fun appearance(theme: ThemeSpec): SettingsUiState {
        val colors = SettingSection(
            title = res(Res.string.settings_appearance_group_colors),
            items = listOfNotNull(
                SettingItem.Choice(
                    id = SettingRowId.ACCENT_COLOR,
                    title = res(Res.string.settings_accent_color_title),
                    value = theme.accent.label(),
                    options = AccentColor.entries.map { ChoiceOption(it.name, it.label()) },
                    selectedOptionKey = theme.accent.name
                ),
                SettingItem.CustomColor(
                    id = SettingRowId.ACCENT_CUSTOM,
                    title = res(Res.string.settings_accent_color_custom),
                    argb = theme.customArgb
                ).takeIf { theme.accent == AccentColor.CUSTOM },
                SettingItem.Choice(
                    id = SettingRowId.THEME_PALETTE,
                    title = res(Res.string.settings_theme_style_title),
                    value = theme.style.label(),
                    options = ThemeStyle.entries.map { ChoiceOption(it.name, it.label(), it.description()) },
                    selectedOptionKey = theme.style.name
                )
            ),
            footer = res(Res.string.settings_appearance_wallpaper_footer).takeIf { theme.accent == AccentColor.WALLPAPER }
        )
        val contrast = SettingSection(
            title = res(Res.string.settings_appearance_group_contrast),
            items = listOf(
                SettingItem.Choice(
                    id = SettingRowId.THEME_CONTRAST,
                    title = res(Res.string.settings_theme_contrast_title),
                    value = theme.contrast.label(),
                    options = ThemeContrast.entries.map { ChoiceOption(it.name, it.label()) },
                    selectedOptionKey = theme.contrast.name
                ),
                SettingItem.Toggle(
                    id = SettingRowId.DARK_BLACK,
                    title = res(Res.string.settings_dark_black_title),
                    description = res(Res.string.settings_dark_black_description),
                    checked = theme.pureBlack
                )
            )
        )
        return state(SettingsPage.APPEARANCE, colors, contrast).copy(themePreview = theme)
    }

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

    val Schedule = state(
        SettingsPage.SCHEDULE,
        SettingSection(
            title = null,
            items = listOf(
                navigation(SettingsPage.NOTIFICATIONS, title = res(Res.string.settings_schedule_notifications_title))
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
                    value = UiText.Res(SettingsStrings.settings_diagnostics_count, listOf(0)),
                    trailingIcon = AppIcon.CHEVRON_RIGHT
                ),
                SettingItem.Action(
                    id = SettingRowId.PRIVACY_POLICY,
                    title = res(Res.string.settings_privacy_policy_title),
                    trailingIcon = AppIcon.OPEN_IN_NEW
                ),
                navigation(SettingsPage.LICENSES),
                SettingItem.Info(SettingRowId.VERSION, res(Res.string.settings_version_title), UiText.Dynamic(VERSION))
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

    /** Every row kind with long Russian text, a locked switch, a switch still loading and a disabled row. */
    val LongText = SettingsUiState(
        page = SettingsPage.HOME,
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

    private fun navigation(page: SettingsPage, title: UiText = page.title, value: UiText? = null) =
        SettingItem.Navigation(SettingRowId.navigation(page), title, value, page = page)

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

    private fun ThemeStyle.label() = res(
        when (this) {
            ThemeStyle.TONAL_SPOT -> Res.string.settings_theme_style_tonal_spot
            ThemeStyle.VIBRANT -> Res.string.settings_theme_style_vibrant
            ThemeStyle.EXPRESSIVE -> Res.string.settings_theme_style_expressive
            ThemeStyle.FIDELITY -> Res.string.settings_theme_style_fidelity
            ThemeStyle.CONTENT -> Res.string.settings_theme_style_content
            ThemeStyle.NEUTRAL -> Res.string.settings_theme_style_neutral
            ThemeStyle.MONOCHROME -> Res.string.settings_theme_style_monochrome
        }
    )

    private fun ThemeStyle.description() = res(
        when (this) {
            ThemeStyle.TONAL_SPOT -> Res.string.settings_theme_style_tonal_spot_description
            ThemeStyle.VIBRANT -> Res.string.settings_theme_style_vibrant_description
            ThemeStyle.EXPRESSIVE -> Res.string.settings_theme_style_expressive_description
            ThemeStyle.FIDELITY -> Res.string.settings_theme_style_fidelity_description
            ThemeStyle.CONTENT -> Res.string.settings_theme_style_content_description
            ThemeStyle.NEUTRAL -> Res.string.settings_theme_style_neutral_description
            ThemeStyle.MONOCHROME -> Res.string.settings_theme_style_monochrome_description
        }
    )

    private fun ThemeContrast.label() = res(
        when (this) {
            ThemeContrast.STANDARD -> Res.string.settings_theme_contrast_standard
            ThemeContrast.MEDIUM -> Res.string.settings_theme_contrast_medium
            ThemeContrast.HIGH -> Res.string.settings_theme_contrast_high
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
