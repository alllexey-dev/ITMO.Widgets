package dev.alllexey.itmowidgets.di

import android.content.Context
import android.os.Build
import api.myitmo.MyItmo
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.session.BackendDeviceSession
import dev.alllexey.itmowidgets.core.session.BackendIdentitySync
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.DefaultBackendDeviceSession
import dev.alllexey.itmowidgets.core.session.DefaultBackendIdentitySync
import dev.alllexey.itmowidgets.core.session.IdTokenCurrentUserProvider
import dev.alllexey.itmowidgets.core.session.SessionLifecycleEffects
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.storage.UtilityStorage
import dev.alllexey.itmowidgets.app.AndroidSessionLifecycleEffects
import dev.alllexey.itmowidgets.feature.auth.data.DefaultRefreshTokenAuthenticator
import dev.alllexey.itmowidgets.feature.auth.data.RefreshTokenAuthenticator
import dev.alllexey.itmowidgets.feature.auth.data.SessionRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SessionModule {

    @Provides
    @Singleton
    fun provideBackendIdentitySync(
        @ApplicationContext context: Context,
        settings: AppSettingsStorage,
        myItmo: MyItmo,
        widgetsApi: ItmoWidgetsApi,
        diagnostics: AppDiagnostics
    ): BackendIdentitySync = DefaultBackendIdentitySync(
        context = context,
        settings = settings,
        myItmo = myItmo,
        widgetsApi = widgetsApi,
        diagnostics = diagnostics
    )

    @Provides
    @Singleton
    fun provideCurrentUserProvider(
        tokenStore: SessionTokenStore,
        gson: Gson
    ): CurrentUserProvider = IdTokenCurrentUserProvider(
        tokenStore = tokenStore,
        gson = gson
    )

    @Provides
    @Singleton
    fun provideBackendDeviceSession(
        settings: AppSettingsStorage,
        utilityStorage: UtilityStorage,
        currentUser: CurrentUserProvider,
        widgetsApi: ItmoWidgetsApi
    ): BackendDeviceSession = DefaultBackendDeviceSession(
        settings = settings,
        utilityStorage = utilityStorage,
        currentUser = currentUser,
        widgetsApi = widgetsApi,
        deviceName = listOf(Build.MANUFACTURER, Build.MODEL)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .joinToString(" ")
            .ifBlank { "Android" }
    )

    @Provides
    @Singleton
    fun provideRefreshTokenAuthenticator(
        impl: DefaultRefreshTokenAuthenticator
    ): RefreshTokenAuthenticator = impl

    @Provides
    @Singleton
    fun provideSessionLifecycleEffects(
        impl: AndroidSessionLifecycleEffects
    ): SessionLifecycleEffects = impl

    @Provides
    @Singleton
    fun provideSessionRepository(
        impl: SessionRepositoryImpl
    ): SessionRepository = impl
}
