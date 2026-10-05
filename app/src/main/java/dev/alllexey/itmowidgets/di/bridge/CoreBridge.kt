package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.storage.CrossProcessLock
import dev.alllexey.itmowidgets.core.storage.SecureStore
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
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
}
