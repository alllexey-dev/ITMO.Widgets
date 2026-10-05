package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The catalogue built by [RootPageProvider]. */
@OptIn(ExperimentalCoroutinesApi::class)
class RootPageProviderTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `root is a compact catalogue with no switches or arbitrary option summaries`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(local = LocalSettings(customServicesEnabled = true))
            advanceUntilIdle()

            assertEquals(SettingsPage.ROOT, fixture.viewModel.uiState.value.page)
            assertEquals(3, fixture.viewModel.uiState.value.sections.size)
            assertEquals(11, fixture.viewModel.allItems().size)
            assertTrue(fixture.viewModel.allItems().none { it is SettingItem.Toggle })
            val navigation = fixture.viewModel.allItems().filterIsInstance<SettingItem.Navigation>()
            assertEquals(
                SettingsPage.entries.filter { it != SettingsPage.ROOT },
                navigation.map { it.page }
            )
            assertTrue(navigation.all { it.description == null })
            assertTrue(navigation.filter { it.page != SettingsPage.SERVICES }.all { it.value == null })
            assertEquals(UiText.Resource(R.string.settings_services_enabled), navigation.first().value)
            val applicationSection = fixture.viewModel.uiState.value.sections.single {
                it.title == UiText.Resource(R.string.me_group_app)
            }
            assertEquals(
                listOf(SettingsPage.HOME, SettingsPage.SCHEDULE, SettingsPage.RECORDBOOK, SettingsPage.SPORT, SettingsPage.MAINTENANCE),
                applicationSection.items.filterIsInstance<SettingItem.Navigation>().map { it.page }
            )
            assertEquals(0, fixture.repository.refreshSharingCount)
        }

    @Test
    fun `exposes notification status and application version`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture()
            advanceUntilIdle()

            assertEquals(
                UiText.Resource(R.string.settings_notifications_checking),
                fixture.viewModel.action(SettingRowId.NOTIFICATIONS).value
            )
            fixture.viewModel.onNotificationPermissionChanged(granted = true)
            advanceUntilIdle()

            assertEquals(
                UiText.Resource(R.string.settings_notifications_allowed),
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
    fun `root lists the recordbook between schedule and sport`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture()
            advanceUntilIdle()

            val pages = fixture.viewModel.allItems().filterIsInstance<SettingItem.Navigation>().map { it.page }
            val index = pages.indexOf(SettingsPage.RECORDBOOK)
            assertEquals(SettingsPage.SCHEDULE, pages[index - 1])
            assertEquals(SettingsPage.SPORT, pages[index + 1])
            assertEquals(UiText.Resource(R.string.title_recordbook), SettingsPage.RECORDBOOK.title)
        }
}
