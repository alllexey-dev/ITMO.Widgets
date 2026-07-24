package dev.alllexey.itmowidgets.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import api.myitmo.storage.Storage
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.storage.AndroidKeystoreTokenCipher
import dev.alllexey.itmowidgets.core.storage.AppPreferences
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.storage.MyItmoStorage
import dev.alllexey.itmowidgets.core.storage.TokenCipher
import dev.alllexey.itmowidgets.core.storage.TokenStorageFile
import dev.alllexey.itmowidgets.core.storage.UtilityStorage
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageModule {

    @Binds
    @Singleton
    abstract fun bindTokenCipher(
        impl: AndroidKeystoreTokenCipher
    ): TokenCipher

    @Binds
    @Singleton
    abstract fun bindMyItmoStorage(
        impl: MyItmoStorage
    ): Storage

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
        @AppPreferences
        fun provideAppPreferences(
            @ApplicationContext context: Context
        ): DataStore<Preferences> {
            return PreferenceDataStoreFactory.create(
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                produceFile = {
                    context.preferencesDataStoreFile(APP_PREFERENCES_FILE)
                }
            )
        }

        @Provides
        @Singleton
        fun provideAppSettingsStorage(
            @AppPreferences dataStore: DataStore<Preferences>
        ): AppSettingsStorage = AppSettingsStorage(dataStore)

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
