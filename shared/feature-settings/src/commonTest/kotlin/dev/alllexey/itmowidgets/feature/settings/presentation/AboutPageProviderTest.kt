package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.platform.AppBuild
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.settings.SettingsStrings
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
class AboutPageProviderTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun aboutOffersAReplayThatResetsTheFlagAndLeavesTheOverlay() =
        runTest(main.dispatcher) {
            val fixture = createFixture(page = SettingsPage.ABOUT)
            advanceUntilIdle()
            assertEquals(
                UiText.Res(SettingsStrings.settings_restart_onboarding_title),
                fixture.viewModel.action(SettingRowId.RESTART_ONBOARDING).title
            )

            fixture.viewModel.onAction(SettingRowId.RESTART_ONBOARDING)
            advanceUntilIdle()

            assertEquals(1, fixture.onboardingRepository.resetCount)
            assertEquals(false, fixture.onboardingRepository.completed.value)
            assertEquals(SettingsEvent.CloseOverlays, fixture.viewModel.events.first())
        }

    @Test
    fun aboutLinksThePrivacyPolicyBeforeTheVersionAndSaysTheAppIsUnofficial() =
        runTest(main.dispatcher) {
            val fixture = createFixture(page = SettingsPage.ABOUT)
            advanceUntilIdle()

            val keys = fixture.viewModel.allItems().map { it.id }
            assertEquals(
                listOf(SettingRowId.PRIVACY_POLICY, SettingRowId.PAGE_LICENSES, SettingRowId.VERSION),
                keys.takeLast(3)
            )
            assertEquals(
                UiText.Res(SettingsStrings.settings_privacy_policy_title),
                fixture.viewModel.action(SettingRowId.PRIVACY_POLICY).title
            )
            assertEquals(
                UiText.Res(CoreStrings.app_unofficial_notice),
                fixture.viewModel.uiState.value.sections.single().footer
            )

            fixture.viewModel.onAction(SettingRowId.PRIVACY_POLICY)
            assertEquals(SettingsEvent.OpenWebPage("/privacy.html"), fixture.viewModel.events.first())
        }

    @Test
    fun theVersionShowsTheCommitAndWhereACustomBuildComesFromAndATapCopiesTheDetails() =
        runTest(main.dispatcher) {
            val official = AppBuild("2.3.0", "20292", "play", "alllexey-dev/ITMO.Widgets", "a1b2c3d4e5f6")
            val local = AppBuild("2.3.0", "20292", AppBuild.CUSTOM, AppBuild.LOCAL)
            val numbers = UiText.Dynamic("2.3.0 (20292)")

            assertEquals(UiText.Joined(listOf(numbers, UiText.Dynamic("a1b2c3d")), " · "), versionSummary(official))
            assertEquals(
                UiText.Joined(listOf(numbers, UiText.Res(SettingsStrings.settings_version_own_build)), " · "),
                versionSummary(local)
            )
            assertEquals(
                UiText.Joined(
                    listOf(
                        UiText.Dynamic("2.1-test (20100)"),
                        UiText.Dynamic("a1b2c3d"),
                        UiText.Dynamic("AlexPer777/ITMO.Widgets")
                    ),
                    " · "
                ),
                versionSummary(TEST_BUILD)
            )

            val fixture = createFixture(page = SettingsPage.ABOUT)
            advanceUntilIdle()
            fixture.viewModel.onAction(SettingRowId.VERSION)
            assertEquals(
                SettingsEvent.CopyText(TEST_BUILD.details(), UiText.Res(SettingsStrings.settings_version_copied)),
                fixture.viewModel.events.first()
            )
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
