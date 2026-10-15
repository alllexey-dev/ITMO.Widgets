package dev.alllexey.itmowidgets.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.platform.AndroidPlatformCapabilities
import dev.alllexey.itmowidgets.core.platform.AppForeground
import dev.alllexey.itmowidgets.core.platform.FileSecureStore
import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.core.platform.ProcessAppForeground
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.storage.AndroidAppDirectories
import dev.alllexey.itmowidgets.core.storage.AndroidKeystoreTokenCipher
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AppearancePreferences
import dev.alllexey.itmowidgets.core.storage.CrossProcessLock
import dev.alllexey.itmowidgets.core.storage.DemoPreferences
import dev.alllexey.itmowidgets.core.storage.DeviceHintPreferences
import dev.alllexey.itmowidgets.core.storage.BellPreferences
import dev.alllexey.itmowidgets.core.storage.MarkSourcePreferences
import dev.alllexey.itmowidgets.core.storage.MyItmoStorage
import dev.alllexey.itmowidgets.core.settings.HomePassVisibility
import dev.alllexey.itmowidgets.core.storage.QrSettingsPreferences
import dev.alllexey.itmowidgets.core.storage.RetiredPreferenceKeys
import dev.alllexey.itmowidgets.core.storage.ScheduleCheckPreferences
import dev.alllexey.itmowidgets.core.storage.SecureStore
import dev.alllexey.itmowidgets.core.storage.ServicesOptInPreferences
import dev.alllexey.itmowidgets.core.storage.SingleProcessLock
import dev.alllexey.itmowidgets.core.storage.SportSignSelectorPreferences
import dev.alllexey.itmowidgets.core.storage.TokenCipher
import dev.alllexey.itmowidgets.core.storage.UtilityStorage
import dev.alllexey.itmowidgets.core.storage.WidgetSettingsPreferences
import dev.alllexey.itmowidgets.core.storage.preferencesDataStoreFile
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.binds
import org.koin.dsl.module

/** Storage and the platform ports over it (`SecureStore`, the lock, the capabilities), and the app's foreground. */
val storageModule = module {
    singleOf(::AndroidKeystoreTokenCipher) { bind<TokenCipher>() }
    // One writer of `myitmo_tokens.enc`: MyItmoApi's token storage and the session's store are the same instance.
    single {
        MyItmoStorage(File(androidContext().noBackupFilesDir, TOKEN_FILE_NAME), get(), get())
    } binds arrayOf(TokenStorage::class, SessionTokenStore::class)
    single<AppDirectories> { AndroidAppDirectories(androidContext()) }
    // The one instance of `app_preferences` in the process; a second one over the same file would throw.
    single<DataStore<Preferences>> {
        val directories = get<AppDirectories>()
        PreferenceDataStoreFactory.createWithPath(
            migrations = listOf(RetiredPreferenceKeys),
            scope = CoroutineScope(SupervisorJob() + get<AppDispatchers>().io),
            produceFile = { directories.preferencesDataStoreFile(APP_PREFERENCES_FILE) }
        )
    }
    singleOf(::ServicesOptInPreferences)
    singleOf(::ScheduleCheckPreferences)
    singleOf(::WidgetSettingsPreferences)
    singleOf(::QrSettingsPreferences) { bind<HomePassVisibility>() }
    singleOf(::SportSignSelectorPreferences)
    singleOf(::MarkSourcePreferences)
    singleOf(::BellPreferences)
    singleOf(::DeviceHintPreferences)
    singleOf(::AppearancePreferences)
    singleOf(::DemoPreferences)
    single { UtilityStorage(dataStore = get(), appVersionName = androidContext().getString(R.string.app_version)) }

    single<SecureStore> { FileSecureStore(get<AppDirectories>().noBackup, get()) }
    single<CrossProcessLock> { SingleProcessLock() }
    single<PlatformCapabilities> { AndroidPlatformCapabilities }
    single<AppForeground> { ProcessAppForeground() }
}

private const val TOKEN_FILE_NAME = "myitmo_tokens.enc"
private const val APP_PREFERENCES_FILE = "app_preferences"
