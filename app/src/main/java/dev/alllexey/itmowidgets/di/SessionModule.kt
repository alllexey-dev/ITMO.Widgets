package dev.alllexey.itmowidgets.di

import android.content.Context
import android.os.Build
import api.myitmo.MyItmo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoCurrentUserProvider
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.notification.FcmTokenSync
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.services.DefaultBackendGate
import dev.alllexey.itmowidgets.core.session.BackendDeviceSession
import dev.alllexey.itmowidgets.core.session.BackendIdentitySync
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.DefaultBackendDeviceSession
import dev.alllexey.itmowidgets.core.session.DefaultBackendIdentitySync
import dev.alllexey.itmowidgets.core.session.IdTokenCurrentUserProvider
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.session.SessionLifecycleEffects
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.storage.DemoPreferences
import dev.alllexey.itmowidgets.core.storage.UtilityStorage
import dev.alllexey.itmowidgets.app.AndroidSessionLifecycleEffects
import dev.alllexey.itmowidgets.feature.auth.data.DataStoreDemoMode
import dev.alllexey.itmowidgets.feature.auth.data.SessionRepositoryImpl
import dev.alllexey.itmowidgets.feature.auth.data.SessionTransitions
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SessionModule {

    @Provides
    @Singleton
    fun provideBackendIdentitySync(
        @ApplicationContext context: Context,
        gate: BackendGate,
        myItmo: MyItmo,
        widgetsApi: ItmoWidgetsApi,
        diagnostics: AppDiagnostics,
        demo: DemoMode,
        dispatchers: AppDispatchers
    ): BackendIdentitySync = DefaultBackendIdentitySync(
        context = context,
        gate = gate,
        myItmo = myItmo,
        widgetsApi = widgetsApi,
        diagnostics = diagnostics,
        demo = demo,
        dispatchers = dispatchers
    )

    @Provides
    @Singleton
    fun provideCurrentUserProvider(
        tokenStore: SessionTokenStore,
        demo: DemoMode,
        dispatchers: AppDispatchers,
        log: AppLog
    ): CurrentUserProvider = DemoCurrentUserProvider(
        demo = demo,
        signedIn = IdTokenCurrentUserProvider(
            tokenStore = tokenStore,
            dispatchers = dispatchers,
            log = log
        )
    )

    @Provides
    @Singleton
    fun provideDemoMode(impl: DataStoreDemoMode): DemoMode = impl

    @Provides
    @Singleton
    fun provideBackendGate(impl: DefaultBackendGate): BackendGate = impl

    @Provides
    @Singleton
    fun provideBackendDeviceSession(
        gate: BackendGate,
        utilityStorage: UtilityStorage,
        currentUser: CurrentUserProvider,
        widgetsApi: ItmoWidgetsApi,
        demo: DemoMode,
        dispatchers: AppDispatchers
    ): BackendDeviceSession = DefaultBackendDeviceSession(
        gate = gate,
        utilityStorage = utilityStorage,
        currentUser = currentUser,
        widgetsApi = widgetsApi,
        demo = demo,
        dispatchers = dispatchers,
        deviceName = listOf(Build.MANUFACTURER, Build.MODEL)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .joinToString(" ")
            .ifBlank { "Android" }
    )

    @Provides
    @Singleton
    fun provideSessionLifecycleEffects(
        impl: AndroidSessionLifecycleEffects
    ): SessionLifecycleEffects = impl

    @Provides
    @Singleton
    fun provideSessionTransitions(
        myItmo: MyItmoClient,
        currentUserProvider: CurrentUserProvider,
        dataCleaners: Provider<Set<@JvmSuppressWildcards SessionDataCleaner>>,
        lifecycleEffects: SessionLifecycleEffects,
        backendIdentitySync: BackendIdentitySync,
        backendDeviceSession: BackendDeviceSession,
        fcmTokenSync: FcmTokenSync,
        diagnostics: AppDiagnostics,
        demoPreferences: DemoPreferences,
        dispatchers: AppDispatchers
    ): SessionTransitions = SessionTransitions(
        myItmo = myItmo,
        currentUserProvider = currentUserProvider,
        dataCleaners = { dataCleaners.get() },
        lifecycleEffects = lifecycleEffects,
        backendIdentitySync = backendIdentitySync,
        backendDeviceSession = backendDeviceSession,
        fcmTokenSync = fcmTokenSync,
        diagnostics = diagnostics,
        demoPreferences = demoPreferences,
        dispatchers = dispatchers
    )

    @Provides
    @Singleton
    fun provideSessionRepository(
        impl: SessionRepositoryImpl
    ): SessionRepository = impl
}
