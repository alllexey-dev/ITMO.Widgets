package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AppPreferences
import dev.alllexey.itmowidgets.core.storage.CrossProcessLock
import dev.alllexey.itmowidgets.core.storage.DeviceHintPreferences
import dev.alllexey.itmowidgets.core.storage.HomeLayoutPreferences
import dev.alllexey.itmowidgets.core.storage.QrSettingsPreferences
import dev.alllexey.itmowidgets.core.storage.SecureStore
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import kotlin.time.Clock
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Hilt to Koin for the core contracts: Hilt constructs each of them, Koin only forwards (one graph per binding).
 * A feature lane adds the core contract it needs here once, with an accessor and a `single`; a feature's own types go
 * to its `<Feature>Bridge.kt`.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface CoreBridgeEntryPoint {
    fun academicTimeProvider(): AcademicTimeProvider
    fun demoMode(): DemoMode
    fun appDiagnostics(): AppDiagnostics
    fun backendGate(): BackendGate
    fun appDispatchers(): AppDispatchers
    fun sessionRepository(): SessionRepository
    fun secureStore(): SecureStore
    fun crossProcessLock(): CrossProcessLock
    fun platformCapabilities(): PlatformCapabilities

    /** The wall clock; academic logic reads [AcademicTimeProvider] instead. */
    fun clock(): Clock

    /** The one MyItmoApi 2.x client of the process; its token manager is the only refresher. */
    fun myItmoClient(): MyItmoClient

    /** The one `app_preferences` DataStore of the process; a second instance over the file would throw. */
    @AppPreferences
    fun appPreferences(): DataStore<Preferences>

    fun appDirectories(): AppDirectories
    fun qrSettingsPreferences(): QrSettingsPreferences
    fun deviceHintPreferences(): DeviceHintPreferences
    fun homeLayoutPreferences(): HomeLayoutPreferences

    /** The custom-services opt-in; reading it is local and never calls Backend. */
    fun customServicesRepository(): CustomServicesRepository

    companion object {
        fun from(context: Context): CoreBridgeEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, CoreBridgeEntryPoint::class.java)
    }
}

/**
 * Lazy singles: Koin starts before Hilt builds its component, so each one reads Hilt on first use. Hilt scopes these
 * bindings as singletons, so Koin caches the same instance Hilt hands out.
 */
val coreBridgeModule = module {
    single<AcademicTimeProvider> { CoreBridgeEntryPoint.from(androidContext()).academicTimeProvider() }
    single<DemoMode> { CoreBridgeEntryPoint.from(androidContext()).demoMode() }
    single<AppDiagnostics> { CoreBridgeEntryPoint.from(androidContext()).appDiagnostics() }
    single<BackendGate> { CoreBridgeEntryPoint.from(androidContext()).backendGate() }
    single<AppDispatchers> { CoreBridgeEntryPoint.from(androidContext()).appDispatchers() }
    single<SessionRepository> { CoreBridgeEntryPoint.from(androidContext()).sessionRepository() }
    single<SecureStore> { CoreBridgeEntryPoint.from(androidContext()).secureStore() }
    single<CrossProcessLock> { CoreBridgeEntryPoint.from(androidContext()).crossProcessLock() }
    single<PlatformCapabilities> { CoreBridgeEntryPoint.from(androidContext()).platformCapabilities() }
    single<Clock> { CoreBridgeEntryPoint.from(androidContext()).clock() }
    single<MyItmoClient> { CoreBridgeEntryPoint.from(androidContext()).myItmoClient() }
    // Unqualified in Koin: `app_preferences` is the only preferences DataStore of the app.
    single<DataStore<Preferences>> { CoreBridgeEntryPoint.from(androidContext()).appPreferences() }
    single<AppDirectories> { CoreBridgeEntryPoint.from(androidContext()).appDirectories() }
    single<QrSettingsPreferences> { CoreBridgeEntryPoint.from(androidContext()).qrSettingsPreferences() }
    single<DeviceHintPreferences> { CoreBridgeEntryPoint.from(androidContext()).deviceHintPreferences() }
    single<HomeLayoutPreferences> { CoreBridgeEntryPoint.from(androidContext()).homeLayoutPreferences() }
    single<CustomServicesRepository> { CoreBridgeEntryPoint.from(androidContext()).customServicesRepository() }
}
