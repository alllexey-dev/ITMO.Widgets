package dev.alllexey.itmowidgets.di

import android.os.Build
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.app.AndroidSessionLifecycleEffects
import dev.alllexey.itmowidgets.core.demo.DemoCurrentUserProvider
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.services.DefaultBackendGate
import dev.alllexey.itmowidgets.core.session.BackendDeviceSession
import dev.alllexey.itmowidgets.core.session.BackendIdentitySync
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.DefaultBackendDeviceSession
import dev.alllexey.itmowidgets.core.session.DefaultBackendIdentitySync
import dev.alllexey.itmowidgets.core.session.IdentitySyncWork
import dev.alllexey.itmowidgets.core.session.IdTokenCurrentUserProvider
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.session.SessionLifecycleEffects
import dev.alllexey.itmowidgets.core.work.BackgroundCheck
import dev.alllexey.itmowidgets.feature.auth.data.SessionDataCleaners
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * The session's Android side and the Backend syncs. The session and the demo switch are `authDataModule`'s. The
 * current user is keyed by its implementation as well, so a debug fixture that overrides [CurrentUserProvider] can
 * point the key back at the same instance.
 */
val sessionModule = module {
    single<BackendIdentitySync> {
        DefaultBackendIdentitySync(
            gate = get(),
            tokens = get<MyItmoClient>().tokens,
            storage = get(),
            users = get(),
            diagnostics = get(),
            demo = get(),
            dispatchers = get(),
            retry = { IdentitySyncWork.schedule(androidContext()) },
        )
    }
    single {
        DemoCurrentUserProvider(
            demo = get(),
            signedIn = IdTokenCurrentUserProvider(tokenStore = get(), dispatchers = get(), log = get())
        )
    } bind CurrentUserProvider::class
    singleOf(::DefaultBackendGate) { bind<BackendGate>() }
    single<BackendDeviceSession> {
        DefaultBackendDeviceSession(
            gate = get(),
            utilityStorage = get(),
            currentUser = get(),
            devices = get(),
            demo = get(),
            dispatchers = get(),
            version = get(),
            deviceName = listOf(Build.MANUFACTURER, Build.MODEL)
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
                .joinToString(" ")
                .ifBlank { "Android" }
        )
    }
    single<SessionLifecycleEffects> {
        AndroidSessionLifecycleEffects(
            context = androidContext(),
            widgetSettings = get(),
            palettes = get(),
            scheduleWidgetStore = get(),
            qrWidgetStateStore = get(),
            qrWidgetImages = get(),
            notifier = get(),
            backgroundChecks = getAll<BackgroundCheck>().toSet()
        )
    }
    // Every cleaner in the graph, read again at each transition: each contribution carries its own qualifier.
    single<SessionDataCleaners> {
        val koin = getKoin()
        SessionDataCleaners { koin.getAll<SessionDataCleaner>() }
    }
}
