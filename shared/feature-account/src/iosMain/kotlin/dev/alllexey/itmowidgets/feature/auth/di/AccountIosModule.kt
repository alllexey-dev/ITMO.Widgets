package dev.alllexey.itmowidgets.feature.auth.di

import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoCurrentUserProvider
import dev.alllexey.itmowidgets.core.notification.FcmTokenSync
import dev.alllexey.itmowidgets.core.notification.IosPushDevice
import dev.alllexey.itmowidgets.core.notification.PushDeviceRegistration
import dev.alllexey.itmowidgets.core.notification.PushForegroundRefresh
import dev.alllexey.itmowidgets.core.notification.PushRegistrationPreferences
import dev.alllexey.itmowidgets.core.session.BackendDeviceSession
import dev.alllexey.itmowidgets.core.session.BackendIdentitySync
import dev.alllexey.itmowidgets.core.session.BackendIdentityForegroundSync
import dev.alllexey.itmowidgets.core.session.DefaultBackendIdentitySync
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.IdTokenCurrentUserProvider
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.session.SessionLifecycleEffects
import dev.alllexey.itmowidgets.core.storage.DemoPreferences
import dev.alllexey.itmowidgets.core.storage.BackendUploadPreferences
import dev.alllexey.itmowidgets.core.storage.InstallIdPreferences
import dev.alllexey.itmowidgets.core.storage.PushCategoryPreferences
import dev.alllexey.itmowidgets.core.storage.ServicesOptInPreferences
import dev.alllexey.itmowidgets.feature.auth.data.SessionDataCleaners
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.binds
import org.koin.dsl.module
import org.koin.dsl.onClose

/**
 * The iOS ports of [authDataModule], what the platform's Koin modules give Android: the demo flag
 * store, the current user, every cleaner of the graph, the session effects and syncs (`IosSessionPorts.kt`) and the
 * push registration: Swift feeds [IosPushDevice] the token and runs [PushForegroundRefresh] on
 * every return to the foreground. The core types (token store, MyItmoApi client, clock, dispatchers, diagnostics, the App Group writers)
 * come from `iosCoreModule`. Load it with [authDataModule].
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
    // Not in iosCoreModule: IosCoreGraph (hosted SessionTests) would open a second DataStore over app_preferences.
    single<SessionDataCleaner>(named("install-id")) { get<InstallIdPreferences>() }
    single<SessionDataCleaner>(named("backend-uploads")) { get<BackendUploadPreferences>() }
    single { IosPushDevice.fromMainBundle() }
    singleOf(::PushRegistrationPreferences)
    single {
        PushDeviceRegistration(
            devices = get<BackendClient>().device,
            device = get<IosPushDevice>(),
            registrations = get(),
            gate = get(),
            demo = get(),
            currentUser = get(),
            version = get(),
        )
    } binds arrayOf(FcmTokenSync::class, BackendDeviceSession::class)
    single { PushForegroundRefresh(get(), get(), get()) }
    single<BackendIdentitySync> {
        DefaultBackendIdentitySync(
            gate = get(),
            tokens = get<MyItmoClient>().tokens,
            storage = get(),
            users = get(),
            diagnostics = get(),
            demo = get(),
            dispatchers = get(),
        )
    }
    single(named("identity-foreground"), createdAtStart = true) {
        BackendIdentityForegroundSync(get(), get(), get()).launchIn(get())
    } onClose { it?.cancel() }
    single(createdAtStart = true) {
        SessionSnapshotSync(
            get(),
            get<IosPushDevice>().observeAlertsAllowed(),
            get<ServicesOptInPreferences>().observeCustomServicesEnabled(),
            get<PushCategoryPreferences>().observeDisabled(),
            get<InstallIdPreferences>()::installId,
            get(),
            get()
        ).also { sync ->
            sync.launchIn(CoroutineScope(SupervisorJob() + get<AppDispatchers>().main))
        }
    }
}
