package dev.alllexey.itmowidgets.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.AndroidWidgetPaletteSource
import dev.alllexey.itmowidgets.app.WidgetRefreshCoordinator
import dev.alllexey.itmowidgets.app.DefaultWidgetPreviewFactory
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreviewFactory
import dev.alllexey.itmowidgets.feature.settings.data.AndroidBackgroundWorkAccess
import dev.alllexey.itmowidgets.feature.settings.data.AndroidQuickSettingsTileAccess
import dev.alllexey.itmowidgets.feature.settings.data.CustomSpoilerRepositoryImpl
import dev.alllexey.itmowidgets.core.settings.CustomSpoilerRepository
import dev.alllexey.itmowidgets.core.settings.WidgetPaletteSource
import dev.alllexey.itmowidgets.feature.settings.domain.BackgroundWorkAccess
import dev.alllexey.itmowidgets.feature.settings.domain.QuickSettingsTileAccess
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import dev.alllexey.itmowidgets.feature.settings.presentation.AppVersion
import javax.inject.Singleton

/**
 * The Android side of settings: the widget preview, the custom spoiler store, the platform accesses, the widget
 * refresher, the widgets' theme palette and the version. The settings repositories are Koin's (`settingsDataModule`), bridged by `SettingsBridge`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {

    @Binds
    abstract fun bindWidgetPreviewFactory(impl: DefaultWidgetPreviewFactory): WidgetPreviewFactory

    @Binds
    @Singleton
    abstract fun bindCustomSpoilerRepository(impl: CustomSpoilerRepositoryImpl): CustomSpoilerRepository

    @Binds
    abstract fun bindBackgroundWorkAccess(impl: AndroidBackgroundWorkAccess): BackgroundWorkAccess

    @Binds
    abstract fun bindQuickSettingsTileAccess(impl: AndroidQuickSettingsTileAccess): QuickSettingsTileAccess

    @Binds
    @Singleton
    abstract fun bindWidgetRefreshRequester(
        impl: WidgetRefreshCoordinator
    ): WidgetRefreshRequester

    @Binds
    abstract fun bindWidgetPaletteSource(impl: AndroidWidgetPaletteSource): WidgetPaletteSource

    companion object {

        @Provides
        fun provideAppVersion(
            @ApplicationContext context: Context
        ): AppVersion = AppVersion(context.getString(R.string.app_version))
    }
}
