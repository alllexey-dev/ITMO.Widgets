package dev.alllexey.itmowidgets.feature.settings.ui

import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettingsState
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPage
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsUiState
import dev.alllexey.itmowidgets.feature.settings.presentation.createFixture
import dev.alllexey.itmowidgets.feature.settings.ui.preview.SettingsPreviewData
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/**
 * The previews draw exactly what the page providers build for the preview states, so the baselines stay a
 * picture of the real pages: custom services on, notifications allowed, everything else the fixture's default.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsPreviewDataTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun everyPagePreviewIsWhatItsProviderBuilds() {
        val pages = mapOf(
            SettingsPage.ROOT to SettingsPreviewData.Root,
            SettingsPage.WIDGETS to SettingsPreviewData.widgets(local = LocalSettings(customServicesEnabled = true)),
            SettingsPage.SECTIONS to SettingsPreviewData.Sections,
            SettingsPage.DEVICES to SettingsPreviewData.Devices,
            SettingsPage.ABOUT to SettingsPreviewData.About,
            SettingsPage.LICENSES to SettingsPreviewData.Licenses,
            SettingsPage.APPEARANCE to SettingsPreviewData.Appearance,
        )
        for ((page, preview) in pages) {
            assertEquals(preview, built(page), "$page")
        }
    }

    private fun built(
        page: SettingsPage,
        local: LocalSettings = LocalSettings(customServicesEnabled = true),
        sharing: SharingSettingsState = SharingSettingsState.Disabled,
    ): SettingsUiState {
        lateinit var state: SettingsUiState
        runTest(main.dispatcher) {
            val fixture = createFixture(local = local, sharing = sharing, page = page)
            fixture.viewModel.onNotificationPermissionChanged(true)
            advanceUntilIdle()
            state = fixture.viewModel.uiState.value
        }
        return state
    }
}
