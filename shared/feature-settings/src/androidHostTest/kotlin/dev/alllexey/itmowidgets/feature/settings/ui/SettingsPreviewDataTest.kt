package dev.alllexey.itmowidgets.feature.settings.ui

import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettings
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
 * The previews draw exactly what the page providers build for LT-3a's reference states, so the baselines stay a
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
            SettingsPage.SERVICES to SettingsPreviewData.Services,
            SettingsPage.COMPACT_SCHEDULE_WIDGET to SettingsPreviewData.CompactScheduleWidget,
            SettingsPage.FULL_SCHEDULE_WIDGET to SettingsPreviewData.FullScheduleWidget,
            SettingsPage.QR_WIDGET to SettingsPreviewData.QrWidget,
            SettingsPage.HOME to SettingsPreviewData.Home,
            SettingsPage.SCHEDULE to SettingsPreviewData.Schedule,
            SettingsPage.RECORDBOOK to SettingsPreviewData.Recordbook,
            SettingsPage.SPORT to SettingsPreviewData.Sport,
            SettingsPage.MAINTENANCE to SettingsPreviewData.Maintenance,
        )
        for ((page, preview) in pages) {
            assertEquals(preview, built(page), "$page")
        }
    }

    @Test
    fun privacyPreviewsAreWhatThePrivacyProviderBuilds() {
        assertEquals(
            SettingsPreviewData.Privacy,
            built(SettingsPage.PRIVACY, sharing = SharingSettingsState.Content(SharingSettings())),
        )
        assertEquals(SettingsPreviewData.PrivacyError, built(SettingsPage.PRIVACY, sharing = SharingSettingsState.Error))
        assertEquals(
            SettingsPreviewData.PrivacyDisabled,
            built(SettingsPage.PRIVACY, local = LocalSettings(customServicesEnabled = false)),
        )
        assertEquals(
            SettingsPreviewData.PrivacyLoading,
            built(SettingsPage.PRIVACY, sharing = SharingSettingsState.Loading),
        )
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
