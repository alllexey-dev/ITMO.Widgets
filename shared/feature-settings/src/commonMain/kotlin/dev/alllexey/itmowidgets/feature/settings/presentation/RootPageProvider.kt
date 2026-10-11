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
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.core.me_group_app
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
import dev.alllexey.itmowidgets.shared.feature.settings.settings_appearance_group_colors
import dev.alllexey.itmowidgets.shared.feature.settings.settings_appearance_group_contrast
import dev.alllexey.itmowidgets.shared.feature.settings.settings_appearance_wallpaper_footer
import dev.alllexey.itmowidgets.shared.feature.settings.settings_dark_black_description
import dev.alllexey.itmowidgets.shared.feature.settings.settings_dark_black_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_group_access
import dev.alllexey.itmowidgets.shared.feature.settings.settings_group_widgets
import dev.alllexey.itmowidgets.shared.feature.settings.settings_notifications_allowed
import dev.alllexey.itmowidgets.shared.feature.settings.settings_notifications_blocked
import dev.alllexey.itmowidgets.shared.feature.settings.settings_notifications_checking
import dev.alllexey.itmowidgets.shared.feature.settings.settings_notifications_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_qr_short_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_services_disabled
import dev.alllexey.itmowidgets.shared.feature.settings.settings_services_enabled
import dev.alllexey.itmowidgets.shared.feature.settings.settings_theme_contrast_high
import dev.alllexey.itmowidgets.shared.feature.settings.settings_theme_contrast_medium
import dev.alllexey.itmowidgets.shared.feature.settings.settings_theme_contrast_standard
import dev.alllexey.itmowidgets.shared.feature.settings.settings_theme_contrast_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_theme_style_content
import dev.alllexey.itmowidgets.shared.feature.settings.settings_theme_style_content_description
import dev.alllexey.itmowidgets.shared.feature.settings.settings_theme_style_expressive
import dev.alllexey.itmowidgets.shared.feature.settings.settings_theme_style_expressive_description
import dev.alllexey.itmowidgets.shared.feature.settings.settings_theme_style_fidelity
import dev.alllexey.itmowidgets.shared.feature.settings.settings_theme_style_fidelity_description
import dev.alllexey.itmowidgets.shared.feature.settings.settings_theme_style_monochrome
import dev.alllexey.itmowidgets.shared.feature.settings.settings_theme_style_monochrome_description
import dev.alllexey.itmowidgets.shared.feature.settings.settings_theme_style_neutral
import dev.alllexey.itmowidgets.shared.feature.settings.settings_theme_style_neutral_description
import dev.alllexey.itmowidgets.shared.feature.settings.settings_theme_style_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_theme_style_tonal_spot
import dev.alllexey.itmowidgets.shared.feature.settings.settings_theme_style_tonal_spot_description
import dev.alllexey.itmowidgets.shared.feature.settings.settings_theme_style_vibrant
import dev.alllexey.itmowidgets.shared.feature.settings.settings_theme_style_vibrant_description
import org.jetbrains.compose.resources.StringResource

/**
 * The catalogue: one row per detail page, plus the notification permission; and the appearance page, whose rows are
 * the stored [ThemeSpec] (DS-ACC1, DS-ACC2). The recordbook page holds only the mark checks, so it is listed where the
 * platform offers mark tracking.
 */
