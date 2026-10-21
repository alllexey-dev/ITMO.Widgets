package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.BarsConnection
import dev.alllexey.itmowidgets.feature.settings.domain.BarsStatusRepository
import dev.alllexey.itmowidgets.feature.settings.presentation.appearance.label
import dev.alllexey.itmowidgets.feature.settings.presentation.notifications.notificationSwitches
import dev.alllexey.itmowidgets.feature.settings.presentation.notifications.notificationsSummary
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.settings.SettingsStrings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The catalogue: the accounts (the server with its state, BARS with its sign-in), then one row per page with what it
 * holds in a word (the notifications that are on, the placed widgets, the accent). «Оформление» is a screen of its own
 * (`AppearanceViewModel`).
 */
class RootPageProvider(
    private val barsStatus: BarsStatusRepository,
    private val capabilities: PlatformCapabilities = EveryPlatformCapability
) : SettingsPageProvider {

    override val pages = setOf(SettingsPage.ROOT)

    override val rows = setOf(SettingRowId.BARS)

    /** Only the root reads BARS. */
    override fun observeState(page: SettingsPage, state: Flow<SettingsPageState>): Flow<SettingsPageState> =
        combine(state, barsStatus.observe()) { pageState, bars -> pageState.copy(bars = bars) }

    override fun sections(page: SettingsPage, state: SettingsPageState) = listOf(
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
                    ),
                    icon = if (state.local.customServicesEnabled) AppIcon.CLOUD_DONE else AppIcon.CLOUD_OFF
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
                    ),
                    icon = AppIcon.NOTIFICATION
                ),
                SettingRows.navigation(
                    SettingsPage.WIDGETS,
                    value = placedWidgetsSummary(state.placedWidgets, capabilities),
                    icon = AppIcon.WIDGETS
                ),
                SettingRows.navigation(SettingsPage.APPEARANCE, value = accentName(state.local.theme), icon = AppIcon.PALETTE),
                SettingRows.navigation(SettingsPage.SECTIONS, icon = AppIcon.TUNE)
            )
        ),
        SettingSection(
            title = null,
            items = listOf(SettingRows.navigation(SettingsPage.ABOUT, icon = AppIcon.INFO))
        )
    )

    /** Without the wallpaper's colours (below Android 12, iOS) the stored default draws as the brand scheme. */
    private fun accentName(theme: ThemeSpec): UiText {
        val accent = theme.accent.takeUnless { it == AccentColor.WALLPAPER && !capabilities.wallpaperColors }
        return (accent ?: AccentColor.BRAND).label
    }

    /** Connected BARS only says so; otherwise the row opens the sign-in. */
    private fun bars(bars: BarsConnection): SettingItem {
        val title = UiText.Res(SettingsStrings.settings_bars_title)
        return when (bars) {
            BarsConnection.CONNECTED ->
                SettingItem.Info(SettingRowId.BARS, title, UiText.Res(SettingsStrings.settings_bars_connected), icon = AppIcon.GRADING)
            BarsConnection.EXPIRED, BarsConnection.NOT_CONNECTED -> SettingItem.Action(
                id = SettingRowId.BARS,
                title = title,
                value = UiText.Res(
                    if (bars == BarsConnection.EXPIRED) SettingsStrings.settings_bars_expired else SettingsStrings.settings_bars_off
                ),
                trailingIcon = AppIcon.CHEVRON_RIGHT,
                icon = AppIcon.GRADING
            )
        }
    }

    override fun onAction(scope: SettingsPageScope, id: SettingRowId) {
        if (id == SettingRowId.BARS) scope.send(SettingsEvent.OpenBarsLogin)
    }
}
