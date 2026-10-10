package dev.alllexey.itmowidgets.core.platform

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.CrossProcessLock
import dev.alllexey.itmowidgets.core.storage.SecureStore
import dev.alllexey.itmowidgets.core.storage.SingleProcessLock
import dev.alllexey.itmowidgets.core.storage.TokenCipher
import javax.inject.Singleton

/**
 * Android's platform ports; `CoreBridge` forwards them to Koin. [PlatformActions] needs the host's activity, so the
 * Navigation 3 shell (`ShellContent`) provides [AndroidPlatformActions] to Compose instead.
 */
@Module
@InstallIn(SingletonComponent::class)
object PlatformPortsModule {

    @Provides
    @Singleton
    fun provideSecureStore(directories: AppDirectories, cipher: TokenCipher): SecureStore =
        FileSecureStore(directories.noBackup, cipher)

    @Provides
    @Singleton
    fun provideCrossProcessLock(): CrossProcessLock = SingleProcessLock()

    @Provides
    @Singleton
    fun providePlatformCapabilities(): PlatformCapabilities = AndroidPlatformCapabilities
}
