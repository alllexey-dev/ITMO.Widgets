package dev.alllexey.itmowidgets.feature.settings.di

import dev.alllexey.itmowidgets.core.schedule.SchedulePreferencesRepository
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.settings.WidgetAppearanceRepository
import dev.alllexey.itmowidgets.feature.settings.data.CustomServicesRepositoryImpl
import dev.alllexey.itmowidgets.feature.settings.data.SchedulePreferencesRepositoryImpl
import dev.alllexey.itmowidgets.feature.settings.data.SettingsRepositoryImpl
import dev.alllexey.itmowidgets.feature.settings.data.WidgetAppearanceRepositoryImpl
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.feature.settings.presentation.CustomSpoilerViewModel
import dev.alllexey.itmowidgets.feature.settings.presentation.DiagnosticsViewModel
import dev.alllexey.itmowidgets.feature.settings.presentation.HomePageProvider
import dev.alllexey.itmowidgets.feature.settings.presentation.IcsExportViewModel
import dev.alllexey.itmowidgets.feature.settings.presentation.MaintenancePageProvider
import dev.alllexey.itmowidgets.feature.settings.presentation.RecordbookPageProvider
import dev.alllexey.itmowidgets.feature.settings.presentation.RootPageProvider
import dev.alllexey.itmowidgets.feature.settings.presentation.SchedulePageProvider
import dev.alllexey.itmowidgets.feature.settings.presentation.ServicesPageProvider
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPages
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsViewModel
import dev.alllexey.itmowidgets.feature.settings.presentation.SportPageProvider
import dev.alllexey.itmowidgets.feature.settings.presentation.WidgetsPageProvider
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * The settings data Koin constructs, one single per repository: Hilt-built code (widgets, workers, the other
 * features' screens) reads the same instances through the app's `SettingsBridge`. The preference stores, the Backend
 * gate and client, the device session and the demo switch come from the app's `CoreBridge`, the widget refresher
 * from its `SettingsBridge`. A separate module from [settingsModule], so a debug fixture that reloads the screens
 * never builds a second repository.
 */
val settingsDataModule = module {
    singleOf(::SettingsRepositoryImpl) { bind<SettingsRepository>() }
    singleOf(::CustomServicesRepositoryImpl) { bind<CustomServicesRepository>() }
    singleOf(::WidgetAppearanceRepositoryImpl) { bind<WidgetAppearanceRepository>() }
    singleOf(::SchedulePreferencesRepositoryImpl) { bind<SchedulePreferencesRepository>() }
}

/**
 * The settings screens Koin constructs. The page providers and [SettingsPages] are factories: every
 * [SettingsViewModel] gets its own set, so stacked settings levels never share page state. The repositories come
 * from [settingsDataModule]; `WidgetRefreshRequester`, `AppVersion` and the platform accesses from the app's
 * `SettingsBridge`, the core contracts from its `CoreBridge`.
 */
val settingsModule = module {
    factoryOf(::RootPageProvider)
    factoryOf(::ServicesPageProvider)
    factoryOf(::WidgetsPageProvider)
    factoryOf(::HomePageProvider)
    factoryOf(::SchedulePageProvider)
    factoryOf(::RecordbookPageProvider)
    factoryOf(::SportPageProvider)
    factoryOf(::MaintenancePageProvider)
    factoryOf(::SettingsPages)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::IcsExportViewModel)
    viewModelOf(::DiagnosticsViewModel)
    viewModelOf(::CustomSpoilerViewModel)
}
