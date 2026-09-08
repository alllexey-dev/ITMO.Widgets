package dev.alllexey.itmowidgets.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.WidgetRefreshCoordinator
import dev.alllexey.itmowidgets.app.DefaultWidgetPreviewFactory
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreviewFactory
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.schedule.SchedulePreferencesRepository
import dev.alllexey.itmowidgets.feature.settings.data.CustomServicesRepositoryImpl
import dev.alllexey.itmowidgets.feature.settings.data.CustomSpoilerRepositoryImpl
import dev.alllexey.itmowidgets.feature.settings.data.SettingsRepositoryImpl
import dev.alllexey.itmowidgets.feature.settings.data.SchedulePreferencesRepositoryImpl
import dev.alllexey.itmowidgets.feature.settings.domain.CustomSpoilerRepository
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import dev.alllexey.itmowidgets.feature.settings.presentation.AppVersion
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {

    @Binds
    abstract fun bindWidgetPreviewFactory(impl: DefaultWidgetPreviewFactory): WidgetPreviewFactory

    @Binds
    @Singleton
    abstract fun bindCustomSpoilerRepository(impl: CustomSpoilerRepositoryImpl): CustomSpoilerRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindSchedulePreferencesRepository(
        impl: SchedulePreferencesRepositoryImpl
    ): SchedulePreferencesRepository

    @Binds
    @Singleton
    abstract fun bindCustomServicesRepository(
        impl: CustomServicesRepositoryImpl
    ): CustomServicesRepository

    @Binds
    @Singleton
    abstract fun bindWidgetRefreshRequester(
        impl: WidgetRefreshCoordinator
    ): WidgetRefreshRequester

    companion object {

        @Provides
        fun provideAppVersion(
            @ApplicationContext context: Context
        ): AppVersion = AppVersion(context.getString(R.string.app_version))
    }
}
