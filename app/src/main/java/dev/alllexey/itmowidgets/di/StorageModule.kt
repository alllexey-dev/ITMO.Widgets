package dev.alllexey.itmowidgets.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.storage.AndroidAppDirectories
import dev.alllexey.itmowidgets.core.storage.AndroidKeystoreTokenCipher
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AppPreferences
import dev.alllexey.itmowidgets.core.storage.DemoPreferences
import dev.alllexey.itmowidgets.core.storage.AppearancePreferences
import dev.alllexey.itmowidgets.core.storage.DeviceHintPreferences
import dev.alllexey.itmowidgets.core.storage.HomeLayoutPreferences
import dev.alllexey.itmowidgets.core.storage.MarkSourcePreferences
import dev.alllexey.itmowidgets.core.storage.MyItmoStorage
import dev.alllexey.itmowidgets.core.storage.QrSettingsPreferences
import dev.alllexey.itmowidgets.core.storage.ScheduleCheckPreferences
import dev.alllexey.itmowidgets.core.storage.ServicesOptInPreferences
import dev.alllexey.itmowidgets.core.storage.SportSignSelectorPreferences
import dev.alllexey.itmowidgets.core.storage.TokenCipher
import dev.alllexey.itmowidgets.core.storage.TokenStorageFile
import dev.alllexey.itmowidgets.core.storage.UtilityStorage
import dev.alllexey.itmowidgets.core.storage.WidgetSettingsPreferences
import dev.alllexey.itmowidgets.core.storage.preferencesDataStoreFile
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageModule {

    @Binds
    @Singleton
    abstract fun bindTokenCipher(
        impl: AndroidKeystoreTokenCipher
    ): TokenCipher

    /** The same instance as [SessionTokenStore]: one writer of `myitmo_tokens.enc`. */
    @Binds
    @Singleton
    abstract fun bindTokenStorage(
        impl: MyItmoStorage
    ): TokenStorage

    @Binds
    @Singleton
    abstract fun bindSessionTokenStore(
        impl: MyItmoStorage
    ): SessionTokenStore

    companion object {

        @Provides
        @Singleton
        @TokenStorageFile
        fun provideTokenStorageFile(
            @ApplicationContext context: Context
        ): File = File(context.noBackupFilesDir, TOKEN_FILE_NAME)

        @Provides
        @Singleton
        fun provideAppDirectories(
            @ApplicationContext context: Context
        ): AppDirectories = AndroidAppDirectories(context)

        /** The one instance of `app_preferences` in the process; a second one over the same file would throw. */
        @Provides
        @Singleton
        @AppPreferences
        fun provideAppPreferences(
            directories: AppDirectories,
            dispatchers: AppDispatchers
        ): DataStore<Preferences> {
            return PreferenceDataStoreFactory.createWithPath(
                scope = CoroutineScope(SupervisorJob() + dispatchers.io),
                produceFile = {
                    directories.preferencesDataStoreFile(APP_PREFERENCES_FILE)
                }
            )
        }

        @Provides
        @Singleton
        fun provideServicesOptInPreferences(
            @AppPreferences dataStore: DataStore<Preferences>
        ): ServicesOptInPreferences = ServicesOptInPreferences(dataStore)

        @Provides
        @Singleton
        fun provideScheduleCheckPreferences(
            @AppPreferences dataStore: DataStore<Preferences>
        ): ScheduleCheckPreferences = ScheduleCheckPreferences(dataStore)

        @Provides
        @Singleton
        fun provideWidgetSettingsPreferences(
            @AppPreferences dataStore: DataStore<Preferences>
        ): WidgetSettingsPreferences = WidgetSettingsPreferences(dataStore)

        @Provides
        @Singleton
        fun provideQrSettingsPreferences(
            @AppPreferences dataStore: DataStore<Preferences>
        ): QrSettingsPreferences = QrSettingsPreferences(dataStore)

        @Provides
        @Singleton
        fun provideSportSignSelectorPreferences(
            @AppPreferences dataStore: DataStore<Preferences>
        ): SportSignSelectorPreferences = SportSignSelectorPreferences(dataStore)

        @Provides
        @Singleton
        fun provideMarkSourcePreferences(
            @AppPreferences dataStore: DataStore<Preferences>
        ): MarkSourcePreferences = MarkSourcePreferences(dataStore)

        @Provides
        @Singleton
        fun provideHomeLayoutPreferences(
            @AppPreferences dataStore: DataStore<Preferences>
        ): HomeLayoutPreferences = HomeLayoutPreferences(dataStore)

        @Provides
        @Singleton
        fun provideDeviceHintPreferences(
            @AppPreferences dataStore: DataStore<Preferences>
        ): DeviceHintPreferences = DeviceHintPreferences(dataStore)

        @Provides
        @Singleton
        fun provideAppearancePreferences(
            @AppPreferences dataStore: DataStore<Preferences>
        ): AppearancePreferences = AppearancePreferences(dataStore)

        @Provides
        @Singleton
        fun provideDemoPreferences(
            @AppPreferences dataStore: DataStore<Preferences>
        ): DemoPreferences = DemoPreferences(dataStore)

        @Provides
        @Singleton
        fun provideUtilityStorage(
            @AppPreferences dataStore: DataStore<Preferences>,
            @ApplicationContext context: Context
        ): UtilityStorage = UtilityStorage(
            dataStore = dataStore,
            appVersionName = context.getString(R.string.app_version)
        )

        private const val TOKEN_FILE_NAME = "myitmo_tokens.enc"
        private const val APP_PREFERENCES_FILE = "app_preferences"
    }
}
