package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.core.settings.HexColor
import dev.alllexey.itmowidgets.core.settings.ThemeContrast
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.settings.ThemeStyle
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.BarsConnection
import dev.alllexey.itmowidgets.feature.settings.domain.BarsStatusRepository
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.feature.settings.presentation.notifications.notificationSwitches
import dev.alllexey.itmowidgets.feature.settings.presentation.notifications.notificationsSummary
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.settings.SettingsStrings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The catalogue: the accounts (the server with its state, BARS with its sign-in), then one row per page with what it
 * holds in a word (the notifications that are on, the placed widgets, the accent); and the appearance page, whose
 * rows are the stored [ThemeSpec] (DS-ACC1, DS-ACC2).
 */
class RootPageProvider(
    private val repository: SettingsRepository,
    private val barsStatus: BarsStatusRepository,
    private val capabilities: PlatformCapabilities = EveryPlatformCapability
) : SettingsPageProvider {

    override val pages = setOf(SettingsPage.ROOT, SettingsPage.APPEARANCE)

    override val rows = setOf(
        SettingRowId.BARS,
        SettingRowId.ACCENT_COLOR,
        SettingRowId.ACCENT_CUSTOM,
        SettingRowId.THEME_PALETTE,
        SettingRowId.THEME_CONTRAST,
        SettingRowId.DARK_BLACK,
    )

    /** Only the root reads BARS. */
    override fun observeState(page: SettingsPage, state: Flow<SettingsPageState>): Flow<SettingsPageState> =
        if (page == SettingsPage.ROOT) {
            combine(state, barsStatus.observe()) { pageState, bars -> pageState.copy(bars = bars) }
        } else {
            state
        }

    override fun sections(page: SettingsPage, state: SettingsPageState) = when (page) {
        SettingsPage.APPEARANCE -> appearance(state.local.theme)
        else -> root(state)
    }

    override fun themePreview(page: SettingsPage, local: LocalSettings): ThemeSpec? =
        local.theme.takeIf { page == SettingsPage.APPEARANCE }

    private fun root(state: SettingsPageState) = listOf(
        SettingSection(
            title = UiText.Res(SettingsStrings.settings_group_accounts),
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
                bars(state.bars)
            )
        ),
        SettingSection(
            title = UiText.Res(CoreStrings.me_group_app),
            items = listOf(
                SettingRows.navigation(
                    SettingsPage.NOTIFICATIONS,
                    value = notificationsSummary(
                        denied = state.notificationsGranted == false,
                        switches = notificationSwitches(state.local, capabilities)
                    )
                ),
                SettingRows.navigation(SettingsPage.WIDGETS, value = placedWidgetsSummary(state.placedWidgets, capabilities)),
                SettingRows.navigation(SettingsPage.APPEARANCE, value = accentColor(state.local.theme.accent).value),
                SettingRows.navigation(SettingsPage.SECTIONS)
            )
        ),
        SettingSection(
            title = null,
            items = listOf(SettingRows.navigation(SettingsPage.ABOUT))
        )
    )

    /** Connected BARS only says so; otherwise the row opens the sign-in. */
    private fun bars(bars: BarsConnection): SettingItem {
        val title = UiText.Res(SettingsStrings.settings_bars_title)
        return when (bars) {
            BarsConnection.CONNECTED -> SettingItem.Info(SettingRowId.BARS, title, UiText.Res(SettingsStrings.settings_bars_connected))
            BarsConnection.EXPIRED, BarsConnection.NOT_CONNECTED -> SettingItem.Action(
                id = SettingRowId.BARS,
                title = title,
                value = UiText.Res(
                    if (bars == BarsConnection.EXPIRED) SettingsStrings.settings_bars_expired else SettingsStrings.settings_bars_off
                ),
                trailingIcon = AppIcon.CHEVRON_RIGHT
            )
        }
    }

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

    override fun onToggleChanged(scope: SettingsPageScope, id: SettingRowId, checked: Boolean) {
        if (id == SettingRowId.DARK_BLACK) updateTheme(scope) { it.copy(pureBlack = checked) }
    }

    override fun onAction(scope: SettingsPageScope, id: SettingRowId) {
        if (id == SettingRowId.BARS) scope.send(SettingsEvent.OpenBarsLogin)
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
