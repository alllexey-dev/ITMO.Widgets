package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.client.friends.FriendsApi
import dev.alllexey.itmowidgets.client.users.UsersApi
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.coroutines.ApplicationScope
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.notification.FcmTokenSync
import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.core.recordbook.MarkTracking
import dev.alllexey.itmowidgets.core.schedule.CalendarSync
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeTracking
import dev.alllexey.itmowidgets.core.schedule.ScheduleIcsExport
import dev.alllexey.itmowidgets.core.schedule.ScheduleRefreshGateway
import dev.alllexey.itmowidgets.core.schedule.ScheduleWidgetRefreshRequester
import dev.alllexey.itmowidgets.core.schedule.SubjectLessonsGateway
import dev.alllexey.itmowidgets.core.schedule.TeacherLessonsGateway
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.session.BackendDeviceSession
import dev.alllexey.itmowidgets.core.session.BackendIdentitySync
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.SessionLifecycleEffects
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.settings.CustomSpoilerRepository
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.sport.SportScoreRepository
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AppPreferences
import dev.alllexey.itmowidgets.core.storage.CrossProcessLock
import dev.alllexey.itmowidgets.core.storage.DemoPreferences
import dev.alllexey.itmowidgets.core.storage.DeviceHintPreferences
import dev.alllexey.itmowidgets.core.storage.HomeLayoutPreferences
import dev.alllexey.itmowidgets.core.storage.MarkSourcePreferences
import dev.alllexey.itmowidgets.core.storage.QrSettingsPreferences
import dev.alllexey.itmowidgets.core.storage.ScheduleCheckPreferences
import dev.alllexey.itmowidgets.core.storage.SecureStore
import dev.alllexey.itmowidgets.core.storage.ServicesOptInPreferences
import dev.alllexey.itmowidgets.core.storage.SportSignSelectorPreferences
import dev.alllexey.itmowidgets.core.storage.UtilityStorage
import dev.alllexey.itmowidgets.core.storage.WidgetSettingsPreferences
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
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
    fun appDiagnostics(): AppDiagnostics
    fun backendGate(): BackendGate
    fun appDispatchers(): AppDispatchers
    fun currentUserProvider(): CurrentUserProvider
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
    fun servicesOptInPreferences(): ServicesOptInPreferences
    fun scheduleCheckPreferences(): ScheduleCheckPreferences
    fun widgetSettingsPreferences(): WidgetSettingsPreferences
    fun sportSignSelectorPreferences(): SportSignSelectorPreferences
    fun markSourcePreferences(): MarkSourcePreferences

    /** Core 2.0's users and friends areas; unscoped in Hilt over the one `BackendClient`, so each returns the same API. */
    fun usersApi(): UsersApi
    fun friendsApi(): FriendsApi

    fun backendIdentitySync(): BackendIdentitySync
    fun backendDeviceSession(): BackendDeviceSession
    fun fcmTokenSync(): FcmTokenSync

    /** The session's ports (L16 KM-11h1): the Keystore token file, the widget and work effects, the two flags. */
    fun sessionTokenStore(): SessionTokenStore
    fun sessionLifecycleEffects(): SessionLifecycleEffects
    fun demoPreferences(): DemoPreferences
    fun utilityStorage(): UtilityStorage

    fun customSpoilerRepository(): CustomSpoilerRepository
    fun scheduleChangeTracking(): ScheduleChangeTracking
    fun markTracking(): MarkTracking
    /** Not `calendarSync()`: `CalendarSyncEntryPoint` declares that name for the implementation type. */
    fun coreCalendarSync(): CalendarSync

    /** Unscoped in Hilt: the export keeps no state, so every reader gets a new one. */
    fun scheduleIcsExport(): ScheduleIcsExport
    /** The own schedule's lessons and its refresh, as other features read them (`@Singleton` in Hilt). */
    fun subjectLessonsGateway(): SubjectLessonsGateway
    fun scheduleRefreshGateway(): ScheduleRefreshGateway

    /** Sport scores as other features read them; unscoped in Hilt but stateless, so one instance serves Koin. */
    fun sportScoreRepository(): SportScoreRepository
    // The schedule contracts other features read (L10 LS-2a): pending sport rows and the teacher lessons gateway;
    // calendar sync and the refresh and subject lessons gateways are bridged above, the schedule preferences come
    // from Koin's `settingsDataModule`. Schedule's own data stays on Hilt.
    fun pendingSportBookingsRepository(): PendingSportBookingsRepository
    fun teacherLessonsGateway(): TeacherLessonsGateway
    /** The scope that outlives screens, for work that must finish after the caller is gone. */
    @ApplicationScope
    fun applicationScope(): CoroutineScope

    /** The schedule's widget refresh after a sport booking changes it; the refresh gateway is bridged above. */
    fun scheduleWidgetRefreshRequester(): ScheduleWidgetRefreshRequester

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
    single<AppDiagnostics> { CoreBridgeEntryPoint.from(androidContext()).appDiagnostics() }
    single<BackendGate> { CoreBridgeEntryPoint.from(androidContext()).backendGate() }
    single<AppDispatchers> { CoreBridgeEntryPoint.from(androidContext()).appDispatchers() }
    single<CurrentUserProvider> { CoreBridgeEntryPoint.from(androidContext()).currentUserProvider() }
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
    single<ServicesOptInPreferences> { CoreBridgeEntryPoint.from(androidContext()).servicesOptInPreferences() }
    single<ScheduleCheckPreferences> { CoreBridgeEntryPoint.from(androidContext()).scheduleCheckPreferences() }
    single<WidgetSettingsPreferences> { CoreBridgeEntryPoint.from(androidContext()).widgetSettingsPreferences() }
    single<SportSignSelectorPreferences> { CoreBridgeEntryPoint.from(androidContext()).sportSignSelectorPreferences() }
    single<MarkSourcePreferences> { CoreBridgeEntryPoint.from(androidContext()).markSourcePreferences() }
    single<UsersApi> { CoreBridgeEntryPoint.from(androidContext()).usersApi() }
    single<FriendsApi> { CoreBridgeEntryPoint.from(androidContext()).friendsApi() }
    single<BackendIdentitySync> { CoreBridgeEntryPoint.from(androidContext()).backendIdentitySync() }
    single<BackendDeviceSession> { CoreBridgeEntryPoint.from(androidContext()).backendDeviceSession() }
    single<FcmTokenSync> { CoreBridgeEntryPoint.from(androidContext()).fcmTokenSync() }
    single<SessionTokenStore> { CoreBridgeEntryPoint.from(androidContext()).sessionTokenStore() }
    single<SessionLifecycleEffects> { CoreBridgeEntryPoint.from(androidContext()).sessionLifecycleEffects() }
    single<DemoPreferences> { CoreBridgeEntryPoint.from(androidContext()).demoPreferences() }
    single<UtilityStorage> { CoreBridgeEntryPoint.from(androidContext()).utilityStorage() }
    single<CustomSpoilerRepository> { CoreBridgeEntryPoint.from(androidContext()).customSpoilerRepository() }
    single<ScheduleChangeTracking> { CoreBridgeEntryPoint.from(androidContext()).scheduleChangeTracking() }
    single<MarkTracking> { CoreBridgeEntryPoint.from(androidContext()).markTracking() }
    single<CalendarSync> { CoreBridgeEntryPoint.from(androidContext()).coreCalendarSync() }
    factory<ScheduleIcsExport> { CoreBridgeEntryPoint.from(androidContext()).scheduleIcsExport() }
    single<SubjectLessonsGateway> { CoreBridgeEntryPoint.from(androidContext()).subjectLessonsGateway() }
    single<ScheduleRefreshGateway> { CoreBridgeEntryPoint.from(androidContext()).scheduleRefreshGateway() }
    single<SportScoreRepository> { CoreBridgeEntryPoint.from(androidContext()).sportScoreRepository() }
    single<PendingSportBookingsRepository> {
        CoreBridgeEntryPoint.from(androidContext()).pendingSportBookingsRepository()
    }
    single<TeacherLessonsGateway> { CoreBridgeEntryPoint.from(androidContext()).teacherLessonsGateway() }
    // Unqualified in Koin: the application scope is the only CoroutineScope of the graph; a second one fails the
    // start under allowOverride(false).
    single<CoroutineScope> { CoreBridgeEntryPoint.from(androidContext()).applicationScope() }
    single<ScheduleWidgetRefreshRequester> {
        CoreBridgeEntryPoint.from(androidContext()).scheduleWidgetRefreshRequester()
    }
}
