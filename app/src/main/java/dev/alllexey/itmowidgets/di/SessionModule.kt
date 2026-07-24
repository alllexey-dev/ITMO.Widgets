package dev.alllexey.itmowidgets.di

import api.myitmo.MyItmo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.session.BackendIdentitySync
import dev.alllexey.itmowidgets.core.session.DefaultBackendIdentitySync
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SessionModule {

    @Provides
    @Singleton
    fun provideBackendIdentitySync(
        settings: AppSettingsStorage,
        myItmo: MyItmo,
        widgetsApi: ItmoWidgetsApi
    ): BackendIdentitySync = DefaultBackendIdentitySync(
        settings = settings,
        myItmo = myItmo,
        widgetsApi = widgetsApi
    )
}
