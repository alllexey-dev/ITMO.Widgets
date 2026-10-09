package dev.alllexey.itmowidgets.core.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.client.ClientVersion
import dev.alllexey.itmowidgets.client.users.UsersApi
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.coroutines.systemAppDispatchers
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.diagnostics.IosAppDiagnostics
import dev.alllexey.itmowidgets.core.diagnostics.OsLogAppLog
import dev.alllexey.itmowidgets.core.navigation.ShareLinkFactory
import dev.alllexey.itmowidgets.core.network.BackendClientFactory
import dev.alllexey.itmowidgets.core.network.BackendOrigin
import dev.alllexey.itmowidgets.core.network.MyItmoClientFactory
import dev.alllexey.itmowidgets.core.network.darwinHttpEngine
import dev.alllexey.itmowidgets.core.platform.AppBundleVersion
import dev.alllexey.itmowidgets.core.platform.BundleIdentifiers
import dev.alllexey.itmowidgets.core.platform.IosClientVersion
import dev.alllexey.itmowidgets.core.platform.IosCoreHost
import dev.alllexey.itmowidgets.core.platform.IosPlatformActions
import dev.alllexey.itmowidgets.core.platform.IosPlatformCapabilities
import dev.alllexey.itmowidgets.core.platform.PlatformActions
import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.services.IosBackendGate
import dev.alllexey.itmowidgets.core.session.AppGroupSessionDataCleaner
import dev.alllexey.itmowidgets.core.session.KeychainSessionDataCleaner
import dev.alllexey.itmowidgets.core.session.KeychainTokenStorage
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.session.SessionSnapshotWriter
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.session.WebsiteDataSessionDataCleaner
import dev.alllexey.itmowidgets.core.session.myItmoRefreshGuard
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AppGroupDirectory
import dev.alllexey.itmowidgets.core.storage.AppGroupSnapshotWriter
import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import dev.alllexey.itmowidgets.core.storage.CrossProcessLock
import dev.alllexey.itmowidgets.core.storage.DeviceHintPreferences
import dev.alllexey.itmowidgets.core.storage.FileCrossProcessLock
import dev.alllexey.itmowidgets.core.storage.HomeLayoutPreferences
import dev.alllexey.itmowidgets.core.storage.IosAppDirectories
import dev.alllexey.itmowidgets.core.storage.KeychainSecureStore
import dev.alllexey.itmowidgets.core.storage.MarkSourcePreferences
import dev.alllexey.itmowidgets.core.storage.QrSettingsPreferences
import dev.alllexey.itmowidgets.core.storage.ScheduleCheckPreferences
import dev.alllexey.itmowidgets.core.storage.SecureStore
import dev.alllexey.itmowidgets.core.storage.ServicesOptInPreferences
import dev.alllexey.itmowidgets.core.storage.SportSignSelectorPreferences
import dev.alllexey.itmowidgets.core.storage.UtilityStorage
import dev.alllexey.itmowidgets.core.storage.WidgetReloader
import dev.alllexey.itmowidgets.core.storage.WidgetSettingsPreferences
import dev.alllexey.itmowidgets.core.storage.preferencesDataStoreFile
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.time.DefaultAcademicTimeProvider
import dev.alllexey.itmowidgets.core.time.NoAcademicTimeOverride
import io.ktor.client.engine.HttpClientEngine
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.datetime.TimeZone
import org.koin.core.module.Module
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.binds
import org.koin.dsl.module

/**
 * The core bindings of the iOS app process, what `:app`'s Hilt modules and `CoreBridge` give Android: storage, the
 * session, both network clients over one Darwin engine and Core 2.0's users API, time, dispatchers, the application
 * scope, logs with the crash journal, the Backend gate, platform actions and capabilities, the share links and the
 * session cleaners. Koin is the only graph
 * on iOS, so each type is defined here once.
 *
 * Not here (one graph per binding): `DemoMode` and `SessionRepository` come from the account module (KM-11h1, loaded
 * by IO-21); ports still in `:app` (`AppNotifier`, `FcmTokenSync`) are bound by the card that needs them.
 *
 * [backendOrigin] is the build's Backend, from the app's Info.plist ([BackendOrigin]), and the site the share links
 * name, as Android's `WIDGETS_BASE_URL` is both; [appVersion] its marketing version ([AppBundleVersion]);
 * [clientVersion] the build every Backend request names in `X-App-Version` ([IosClientVersion]).
 */
