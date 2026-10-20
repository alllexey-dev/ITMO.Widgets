package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.settings.WidgetFormat
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.BarsConnection
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.settings.SettingsStrings
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/** The catalogue built by [RootPageProvider]. */
@OptIn(ExperimentalCoroutinesApi::class)
class RootPageProviderTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun rootIsACompactCatalogueWithoutSwitches() =
        runTest(main.dispatcher) {
            val fixture = createFixture(local = LocalSettings(customServicesEnabled = true))
            advanceUntilIdle()

            val sections = fixture.viewModel.uiState.value.sections
            assertEquals(SettingsPage.ROOT, fixture.viewModel.uiState.value.page)
            assertEquals(
                listOf(UiText.Res(SettingsStrings.settings_group_accounts), UiText.Res(CoreStrings.me_group_app), null),
                sections.map { it.title }
            )
            assertEquals(
                listOf(
                    SettingRowId.PAGE_SERVICES,
                    SettingRowId.BARS,
                    SettingRowId.PAGE_NOTIFICATIONS,
                    SettingRowId.PAGE_WIDGETS,
                    SettingRowId.PAGE_APPEARANCE,
                    SettingRowId.PAGE_SECTIONS,
                    SettingRowId.PAGE_ABOUT
                ),
                fixture.viewModel.allItems().map { it.id }
            )
            assertTrue(fixture.viewModel.allItems().none { it is SettingItem.Toggle })
            assertEquals(UiText.Res(SettingsStrings.settings_services_enabled), navigation(fixture, SettingsPage.SERVICES).value)
            assertEquals(UiText.Res(SettingsStrings.settings_accent_color_wallpaper), navigation(fixture, SettingsPage.APPEARANCE).value)
            assertEquals(null, navigation(fixture, SettingsPage.SECTIONS).value)
            // Every row has its icon, as on the profile.
            assertTrue(fixture.viewModel.allItems().all { it.icon != null })
            assertEquals(0, fixture.repository.refreshSharingCount)
        }

    @Test
    fun theBarsRowOpensTheSignInUntilBarsIsConnected() = runTest(main.dispatcher) {
        val fixture = createFixture()
        val events = recordEvents(fixture)
        advanceUntilIdle()

        val off = fixture.viewModel.action(SettingRowId.BARS)
        assertEquals(UiText.Res(SettingsStrings.settings_bars_off), off.value)
        fixture.viewModel.onAction(SettingRowId.BARS)
        advanceUntilIdle()
        assertEquals(listOf<SettingsEvent>(SettingsEvent.OpenBarsLogin), events)

        fixture.bars.value = BarsConnection.EXPIRED
        advanceUntilIdle()
        assertEquals(UiText.Res(SettingsStrings.settings_bars_expired), fixture.viewModel.action(SettingRowId.BARS).value)

        fixture.bars.value = BarsConnection.CONNECTED
        advanceUntilIdle()
        val connected = fixture.viewModel.allItems().single { it.id == SettingRowId.BARS }
        assertEquals(
            SettingItem.Info(
                SettingRowId.BARS,
                UiText.Res(SettingsStrings.settings_bars_title),
                UiText.Res(SettingsStrings.settings_bars_connected),
                icon = AppIcon.GRADING,
            ),
            connected,
        )
    }

    @Test
    fun theNotificationsRowCountsTheTypesThatAreOnOrSaysTheSystemDeniesThem() =
        runTest(main.dispatcher) {
            val fixture = createFixture()
            advanceUntilIdle()
            val row = { navigation(fixture, SettingsPage.NOTIFICATIONS) }

            // Not connected: the marks and the schedule changes, both on by default.
            assertEquals(UiText.Res(SettingsStrings.notifications_subtitle_count, listOf(2, 2)), row().value)
            fixture.viewModel.onNotificationPermissionChanged(granted = false)
            advanceUntilIdle()
            assertEquals(UiText.Res(SettingsStrings.notifications_subtitle_denied), row().value)
            fixture.viewModel.onNotificationPermissionChanged(granted = true)
            fixture.repository.local.value = fixture.repository.local.value.copy(
                customServicesEnabled = true,
                disabledPushes = setOf(AppNotificationChannels.FRIENDS),
            )
            advanceUntilIdle()
            assertEquals(UiText.Res(SettingsStrings.notifications_subtitle_count, listOf(4, 5)), row().value)
        }

    @Test
    fun theWidgetsRowCountsThePlacedWidgets() = runTest(main.dispatcher) {
        val fixture = createFixture()
        advanceUntilIdle()
        assertEquals(null, navigation(fixture, SettingsPage.WIDGETS).value)

        fixture.viewModel.onPlacedWidgets(WidgetFormat.DAY, 2)
        fixture.viewModel.onPlacedWidgets(WidgetFormat.PASS, 1)
        advanceUntilIdle()
        assertEquals(UiText.Res(SettingsStrings.settings_widgets_placed, listOf(3)), navigation(fixture, SettingsPage.WIDGETS).value)
    }

    @Test
    fun withoutWallpaperColoursTheAppearanceRowNamesTheBrandColour() = runTest(main.dispatcher) {
        val fixture = createFixture(capabilities = EveryPlatformCapability.copy(wallpaperColors = false))
        advanceUntilIdle()
        assertEquals(UiText.Res(SettingsStrings.settings_accent_color_brand), navigation(fixture, SettingsPage.APPEARANCE).value)
    }

    @Test
    fun theAboutPageShowsTheVersion() = runTest(main.dispatcher) {
        val about = createFixture(page = SettingsPage.ABOUT)
        advanceUntilIdle()
        assertEquals(versionSummary(TEST_BUILD), about.viewModel.action(SettingRowId.VERSION).description)
        assertEquals(UiText.Res(SettingsStrings.connection_title), SettingsPage.SERVICES.title)
        assertEquals(UiText.Res(SettingsStrings.notifications_title), SettingsPage.NOTIFICATIONS.title)
    }

    @Test
    fun theDevicesPageOpensTheWebSignInAndTheConnectedDevices() = runTest(main.dispatcher) {
        val fixture = createFixture(page = SettingsPage.DEVICES)
        val events = recordEvents(fixture)
        advanceUntilIdle()
        assertEquals(listOf(SettingRowId.PAIR_DEVICE, SettingRowId.CONNECTED_DEVICES), fixture.viewModel.allItems().map { it.id })

        fixture.viewModel.onAction(SettingRowId.PAIR_DEVICE)
        fixture.viewModel.onAction(SettingRowId.CONNECTED_DEVICES)
        advanceUntilIdle()
        assertEquals(
            listOf<SettingsEvent>(SettingsEvent.OpenRoute(AppRoutes.WebLogin()), SettingsEvent.OpenRoute(AppRoutes.Connections)),
            events
        )
    }

    @Test
    fun theWidgetsFollowTheThemeFromTheGallery() =
        runTest(main.dispatcher) {
            val fixture = createFixture(page = SettingsPage.WIDGETS)
            advanceUntilIdle()
            val toggle = fixture.viewModel.toggle(SettingRowId.WIDGETS_FOLLOW_THEME)
            assertEquals(UiText.Res(SettingsStrings.settings_widgets_theme_title), toggle.title)
            assertFalse(toggle.checked)

            fixture.viewModel.onToggleChanged(SettingRowId.WIDGETS_FOLLOW_THEME, true)
            advanceUntilIdle()

            assertEquals(listOf(true), fixture.repository.widgetsFollowThemeRequests)
            assertTrue(fixture.viewModel.toggle(SettingRowId.WIDGETS_FOLLOW_THEME).checked)
            assertEquals(1, fixture.widgetRefresher.refreshCount)
        }

    private fun navigation(fixture: Fixture, page: SettingsPage): SettingItem.Navigation =
        fixture.viewModel.allItems().filterIsInstance<SettingItem.Navigation>().single { it.page == page }
}
