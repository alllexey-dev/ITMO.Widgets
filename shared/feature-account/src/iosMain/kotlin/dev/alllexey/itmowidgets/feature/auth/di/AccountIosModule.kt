package dev.alllexey.itmowidgets.feature.auth.di

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoCurrentUserProvider
import dev.alllexey.itmowidgets.core.notification.FcmTokenSync
import dev.alllexey.itmowidgets.core.session.BackendDeviceSession
import dev.alllexey.itmowidgets.core.session.BackendIdentitySync
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.IdTokenCurrentUserProvider
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.session.SessionLifecycleEffects
import dev.alllexey.itmowidgets.core.storage.DemoPreferences
import dev.alllexey.itmowidgets.feature.auth.data.SessionDataCleaners
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * The iOS ports of [authDataModule], what `:app`'s `CoreBridge` and `AccountAuthBridge` give Android: the demo flag
 * store, the current user, every cleaner of the graph and the session effects and syncs (`IosSessionPorts.kt`). The
 * core types (token store, MyItmoApi client, clock, dispatchers, diagnostics, the App Group writers) come from
 * `iosCoreModule`. Load it with [authDataModule].
 */
val accountIosModule = module {
    singleOf(::DemoPreferences)
    single<CurrentUserProvider> { DemoCurrentUserProvider(get(), IdTokenCurrentUserProvider(get(), get(), get())) }
    // The graph is Koin only, so getAll() holds each cleaner once; read on every transition, not captured here.
    single<SessionDataCleaners> {
        val koin = getKoin()
        SessionDataCleaners { koin.getAll<SessionDataCleaner>() }
    }
    single<SessionLifecycleEffects> { IosSessionLifecycleEffects }
    single<FcmTokenSync> { NoFcmTokenSync }
    single<BackendDeviceSession> { NoBackendDeviceSession }
    single<BackendIdentitySync> { NoBackendIdentitySync }
    single(createdAtStart = true) {
        SessionSnapshotSync(get(), get(), get()).also { sync ->
            sync.launchIn(CoroutineScope(SupervisorJob() + get<AppDispatchers>().main))
        }
    }
}
