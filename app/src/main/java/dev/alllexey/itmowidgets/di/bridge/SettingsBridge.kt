package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.feature.settings.domain.BackgroundWorkAccess
import dev.alllexey.itmowidgets.feature.settings.domain.QuickSettingsTileAccess
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import dev.alllexey.itmowidgets.feature.settings.presentation.AppVersion
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Hilt to Koin for the settings data, which stays on Hilt until KM-11e: `settingsModule` builds the screens, Hilt
 * keeps constructing the repository, the widget refresher, the version and the platform accesses (one graph per
 * binding). The core contracts the settings ViewModels read come from `CoreBridge`.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsBridgeEntryPoint {
    fun settingsRepository(): SettingsRepository
    fun widgetRefreshRequester(): WidgetRefreshRequester
    fun appVersion(): AppVersion
    fun backgroundWorkAccess(): BackgroundWorkAccess
    fun quickSettingsTileAccess(): QuickSettingsTileAccess

    companion object {
        fun from(context: Context): SettingsBridgeEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, SettingsBridgeEntryPoint::class.java)
    }
}

/**
 * Lazy definitions: Koin starts before Hilt builds its component, so each one reads Hilt on first use. The
 * repository and the refresher are Hilt singletons and cached as such; the version and the platform accesses are
 * unscoped in Hilt, so Koin asks Hilt again for every reader.
 */
val settingsBridgeModule = module {
    single<SettingsRepository> { SettingsBridgeEntryPoint.from(androidContext()).settingsRepository() }
    single<WidgetRefreshRequester> { SettingsBridgeEntryPoint.from(androidContext()).widgetRefreshRequester() }
    factory<AppVersion> { SettingsBridgeEntryPoint.from(androidContext()).appVersion() }
    factory<BackgroundWorkAccess> { SettingsBridgeEntryPoint.from(androidContext()).backgroundWorkAccess() }
    factory<QuickSettingsTileAccess> { SettingsBridgeEntryPoint.from(androidContext()).quickSettingsTileAccess() }
}
