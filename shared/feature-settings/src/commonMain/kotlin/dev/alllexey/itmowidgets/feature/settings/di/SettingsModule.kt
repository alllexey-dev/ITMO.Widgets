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
 * The settings data Koin constructs, one single per repository. The preference stores, the Backend gate and client,
 * the device session and the demo switch come from the platform's Koin modules, the widget refresher from
 * `androidScheduleModule` (`settingsIosModule` on iOS). A separate module from [settingsModule], so a debug fixture
 * that reloads the screens never builds a second repository.
 */
val settingsDataModule = module {
    singleOf(::SettingsRepositoryImpl) { bind<SettingsRepository>() }
    singleOf(::CustomServicesRepositoryImpl) { bind<CustomServicesRepository>() }
    singleOf(::WidgetAppearanceRepositoryImpl) { bind<WidgetAppearanceRepository>() }
    singleOf(::SchedulePreferencesRepositoryImpl) { bind<SchedulePreferencesRepository>() }
}

/**
 * The settings screens Koin constructs. The page providers and [SettingsPages] are factories: every [SettingsViewModel]
 * gets its own set, so stacked settings levels never share page state. The repositories come from [settingsDataModule];
 * `AppVersion` and the platform accesses from `androidSettingsModule` (`settingsIosModule` on iOS), the core contracts,
 * `PlatformCapabilities` included, from the platform's core bindings (`iosCoreModule` on iOS). The providers that hide
 * a row by capability are built explicitly: their capabilities parameter has a default for hand-built providers, which
 * a constructor reference would make ambiguous.
 */
val settingsModule = module {
    factory { RootPageProvider(get(), get()) }
    factoryOf(::ServicesPageProvider)
    factory { WidgetsPageProvider(get(), get(), get()) }
    factoryOf(::HomePageProvider)
    factory { SchedulePageProvider(get(), get(), get(), get()) }
    factoryOf(::RecordbookPageProvider)
    factoryOf(::SportPageProvider)
    factoryOf(::MaintenancePageProvider)
    factoryOf(::SettingsPages)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::IcsExportViewModel)
    viewModelOf(::DiagnosticsViewModel)
    viewModelOf(::CustomSpoilerViewModel)
}
