package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.core.settings.HexColor
import dev.alllexey.itmowidgets.core.settings.ThemeContrast
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.settings.ThemeStyle
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.settings.SettingsStrings

/**
 * The catalogue: one row per detail page, plus the notification permission and whether the widgets take the app's
 * theme (DS-ACC3); and the appearance page, whose rows are the stored [ThemeSpec] (DS-ACC1, DS-ACC2). The recordbook
 * page holds only the mark checks, so it is listed where the platform offers mark tracking.
 */
class RootPageProvider(
    private val repository: SettingsRepository,
    private val capabilities: PlatformCapabilities = EveryPlatformCapability
) : SettingsPageProvider {

    override val pages = setOf(SettingsPage.ROOT, SettingsPage.APPEARANCE, SettingsPage.HOME)

    override val rows = setOf(
        SettingRowId.NOTIFICATIONS,
        SettingRowId.CONNECTIONS,
        SettingRowId.ACCENT_COLOR,
        SettingRowId.ACCENT_CUSTOM,
        SettingRowId.THEME_PALETTE,
        SettingRowId.THEME_CONTRAST,
        SettingRowId.DARK_BLACK,
        SettingRowId.HOME_PASS_VISIBLE,
    )

    override fun sections(page: SettingsPage, state: SettingsPageState) = when (page) {
        SettingsPage.APPEARANCE -> appearance(state.local.theme)
        SettingsPage.HOME -> listOf(
            SettingSection(
                title = null,
                items = listOf(SettingItem.Toggle(
                    id = SettingRowId.HOME_PASS_VISIBLE,
                    title = UiText.Res(SettingsStrings.settings_home_pass_title),
                    checked = state.local.homePassVisible,
                )),
                footer = UiText.Res(SettingsStrings.settings_home_pass_description),
            )
        )
        else -> root(state)
    }

    override fun themePreview(page: SettingsPage, local: LocalSettings): ThemeSpec? =
        local.theme.takeIf { page == SettingsPage.APPEARANCE }

    private fun root(state: SettingsPageState) = listOf(
        SettingSection(
            title = UiText.Res(SettingsStrings.settings_group_access),
            items = listOf(
                SettingRows.navigation(
                    SettingsPage.SERVICES,
                    value = UiText.Res(
                        if (state.local.customServicesEnabled) {
                            SettingsStrings.settings_services_enabled
                        } else {
                            SettingsStrings.settings_services_disabled
                        }
                    )
                ),
                SettingRows.navigation(SettingsPage.PRIVACY),
                SettingItem.Action(
                    id = SettingRowId.CONNECTIONS,
                    title = UiText.Res(SettingsStrings.settings_connections_title),
                    trailingIcon = AppIcon.CHEVRON_RIGHT
                ),
                SettingItem.Action(
                    id = SettingRowId.NOTIFICATIONS,
                    title = UiText.Res(SettingsStrings.settings_notifications_title),
                    value = UiText.Res(
                        when (state.notificationsGranted) {
                            true -> SettingsStrings.settings_notifications_allowed
                            false -> SettingsStrings.settings_notifications_blocked
                            null -> SettingsStrings.settings_notifications_checking
                        }
                    ),
                    trailingIcon = AppIcon.CHEVRON_RIGHT
                )
            )
        ),
        SettingSection(
            title = UiText.Res(SettingsStrings.settings_group_widgets),
            items = listOf(SettingRows.navigation(SettingsPage.WIDGETS))
        ),
        SettingSection(
            title = UiText.Res(CoreStrings.me_group_app),
            items = listOfNotNull(
                SettingRows.navigation(SettingsPage.APPEARANCE),
                SettingRows.navigation(SettingsPage.HOME),
                SettingRows.navigation(SettingsPage.SCHEDULE),
                SettingRows.navigation(SettingsPage.RECORDBOOK).takeIf { capabilities.marks },
                SettingRows.navigation(SettingsPage.SPORT),
                SettingRows.navigation(SettingsPage.MAINTENANCE)
            )
        )
    )

    /**
     * The colours, then contrast and the black background. The custom colour row shows only while it is the accent.
     * Where the wallpaper has colours, a footer says that a style or contrast rebuilds them (`resolveColorScheme`).
     */
    private fun appearance(theme: ThemeSpec) = listOf(
        SettingSection(
            title = UiText.Res(SettingsStrings.settings_appearance_group_colors),
            items = listOfNotNull(
                accentColor(theme.accent),
                SettingItem.CustomColor(
                    id = SettingRowId.ACCENT_CUSTOM,
                    title = UiText.Res(SettingsStrings.settings_accent_color_custom),
                    argb = theme.customArgb
                ).takeIf { theme.accent == AccentColor.CUSTOM },
                SettingItem.Choice(
                    id = SettingRowId.THEME_PALETTE,
                    title = UiText.Res(SettingsStrings.settings_theme_style_title),
                    value = theme.style.label,
                    options = ThemeStyle.entries.map {
                        ChoiceOption(it.name, it.label, it.description)
                    },
                    selectedOptionKey = theme.style.name
                )
            ),
            footer = UiText.Res(SettingsStrings.settings_appearance_wallpaper_footer)
                .takeIf { capabilities.wallpaperColors && theme.accent == AccentColor.WALLPAPER }
        ),
        SettingSection(
            title = UiText.Res(SettingsStrings.settings_appearance_group_contrast),
            items = listOf(
                SettingItem.Choice(
                    id = SettingRowId.THEME_CONTRAST,
                    title = UiText.Res(SettingsStrings.settings_theme_contrast_title),
                    value = theme.contrast.label,
                    options = ThemeContrast.entries.map { ChoiceOption(it.name, it.label) },
                    selectedOptionKey = theme.contrast.name
                ),
                SettingItem.Toggle(
                    id = SettingRowId.DARK_BLACK,
                    title = UiText.Res(SettingsStrings.settings_dark_black_title),
                    description = UiText.Res(SettingsStrings.settings_dark_black_description),
                    checked = theme.pureBlack
                )
            )
        )
    )

    override fun onAction(scope: SettingsPageScope, id: SettingRowId) {
        when (id) {
            SettingRowId.NOTIFICATIONS -> scope.send(SettingsEvent.OpenNotificationSettings)
            SettingRowId.CONNECTIONS -> scope.send(SettingsEvent.OpenConnections)
            else -> Unit
        }
    }

    override fun onToggleChanged(scope: SettingsPageScope, id: SettingRowId, checked: Boolean) {
        when (id) {
            SettingRowId.HOME_PASS_VISIBLE -> scope.updateLocalSetting { repository.setHomePassVisible(checked) }
            SettingRowId.DARK_BLACK -> updateTheme(scope) { it.copy(pureBlack = checked) }
            else -> Unit
        }
    }

    /** The custom colour row reports its colour as `#RRGGBB`; picking it also makes it the accent. */
    override fun onChoiceChanged(scope: SettingsPageScope, id: SettingRowId, optionKey: String) {
        when (id) {
            SettingRowId.ACCENT_COLOR -> AccentColor.entries.firstOrNull { it.name == optionKey }
                ?.let { color -> updateTheme(scope) { it.copy(accent = color) } }
            SettingRowId.ACCENT_CUSTOM -> HexColor.parse(optionKey)
                ?.let { argb -> updateTheme(scope) { it.copy(accent = AccentColor.CUSTOM, customArgb = argb) } }
            SettingRowId.THEME_PALETTE -> ThemeStyle.entries.firstOrNull { it.name == optionKey }
                ?.let { style -> updateTheme(scope) { it.copy(style = style) } }
            SettingRowId.THEME_CONTRAST -> ThemeContrast.entries.firstOrNull { it.name == optionKey }
                ?.let { contrast -> updateTheme(scope) { it.copy(contrast = contrast) } }
            else -> Unit
        }
    }

    /** Widgets that follow the theme redraw with every change of it. */
    private fun updateTheme(scope: SettingsPageScope, change: (ThemeSpec) -> ThemeSpec) {
        scope.updateWidgetSetting { repository.updateTheme(change) }
    }

    /**
     * The choice of the scheme's seed. Without the wallpaper's colours (below Android 12, iOS) the stored default
     * shows as the brand scheme, which is what the theme draws then.
     */
    private fun accentColor(stored: AccentColor): SettingItem.Choice {
        val options = AccentColor.entries.filter { it != AccentColor.WALLPAPER || capabilities.wallpaperColors }
        val selected = stored.takeIf { it in options } ?: AccentColor.BRAND
        return SettingItem.Choice(
            id = SettingRowId.ACCENT_COLOR,
            title = UiText.Res(SettingsStrings.settings_accent_color_title),
            value = selected.label,
            options = options.map { ChoiceOption(it.name, it.label) },
            selectedOptionKey = selected.name
        )
    }

    private val AccentColor.label: UiText
        get() = UiText.Res(
            when (this) {
                AccentColor.WALLPAPER -> SettingsStrings.settings_accent_color_wallpaper
                AccentColor.BRAND -> SettingsStrings.settings_accent_color_brand
                AccentColor.TEAL -> SettingsStrings.settings_accent_color_teal
                AccentColor.GREEN -> SettingsStrings.settings_accent_color_green
                AccentColor.AMBER -> SettingsStrings.settings_accent_color_amber
                AccentColor.RED -> SettingsStrings.settings_accent_color_red
                AccentColor.PINK -> SettingsStrings.settings_accent_color_pink
                AccentColor.PURPLE -> SettingsStrings.settings_accent_color_purple
                AccentColor.CUSTOM -> SettingsStrings.settings_accent_color_custom
            }
        )

    private val ThemeStyle.label: UiText
        get() = UiText.Res(
            when (this) {
                ThemeStyle.TONAL_SPOT -> SettingsStrings.settings_theme_style_tonal_spot
                ThemeStyle.VIBRANT -> SettingsStrings.settings_theme_style_vibrant
                ThemeStyle.EXPRESSIVE -> SettingsStrings.settings_theme_style_expressive
                ThemeStyle.FIDELITY -> SettingsStrings.settings_theme_style_fidelity
                ThemeStyle.CONTENT -> SettingsStrings.settings_theme_style_content
                ThemeStyle.NEUTRAL -> SettingsStrings.settings_theme_style_neutral
                ThemeStyle.MONOCHROME -> SettingsStrings.settings_theme_style_monochrome
            }
        )

    private val ThemeStyle.description: UiText
        get() = UiText.Res(
            when (this) {
                ThemeStyle.TONAL_SPOT -> SettingsStrings.settings_theme_style_tonal_spot_description
                ThemeStyle.VIBRANT -> SettingsStrings.settings_theme_style_vibrant_description
                ThemeStyle.EXPRESSIVE -> SettingsStrings.settings_theme_style_expressive_description
                ThemeStyle.FIDELITY -> SettingsStrings.settings_theme_style_fidelity_description
                ThemeStyle.CONTENT -> SettingsStrings.settings_theme_style_content_description
                ThemeStyle.NEUTRAL -> SettingsStrings.settings_theme_style_neutral_description
                ThemeStyle.MONOCHROME -> SettingsStrings.settings_theme_style_monochrome_description
            }
        )

    private val ThemeContrast.label: UiText
        get() = UiText.Res(
            when (this) {
                ThemeContrast.STANDARD -> SettingsStrings.settings_theme_contrast_standard
                ThemeContrast.MEDIUM -> SettingsStrings.settings_theme_contrast_medium
                ThemeContrast.HIGH -> SettingsStrings.settings_theme_contrast_high
            }
        )
}