fun iosCoreModule(
    host: IosCoreHost,
    backendOrigin: String = BackendOrigin.fromMainBundle(),
    appVersion: String = AppBundleVersion.fromMainBundle(),
    clientVersion: ClientVersion = IosClientVersion.fromMainBundle()
): Module = module {
    single<WidgetReloader> { host }
    single { ShareLinkFactory(backendOrigin) }
    single<PlatformActions> { IosPlatformActions(host) }
    single<PlatformCapabilities> { IosPlatformCapabilities }

    single<AppLog> { OsLogAppLog() }
    // The crash journal file: the app's own, never backed up (IosCrashHook writes it as the process ends).
    single {
        val crashFile = get<AppDirectories>().noBackup / IosAppDiagnostics.CRASH_FILE
        IosAppDiagnostics(get(), get(), AtomicTextFile(crashFile))
    } binds arrayOf(AppDiagnostics::class)
    single<Clock> { Clock.System }
    single<AppDispatchers> { systemAppDispatchers() }
    // The application scope, as Android's `CoroutinesModule`: work a screen starts that must outlive it.
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + get<AppDispatchers>().default) }
    single<AcademicTimeProvider> {
        DefaultAcademicTimeProvider(get(), TimeZone.of(ACADEMIC_TIME_ZONE), NoAcademicTimeOverride)
    }

    single { BundleIdentifiers.fromMainBundle() }
    single<AppDirectories> { IosAppDirectories.create(get()) }
    single { AppGroupDirectory.resolve(get(), get(), get()) }
    single { AppGroupSnapshotWriter(get(), get()) }
    singleOf(::SessionSnapshotWriter)
    single<CrossProcessLock> { FileCrossProcessLock(get<AppGroupDirectory>().locks) }
    single { KeychainSecureStore(get<BundleIdentifiers>().keychainGroup, get()) } binds arrayOf(SecureStore::class)
    // DataStore is single-process: only the app opens `app_preferences`; extensions read App Group files.
    single<DataStore<Preferences>> {
        val directories = get<AppDirectories>()
        PreferenceDataStoreFactory.createWithPath(
            scope = CoroutineScope(SupervisorJob() + get<AppDispatchers>().io),
            produceFile = { directories.preferencesDataStoreFile(APP_PREFERENCES) }
        )
    }
    singleOf(::ServicesOptInPreferences)
    singleOf(::QrSettingsPreferences)
    singleOf(::DeviceHintPreferences)
    singleOf(::HomeLayoutPreferences)
    singleOf(::ScheduleCheckPreferences)
    singleOf(::WidgetSettingsPreferences)
    singleOf(::SportSignSelectorPreferences)
    singleOf(::MarkSourcePreferences)
    single { UtilityStorage(get(), appVersion) }
    singleOf(::IosBackendGate) { bind<BackendGate>() }

    // The session: one Keychain item that both MyItmoApi and the session read; the client's TokenManager is its
    // only refresher, under the cross-process lock the notification service takes too.
    single { KeychainTokenStorage(get(), get(), get()) } binds arrayOf(TokenStorage::class, SessionTokenStore::class)
    single<HttpClientEngine> { darwinHttpEngine() }
    single<MyItmoClient> {
        MyItmoClientFactory.create(
            storage = get(),
            engine = get(),
            clock = get(),
            refreshGuard = get<CrossProcessLock>().myItmoRefreshGuard()
        )
    }
    single<BackendClient> {
        BackendClientFactory.create(backendOrigin, get<MyItmoClient>().tokens, get(), clientVersion)
    }
    single<UsersApi> { get<BackendClient>().users }

    // An open set: each contribution is qualified (recipe koin-module).
    single<SessionDataCleaner>(named("keychain")) { KeychainSessionDataCleaner(get(), get()) }
    single<SessionDataCleaner>(named("app-group")) { AppGroupSessionDataCleaner(get(), get()) }
    single<SessionDataCleaner>(named("website-data")) { WebsiteDataSessionDataCleaner(host, get()) }
}

/** As Android's `TimeModule`. */
private const val ACADEMIC_TIME_ZONE = "Europe/Moscow"

/** As Android's `StorageModule`: the one preferences DataStore of the app. */
private const val APP_PREFERENCES = "app_preferences"
