package dev.alllexey.itmowidgets.feature.settings.di

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
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * The settings screens Koin constructs. The page providers and [SettingsPages] are factories: every
 * [SettingsViewModel] gets its own set, so stacked settings levels never share page state. The settings data
 * (`SettingsRepository`, `WidgetRefreshRequester`, `AppVersion`, the platform accesses) still comes from the app's
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
