package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
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
import dev.alllexey.itmowidgets.shared.feature.settings.settings_group_access
import dev.alllexey.itmowidgets.shared.feature.settings.settings_group_widgets
import dev.alllexey.itmowidgets.shared.feature.settings.settings_notifications_allowed
import dev.alllexey.itmowidgets.shared.feature.settings.settings_notifications_blocked
import dev.alllexey.itmowidgets.shared.feature.settings.settings_notifications_checking
import dev.alllexey.itmowidgets.shared.feature.settings.settings_notifications_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_qr_short_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_services_disabled
import dev.alllexey.itmowidgets.shared.feature.settings.settings_services_enabled
import org.jetbrains.compose.resources.StringResource

/**
 * The catalogue: one row per detail page, plus the notification permission and the accent colour. The recordbook
 * page holds only the mark checks, so it is listed where the platform offers mark tracking.
 */
class RootPageProvider(
    private val repository: SettingsRepository,
    private val capabilities: PlatformCapabilities = EveryPlatformCapability
) : SettingsPageProvider {

    override val pages = setOf(SettingsPage.ROOT)

    override val rows = setOf(SettingRowId.NOTIFICATIONS, SettingRowId.ACCENT_COLOR)

    override fun sections(page: SettingsPage, state: SettingsPageState) = listOf(
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
                accentColor(state.local.theme.accent),
                SettingRows.navigation(SettingsPage.HOME),
                SettingRows.navigation(SettingsPage.SCHEDULE),
                SettingRows.navigation(SettingsPage.RECORDBOOK).takeIf { capabilities.marks },
                SettingRows.navigation(SettingsPage.SPORT),
                SettingRows.navigation(SettingsPage.MAINTENANCE)
            )
        )
    )

    override fun onAction(scope: SettingsPageScope, id: SettingRowId) {
        if (id == SettingRowId.NOTIFICATIONS) scope.send(SettingsEvent.OpenNotificationSettings)
    }

    override fun onChoiceChanged(scope: SettingsPageScope, id: SettingRowId, optionKey: String) {
        if (id != SettingRowId.ACCENT_COLOR) return
        val color = AccentColor.entries.firstOrNull { it.name == optionKey } ?: return
        scope.updateLocalSetting { repository.updateTheme { it.copy(accent = color) } }
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
}
