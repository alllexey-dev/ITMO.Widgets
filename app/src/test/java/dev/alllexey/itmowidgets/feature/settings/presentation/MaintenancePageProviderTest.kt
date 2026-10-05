package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.text.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The maintenance page built and handled by [MaintenancePageProvider]. */
@OptIn(ExperimentalCoroutinesApi::class)
class MaintenancePageProviderTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `maintenance offers a replay that resets the flag and leaves the overlay`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.MAINTENANCE)
            advanceUntilIdle()
            assertEquals(
                UiText.Resource(R.string.settings_restart_onboarding_title),
                fixture.viewModel.action(SettingRowId.RESTART_ONBOARDING).title
            )

            fixture.viewModel.onAction(SettingRowId.RESTART_ONBOARDING)
            advanceUntilIdle()

            assertEquals(1, fixture.onboardingRepository.resetCount)
            assertEquals(false, fixture.onboardingRepository.completed.value)
            assertEquals(SettingsEvent.CloseOverlays, fixture.viewModel.events.first())
        }

    @Test
    fun `maintenance links the privacy policy before the version and says the app is unofficial`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.MAINTENANCE)
            advanceUntilIdle()

            val keys = fixture.viewModel.allItems().map { it.id }
            assertEquals(
                listOf(SettingRowId.PRIVACY_POLICY, SettingRowId.VERSION),
                keys.takeLast(2)
            )
            assertEquals(
                UiText.Resource(R.string.settings_privacy_policy_title),
                fixture.viewModel.action(SettingRowId.PRIVACY_POLICY).title
            )
            assertEquals(
                UiText.Resource(R.string.app_unofficial_notice),
                fixture.viewModel.uiState.value.sections.single().footer
            )

            fixture.viewModel.onAction(SettingRowId.PRIVACY_POLICY)
            assertEquals(SettingsEvent.OpenWebPage("/privacy.html"), fixture.viewModel.events.first())
        }

    @Test
    fun `the replay action stays out of every other page`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture(page = SettingsPage.ROOT)
        advanceUntilIdle()

        assertTrue(
            fixture.viewModel.allItems().none { it.id == SettingRowId.RESTART_ONBOARDING }
        )
    }
}
