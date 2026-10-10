package dev.alllexey.itmowidgets.di

import android.content.Context
import android.os.Build
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.client.ClientVersion
import dev.alllexey.itmowidgets.client.device.DeviceApi
import dev.alllexey.itmowidgets.client.users.UsersApi
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoCurrentUserProvider
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.services.DefaultBackendGate
import dev.alllexey.itmowidgets.core.session.BackendDeviceSession
import dev.alllexey.itmowidgets.core.session.BackendIdentitySync
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.DefaultBackendDeviceSession
import dev.alllexey.itmowidgets.core.session.DefaultBackendIdentitySync
import dev.alllexey.itmowidgets.core.session.IdTokenCurrentUserProvider
import dev.alllexey.itmowidgets.core.session.SessionLifecycleEffects
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.storage.UtilityStorage
import dev.alllexey.itmowidgets.app.AndroidSessionLifecycleEffects
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SessionModule {

    @Provides
    @Singleton
    fun provideBackendIdentitySync(
        @ApplicationContext context: Context,
        gate: BackendGate,
        myItmo: MyItmoClient,
        storage: TokenStorage,
        users: UsersApi,
        diagnostics: AppDiagnostics,
        demo: DemoMode,
        dispatchers: AppDispatchers
    ): BackendIdentitySync = DefaultBackendIdentitySync(
        context = context,
        gate = gate,
        tokens = myItmo.tokens,
        storage = storage,
        users = users,
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
    fun provideBackendGate(impl: DefaultBackendGate): BackendGate = impl

    @Provides
    @Singleton
    fun provideBackendDeviceSession(
        gate: BackendGate,
        utilityStorage: UtilityStorage,
        currentUser: CurrentUserProvider,
        devices: DeviceApi,
        demo: DemoMode,
        dispatchers: AppDispatchers,
        version: ClientVersion
    ): BackendDeviceSession = DefaultBackendDeviceSession(
        gate = gate,
        utilityStorage = utilityStorage,
        currentUser = currentUser,
        devices = devices,
        demo = demo,
        dispatchers = dispatchers,
        version = version,
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
}
