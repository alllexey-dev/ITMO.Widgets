package dev.alllexey.itmowidgets.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.feature.settings.presentation.AppVersion
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.feature.settings.data.CustomServicesRepositoryImpl
import dev.alllexey.itmowidgets.feature.settings.data.SettingsRepositoryImpl
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindCustomServicesRepository(
        impl: CustomServicesRepositoryImpl
    ): CustomServicesRepository

    companion object {

        @Provides
        fun provideAppVersion(
            @ApplicationContext context: Context
        ): AppVersion = AppVersion(context.getString(R.string.app_version))
    }
}
