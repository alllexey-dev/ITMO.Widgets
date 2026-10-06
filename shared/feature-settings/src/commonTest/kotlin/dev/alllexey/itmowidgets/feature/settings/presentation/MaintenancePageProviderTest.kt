package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.core.app_unofficial_notice
import dev.alllexey.itmowidgets.shared.feature.settings.Res
import dev.alllexey.itmowidgets.shared.feature.settings.settings_privacy_policy_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_restart_onboarding_title
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

/** The maintenance page built and handled by [MaintenancePageProvider]. */
@OptIn(ExperimentalCoroutinesApi::class)
class MaintenancePageProviderTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun maintenanceOffersAReplayThatResetsTheFlagAndLeavesTheOverlay() =
        runTest(main.dispatcher) {
            val fixture = createFixture(page = SettingsPage.MAINTENANCE)
            advanceUntilIdle()
            assertEquals(
                UiText.Res(Res.string.settings_restart_onboarding_title),
                fixture.viewModel.action(SettingRowId.RESTART_ONBOARDING).title
            )

            fixture.viewModel.onAction(SettingRowId.RESTART_ONBOARDING)
            advanceUntilIdle()

            assertEquals(1, fixture.onboardingRepository.resetCount)
            assertEquals(false, fixture.onboardingRepository.completed.value)
            assertEquals(SettingsEvent.CloseOverlays, fixture.viewModel.events.first())
        }

    @Test
    fun maintenanceLinksThePrivacyPolicyBeforeTheVersionAndSaysTheAppIsUnofficial() =
        runTest(main.dispatcher) {
            val fixture = createFixture(page = SettingsPage.MAINTENANCE)
            advanceUntilIdle()

            val keys = fixture.viewModel.allItems().map { it.id }
            assertEquals(
                listOf(SettingRowId.PRIVACY_POLICY, SettingRowId.VERSION),
                keys.takeLast(2)
            )
            assertEquals(
                UiText.Res(Res.string.settings_privacy_policy_title),
                fixture.viewModel.action(SettingRowId.PRIVACY_POLICY).title
            )
            assertEquals(
                UiText.Res(CoreRes.string.app_unofficial_notice),
                fixture.viewModel.uiState.value.sections.single().footer
            )

            fixture.viewModel.onAction(SettingRowId.PRIVACY_POLICY)
            assertEquals(SettingsEvent.OpenWebPage("/privacy.html"), fixture.viewModel.events.first())
        }

    @Test
    fun theReplayActionStaysOutOfEveryOtherPage() = runTest(main.dispatcher) {
        val fixture = createFixture(page = SettingsPage.ROOT)
        advanceUntilIdle()

        assertTrue(
            fixture.viewModel.allItems().none { it.id == SettingRowId.RESTART_ONBOARDING }
        )
    }
}
