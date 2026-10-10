package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.core.me_group_app
import dev.alllexey.itmowidgets.shared.core.title_recordbook
import dev.alllexey.itmowidgets.shared.feature.settings.Res
import dev.alllexey.itmowidgets.shared.feature.settings.settings_accent_color_brand
import dev.alllexey.itmowidgets.shared.feature.settings.settings_accent_color_teal
import dev.alllexey.itmowidgets.shared.feature.settings.settings_accent_color_wallpaper
import dev.alllexey.itmowidgets.shared.feature.settings.settings_notifications_allowed
import dev.alllexey.itmowidgets.shared.feature.settings.settings_notifications_checking
import dev.alllexey.itmowidgets.shared.feature.settings.settings_services_enabled
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun rootIsACompactCatalogueWithNoSwitchesOrArbitraryOptionSummaries() =
        runTest(main.dispatcher) {
            val fixture = createFixture(local = LocalSettings(customServicesEnabled = true))
            advanceUntilIdle()

            assertEquals(SettingsPage.ROOT, fixture.viewModel.uiState.value.page)
            assertEquals(3, fixture.viewModel.uiState.value.sections.size)
            assertEquals(12, fixture.viewModel.allItems().size)
            assertTrue(fixture.viewModel.allItems().none { it is SettingItem.Toggle })
            val navigation = fixture.viewModel.allItems().filterIsInstance<SettingItem.Navigation>()
            assertEquals(
                SettingsPage.entries.filter { it != SettingsPage.ROOT },
                navigation.map { it.page }
            )
            assertTrue(navigation.all { it.description == null })
            assertTrue(navigation.filter { it.page != SettingsPage.SERVICES }.all { it.value == null })
            assertEquals(UiText.Res(Res.string.settings_services_enabled), navigation.first().value)
            val applicationSection = fixture.viewModel.uiState.value.sections.single {
                it.title == UiText.Res(CoreRes.string.me_group_app)
            }
            assertEquals(SettingRowId.ACCENT_COLOR, applicationSection.items.first().id)
            assertEquals(
                listOf(SettingsPage.HOME, SettingsPage.SCHEDULE, SettingsPage.RECORDBOOK, SettingsPage.SPORT, SettingsPage.MAINTENANCE),
                applicationSection.items.filterIsInstance<SettingItem.Navigation>().map { it.page }
            )
            assertEquals(0, fixture.repository.refreshSharingCount)
        }

    @Test
    fun exposesNotificationStatusAndApplicationVersion() =
        runTest(main.dispatcher) {
            val fixture = createFixture()
            advanceUntilIdle()

            assertEquals(
                UiText.Res(Res.string.settings_notifications_checking),
                fixture.viewModel.action(SettingRowId.NOTIFICATIONS).value
            )
            fixture.viewModel.onNotificationPermissionChanged(granted = true)
            advanceUntilIdle()

            assertEquals(
                UiText.Res(Res.string.settings_notifications_allowed),
                fixture.viewModel.action(SettingRowId.NOTIFICATIONS).value
            )
            val maintenance = createFixture(page = SettingsPage.MAINTENANCE)
            advanceUntilIdle()
            val version = maintenance.viewModel.allItems()
                .filterIsInstance<SettingItem.Info>()
                .single { it.id == SettingRowId.VERSION }
            assertEquals(UiText.Dynamic("2.1-test"), version.value)
        }

    @Test
    fun rootListsTheRecordbookBetweenScheduleAndSport() =
        runTest(main.dispatcher) {
            val fixture = createFixture()
            advanceUntilIdle()

            val pages = fixture.viewModel.allItems().filterIsInstance<SettingItem.Navigation>().map { it.page }
            val index = pages.indexOf(SettingsPage.RECORDBOOK)
            assertEquals(SettingsPage.SCHEDULE, pages[index - 1])
            assertEquals(SettingsPage.SPORT, pages[index + 1])
            assertEquals(UiText.Res(CoreRes.string.title_recordbook), SettingsPage.RECORDBOOK.title)
        }

    @Test
    fun accentColorDefaultsToTheWallpaperWhereThereIsOne() =
        runTest(main.dispatcher) {
            val fixture = createFixture()
            advanceUntilIdle()

            val choice = fixture.viewModel.choice(SettingRowId.ACCENT_COLOR)
            assertEquals(AccentColor.entries.map { it.name }, choice.options.map { it.key })
            assertEquals(AccentColor.WALLPAPER.name, choice.selectedOptionKey)
            assertEquals(UiText.Res(Res.string.settings_accent_color_wallpaper), choice.value)
        }

    @Test
    fun withoutWallpaperColoursTheDefaultShowsAsTheBrandScheme() =
        runTest(main.dispatcher) {
            val fixture = createFixture(capabilities = EveryPlatformCapability.copy(wallpaperColors = false))
            advanceUntilIdle()

            val choice = fixture.viewModel.choice(SettingRowId.ACCENT_COLOR)
            assertEquals(AccentColor.entries.drop(1).map { it.name }, choice.options.map { it.key })
            assertEquals(AccentColor.BRAND.name, choice.selectedOptionKey)
            assertEquals(UiText.Res(Res.string.settings_accent_color_brand), choice.value)
        }

    @Test
    fun pickingAColourStoresItAndTheRowFollows() =
        runTest(main.dispatcher) {
            val fixture = createFixture()
            advanceUntilIdle()

            fixture.viewModel.onChoiceChanged(SettingRowId.ACCENT_COLOR, AccentColor.TEAL.name)
            fixture.viewModel.onChoiceChanged(SettingRowId.ACCENT_COLOR, "NOT_A_COLOUR")
            advanceUntilIdle()

            assertEquals(listOf(AccentColor.TEAL), fixture.repository.accentColorRequests)
            val choice = fixture.viewModel.choice(SettingRowId.ACCENT_COLOR)
            assertEquals(AccentColor.TEAL.name, choice.selectedOptionKey)
            assertEquals(UiText.Res(Res.string.settings_accent_color_teal), choice.value)
        }
}