class RootPageProvider(
    private val repository: SettingsRepository,
    private val capabilities: PlatformCapabilities = EveryPlatformCapability
) : SettingsPageProvider {

    override val pages = setOf(SettingsPage.ROOT, SettingsPage.APPEARANCE)

    override val rows = setOf(
        SettingRowId.NOTIFICATIONS,
        SettingRowId.ACCENT_COLOR,
        SettingRowId.ACCENT_CUSTOM,
        SettingRowId.THEME_PALETTE,
        SettingRowId.THEME_CONTRAST,
        SettingRowId.DARK_BLACK
    )

    override fun sections(page: SettingsPage, state: SettingsPageState) = when (page) {
        SettingsPage.APPEARANCE -> appearance(state.local.theme)
        else -> root(state)
    }

    override fun themePreview(page: SettingsPage, local: LocalSettings): ThemeSpec? =
        local.theme.takeIf { page == SettingsPage.APPEARANCE }

    private fun root(state: SettingsPageState) = listOf(
        SettingSection(
            title = UiText.Res(Res.string.settings_group_access),
            items = listOf(
                SettingRows.navigation(
                    SettingsPage.SERVICES,
                    value = UiText.Res(
                        if (state.local.customServicesEnabled) {
                            Res.string.settings_services_enabled
                        } else {
                            Res.string.settings_services_disabled
                        }
                    )
                ),
                SettingRows.navigation(SettingsPage.PRIVACY),
                SettingItem.Action(
                    id = SettingRowId.NOTIFICATIONS,
                    title = UiText.Res(Res.string.settings_notifications_title),
                    value = UiText.Res(
                        when (state.notificationsGranted) {
                            true -> Res.string.settings_notifications_allowed
                            false -> Res.string.settings_notifications_blocked
                            null -> Res.string.settings_notifications_checking
                        }
                    ),
                    trailingIcon = AppIcon.CHEVRON_RIGHT
                )
            )
        ),
        SettingSection(
            title = UiText.Res(Res.string.settings_group_widgets),
            items = listOf(
                SettingRows.navigation(SettingsPage.COMPACT_SCHEDULE_WIDGET),
                SettingRows.navigation(SettingsPage.FULL_SCHEDULE_WIDGET),
                SettingRows.navigation(
                    SettingsPage.QR_WIDGET,
                    title = UiText.Res(Res.string.settings_qr_short_title)
                )
            )
        ),
        SettingSection(
            title = UiText.Res(CoreRes.string.me_group_app),
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
            title = UiText.Res(Res.string.settings_appearance_group_colors),
            items = listOfNotNull(
                accentColor(theme.accent),
                SettingItem.CustomColor(
                    id = SettingRowId.ACCENT_CUSTOM,
                    title = UiText.Res(Res.string.settings_accent_color_custom),
                    argb = theme.customArgb
                ).takeIf { theme.accent == AccentColor.CUSTOM },
                SettingItem.Choice(
                    id = SettingRowId.THEME_PALETTE,
                    title = UiText.Res(Res.string.settings_theme_style_title),
                    value = UiText.Res(theme.style.label),
                    options = ThemeStyle.entries.map {
                        ChoiceOption(it.name, UiText.Res(it.label), UiText.Res(it.description))
                    },
                    selectedOptionKey = theme.style.name
                )
            ),
            footer = UiText.Res(Res.string.settings_appearance_wallpaper_footer)
                .takeIf { capabilities.wallpaperColors && theme.accent == AccentColor.WALLPAPER }
        ),
        SettingSection(
            title = UiText.Res(Res.string.settings_appearance_group_contrast),
            items = listOf(
                SettingItem.Choice(
                    id = SettingRowId.THEME_CONTRAST,
                    title = UiText.Res(Res.string.settings_theme_contrast_title),
                    value = UiText.Res(theme.contrast.label),
                    options = ThemeContrast.entries.map { ChoiceOption(it.name, UiText.Res(it.label)) },
                    selectedOptionKey = theme.contrast.name
                ),
                SettingItem.Toggle(
                    id = SettingRowId.DARK_BLACK,
                    title = UiText.Res(Res.string.settings_dark_black_title),
                    description = UiText.Res(Res.string.settings_dark_black_description),
                    checked = theme.pureBlack
                )
            )
        )
    )

    override fun onAction(scope: SettingsPageScope, id: SettingRowId) {
        if (id == SettingRowId.NOTIFICATIONS) scope.send(SettingsEvent.OpenNotificationSettings)
    }

    override fun onToggleChanged(scope: SettingsPageScope, id: SettingRowId, checked: Boolean) {
        if (id == SettingRowId.DARK_BLACK) updateTheme(scope) { it.copy(pureBlack = checked) }
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

    private fun updateTheme(scope: SettingsPageScope, change: (ThemeSpec) -> ThemeSpec) {
        scope.updateLocalSetting { repository.updateTheme(change) }
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
            title = UiText.Res(Res.string.settings_accent_color_title),
            value = UiText.Res(selected.label),
            options = options.map { ChoiceOption(it.name, UiText.Res(it.label)) },
            selectedOptionKey = selected.name
        )
    }

    private val AccentColor.label: StringResource
        get() = when (this) {
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

    private val ThemeStyle.label: StringResource
        get() = when (this) {
            ThemeStyle.TONAL_SPOT -> Res.string.settings_theme_style_tonal_spot
            ThemeStyle.VIBRANT -> Res.string.settings_theme_style_vibrant
            ThemeStyle.EXPRESSIVE -> Res.string.settings_theme_style_expressive
            ThemeStyle.FIDELITY -> Res.string.settings_theme_style_fidelity
            ThemeStyle.CONTENT -> Res.string.settings_theme_style_content
            ThemeStyle.NEUTRAL -> Res.string.settings_theme_style_neutral
            ThemeStyle.MONOCHROME -> Res.string.settings_theme_style_monochrome
        }

    private val ThemeStyle.description: StringResource
        get() = when (this) {
            ThemeStyle.TONAL_SPOT -> Res.string.settings_theme_style_tonal_spot_description
            ThemeStyle.VIBRANT -> Res.string.settings_theme_style_vibrant_description
            ThemeStyle.EXPRESSIVE -> Res.string.settings_theme_style_expressive_description
            ThemeStyle.FIDELITY -> Res.string.settings_theme_style_fidelity_description
            ThemeStyle.CONTENT -> Res.string.settings_theme_style_content_description
            ThemeStyle.NEUTRAL -> Res.string.settings_theme_style_neutral_description
            ThemeStyle.MONOCHROME -> Res.string.settings_theme_style_monochrome_description
        }

    private val ThemeContrast.label: StringResource
        get() = when (this) {
            ThemeContrast.STANDARD -> Res.string.settings_theme_contrast_standard
            ThemeContrast.MEDIUM -> Res.string.settings_theme_contrast_medium
            ThemeContrast.HIGH -> Res.string.settings_theme_contrast_high
        }
}
