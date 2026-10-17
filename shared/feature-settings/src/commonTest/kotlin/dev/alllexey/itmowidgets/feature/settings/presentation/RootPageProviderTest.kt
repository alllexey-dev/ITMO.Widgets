package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.text.UiText
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
import kotlinx.coroutines.flow.first
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
    fun rootIsACompactCatalogueWithOneSwitchAndNoArbitraryOptionSummaries() =
        runTest(main.dispatcher) {
            val fixture = createFixture(local = LocalSettings(customServicesEnabled = true))
            advanceUntilIdle()

            assertEquals(SettingsPage.ROOT, fixture.viewModel.uiState.value.page)
            assertEquals(3, fixture.viewModel.uiState.value.sections.size)
            assertEquals(11, fixture.viewModel.allItems().size)
            assertEquals(
                listOf(SettingRowId.WIDGETS_FOLLOW_THEME),
                fixture.viewModel.allItems().filterIsInstance<SettingItem.Toggle>().map { it.id }
            )
            val navigation = fixture.viewModel.allItems().filterIsInstance<SettingItem.Navigation>()
            assertEquals(
                SettingsPage.entries.filter { it != SettingsPage.ROOT && it != SettingsPage.PRIVACY },
                navigation.map { it.page }
            )
            assertTrue(navigation.all { it.description == null })
            assertTrue(navigation.filter { it.page != SettingsPage.SERVICES }.all { it.value == null })
            assertEquals(UiText.Res(SettingsStrings.settings_services_enabled), navigation.first().value)
            val applicationSection = fixture.viewModel.uiState.value.sections.single {
                it.title == UiText.Res(CoreStrings.me_group_app)
            }
            assertEquals(
                listOf(
                    SettingsPage.APPEARANCE,
                    SettingsPage.HOME,
                    SettingsPage.SCHEDULE,
                    SettingsPage.SPORT,
                    SettingsPage.MAINTENANCE
                ),
                applicationSection.items.filterIsInstance<SettingItem.Navigation>().map { it.page }
            )
            assertEquals(null, fixture.viewModel.uiState.value.themePreview)
            assertEquals(0, fixture.repository.refreshSharingCount)
        }

    @Test
    fun homePassCanBeHiddenAndShownWithoutRefreshingWidgets() = runTest(main.dispatcher) {
        val fixture = createFixture(page = SettingsPage.HOME)
        advanceUntilIdle()
        assertTrue(fixture.viewModel.toggle(SettingRowId.HOME_PASS_VISIBLE).checked)

        fixture.viewModel.onToggleChanged(SettingRowId.HOME_PASS_VISIBLE, false)
        advanceUntilIdle()
        assertFalse(fixture.viewModel.toggle(SettingRowId.HOME_PASS_VISIBLE).checked)
        assertFalse(fixture.repository.local.value.homePassVisible)

        fixture.viewModel.onToggleChanged(SettingRowId.HOME_PASS_VISIBLE, true)
        advanceUntilIdle()
        assertTrue(fixture.viewModel.toggle(SettingRowId.HOME_PASS_VISIBLE).checked)
        assertEquals(0, fixture.widgetRefresher.refreshCount)
    }

    @Test
    fun theNotificationsRowSaysOnlyWhenTheSystemDeniesThemAndMaintenanceShowsTheVersion() =
        runTest(main.dispatcher) {
            val fixture = createFixture()
            advanceUntilIdle()
            val row = {
                fixture.viewModel.allItems().filterIsInstance<SettingItem.Navigation>()
                    .single { it.page == SettingsPage.NOTIFICATIONS }
            }

            assertEquals(null, row().value)
            fixture.viewModel.onNotificationPermissionChanged(granted = false)
            advanceUntilIdle()
            assertEquals(UiText.Res(SettingsStrings.settings_notifications_blocked), row().value)
            fixture.viewModel.onNotificationPermissionChanged(granted = true)
            advanceUntilIdle()
            assertEquals(null, row().value)

            val maintenance = createFixture(page = SettingsPage.MAINTENANCE)
            advanceUntilIdle()
            val version = maintenance.viewModel.allItems()
                .filterIsInstance<SettingItem.Info>()
                .single { it.id == SettingRowId.VERSION }
            assertEquals(UiText.Dynamic("2.1-test"), version.value)
        }

    @Test
    fun theAccessGroupLeadsToTheConnectionAndTheNotifications() =
        runTest(main.dispatcher) {
            val fixture = createFixture()
            advanceUntilIdle()

            val access = fixture.viewModel.uiState.value.sections.first()
            assertEquals(
                listOf(SettingsPage.SERVICES, SettingsPage.NOTIFICATIONS),
                access.items.filterIsInstance<SettingItem.Navigation>().map { it.page }
            )
            assertEquals(UiText.Res(SettingsStrings.connection_title), SettingsPage.SERVICES.title)
            assertEquals(UiText.Res(SettingsStrings.notifications_title), SettingsPage.NOTIFICATIONS.title)
        }

    @Test
    fun widgetsFollowTheThemeOnlyWhenTurnedOnInTheWidgetsGroup() =
        runTest(main.dispatcher) {
            val fixture = createFixture()
            advanceUntilIdle()

            val widgets = fixture.viewModel.uiState.value.sections.single {
                it.title == UiText.Res(SettingsStrings.settings_group_widgets)
            }
            assertEquals(SettingRowId.WIDGETS_FOLLOW_THEME, widgets.items.last().id)
            val toggle = fixture.viewModel.toggle(SettingRowId.WIDGETS_FOLLOW_THEME)
            assertEquals(UiText.Res(SettingsStrings.settings_widgets_theme_title), toggle.title)
            assertFalse(toggle.checked)

            fixture.viewModel.onToggleChanged(SettingRowId.WIDGETS_FOLLOW_THEME, true)
            advanceUntilIdle()

            assertEquals(listOf(true), fixture.repository.widgetsFollowThemeRequests)
            assertTrue(fixture.viewModel.toggle(SettingRowId.WIDGETS_FOLLOW_THEME).checked)
            assertEquals(1, fixture.widgetRefresher.refreshCount)
        }
}
