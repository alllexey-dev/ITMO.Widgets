package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.schedule.SchedulePreferencesRepository
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.settings.WidgetAppearanceRepository
import dev.alllexey.itmowidgets.feature.settings.domain.BackgroundWorkAccess
import dev.alllexey.itmowidgets.core.settings.WidgetPaletteSource
import dev.alllexey.itmowidgets.feature.settings.domain.QuickSettingsTileAccess
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import dev.alllexey.itmowidgets.feature.settings.presentation.AppVersion
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Hilt to Koin for the Android side of settings: Hilt keeps constructing the widget refresher, the version, the
 * widgets' theme palette and the platform accesses, which `settingsModule` and `settingsDataModule` read (one graph per binding). The core contracts
 * come from `CoreBridge`.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsBridgeEntryPoint {
    fun widgetRefreshRequester(): WidgetRefreshRequester
    fun appVersion(): AppVersion
    fun backgroundWorkAccess(): BackgroundWorkAccess
    fun quickSettingsTileAccess(): QuickSettingsTileAccess
    fun widgetPaletteSource(): WidgetPaletteSource

    companion object {
        fun from(context: Context): SettingsBridgeEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, SettingsBridgeEntryPoint::class.java)
    }
}

/**
 * Lazy definitions: Koin starts before Hilt builds its component, so each one reads Hilt on first use. The
 * refresher is a Hilt singleton and cached as such; the version and the platform accesses are unscoped in Hilt, so
 * Koin asks Hilt again for every reader.
 */
val settingsBridgeModule = module {
    single<WidgetRefreshRequester> { SettingsBridgeEntryPoint.from(androidContext()).widgetRefreshRequester() }
    factory<AppVersion> { SettingsBridgeEntryPoint.from(androidContext()).appVersion() }
    factory<BackgroundWorkAccess> { SettingsBridgeEntryPoint.from(androidContext()).backgroundWorkAccess() }
    factory<QuickSettingsTileAccess> { SettingsBridgeEntryPoint.from(androidContext()).quickSettingsTileAccess() }
    factory<WidgetPaletteSource> { SettingsBridgeEntryPoint.from(androidContext()).widgetPaletteSource() }
}

/**
 * Koin to Hilt for the settings repositories `settingsDataModule` constructs that Hilt-built code still injects: the
 * opt-in (other features' screens, repositories and push handlers), the widget appearance (onboarding) and the
 * schedule preferences (the schedule screen and its home card). `SettingsRepository` has no Hilt reader. Unscoped on
 * purpose: Koin owns the lifetime and returns its single every time. `ensureStarted`, because a worker or a widget
 * broadcast can run before `Application.onCreate()`.
 */
@Module
@InstallIn(SingletonComponent::class)
object SettingsBridge {

    @Provides
    fun customServicesRepository(@ApplicationContext context: Context): CustomServicesRepository =
        KoinStarter.ensureStarted(context).get()

    @Provides
    fun widgetAppearanceRepository(@ApplicationContext context: Context): WidgetAppearanceRepository =
        KoinStarter.ensureStarted(context).get()

    @Provides
    fun schedulePreferencesRepository(@ApplicationContext context: Context): SchedulePreferencesRepository =
        KoinStarter.ensureStarted(context).get()
}
