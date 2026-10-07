package dev.alllexey.itmowidgets.feature.settings.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.client.users.UsersApi
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.notification.FcmTokenSync
import dev.alllexey.itmowidgets.core.onboarding.OnboardingRepository
import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.core.recordbook.MarkTracking
import dev.alllexey.itmowidgets.core.schedule.CalendarSync
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeTracking
import dev.alllexey.itmowidgets.core.schedule.ScheduleIcsExport
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.session.BackendDeviceSession
import dev.alllexey.itmowidgets.core.session.BackendIdentitySync
import dev.alllexey.itmowidgets.core.settings.CustomSpoilerRepository
import dev.alllexey.itmowidgets.core.storage.DeviceHintPreferences
import dev.alllexey.itmowidgets.core.storage.HomeLayoutPreferences
import dev.alllexey.itmowidgets.core.storage.MarkSourcePreferences
import dev.alllexey.itmowidgets.core.storage.QrSettingsPreferences
import dev.alllexey.itmowidgets.core.storage.ScheduleCheckPreferences
import dev.alllexey.itmowidgets.core.storage.ServicesOptInPreferences
import dev.alllexey.itmowidgets.core.storage.SportSignSelectorPreferences
import dev.alllexey.itmowidgets.core.storage.WidgetSettingsPreferences
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.settings.domain.BackgroundWorkAccess
import dev.alllexey.itmowidgets.feature.settings.domain.QuickSettingsTileAccess
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import dev.alllexey.itmowidgets.feature.settings.presentation.AppVersion
import kotlin.test.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class)
class SettingsModuleTest {

    /** The repositories resolve over the core stores and clients the app's `CoreBridge` forwards. */
    @Test
    fun theSettingsDataModuleResolvesWithTheBridgedTypes() {
        settingsDataModule.verify(extraTypes = dataBridgedTypes)
    }

    /**
     * The page providers and the four ViewModels resolve over the settings data; the Android side of settings
     * comes from the app's `SettingsBridge`, the core contracts from its `CoreBridge`.
     */
    @Test
    fun theSettingsModulesResolveWithTheBridgedTypes() {
        module { includes(settingsDataModule, settingsModule) }.verify(
            extraTypes = dataBridgedTypes + listOf(
                AppVersion::class,
                BackgroundWorkAccess::class,
                QuickSettingsTileAccess::class,
                CustomSpoilerRepository::class,
                OnboardingRepository::class,
                ScheduleChangeTracking::class,
                MarkTracking::class,
                CalendarSync::class,
                ScheduleIcsExport::class,
                AcademicTimeProvider::class,
                SavedStateHandle::class,
                PlatformCapabilities::class,
            ),
        )
    }

    private val dataBridgedTypes = listOf(
        ServicesOptInPreferences::class,
        ScheduleCheckPreferences::class,
        WidgetSettingsPreferences::class,
        QrSettingsPreferences::class,
        SportSignSelectorPreferences::class,
        MarkSourcePreferences::class,
        HomeLayoutPreferences::class,
        DeviceHintPreferences::class,
        BackendGate::class,
        UsersApi::class,
        DemoMode::class,
        AppDispatchers::class,
        BackendIdentitySync::class,
        FcmTokenSync::class,
        BackendDeviceSession::class,
        AppDiagnostics::class,
        WidgetRefreshRequester::class,
    )
}
