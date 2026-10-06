package dev.alllexey.itmowidgets.feature.settings.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.onboarding.OnboardingRepository
import dev.alllexey.itmowidgets.core.recordbook.MarkTracking
import dev.alllexey.itmowidgets.core.schedule.CalendarSync
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeTracking
import dev.alllexey.itmowidgets.core.schedule.ScheduleIcsExport
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.settings.CustomSpoilerRepository
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.settings.domain.BackgroundWorkAccess
import dev.alllexey.itmowidgets.feature.settings.domain.QuickSettingsTileAccess
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import dev.alllexey.itmowidgets.feature.settings.presentation.AppVersion
import kotlin.test.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class SettingsModuleTest {

    /**
     * The page providers and the four ViewModels resolve inside the module; the settings data comes from the app's
     * `SettingsBridge`, the core contracts from its `CoreBridge`.
     */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun theSettingsModuleResolvesWithTheBridgedTypes() {
        settingsModule.verify(
            extraTypes = listOf(
                SettingsRepository::class,
                WidgetRefreshRequester::class,
                AppVersion::class,
                BackgroundWorkAccess::class,
                QuickSettingsTileAccess::class,
                CustomServicesRepository::class,
                CustomSpoilerRepository::class,
                OnboardingRepository::class,
                ScheduleChangeTracking::class,
                MarkTracking::class,
                CalendarSync::class,
                ScheduleIcsExport::class,
                AcademicTimeProvider::class,
                AppDiagnostics::class,
                SavedStateHandle::class,
            ),
        )
    }
}
