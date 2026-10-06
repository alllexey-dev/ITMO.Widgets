package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.feature.update.domain.PendingAppUpdate
import dev.alllexey.itmowidgets.feature.update.ui.InstallStateWatcher
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Hilt to Koin for what the Compose shell's host (`app/shell/ShellHost`) reads besides the session and the first-run
 * flag (`CoreBridge`): the update check and the flavor's flexible-update watcher. Hilt keeps constructing both (one
 * graph per binding); L16's LA-2b replaces the [PendingAppUpdate] line with its Koin definition.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ShellBridgeEntryPoint {
    fun pendingAppUpdate(): PendingAppUpdate
    fun installStateWatcher(): InstallStateWatcher

    companion object {
        fun from(context: Context): ShellBridgeEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, ShellBridgeEntryPoint::class.java)
    }
}

/**
 * Lazy factories: Koin starts before Hilt builds its component, and both types are unscoped in Hilt, so Koin asks Hilt
 * again for every reader, as `MainActivity`'s field injection does.
 */
val shellBridgeModule = module {
    factory<PendingAppUpdate> { ShellBridgeEntryPoint.from(androidContext()).pendingAppUpdate() }
    factory<InstallStateWatcher> { ShellBridgeEntryPoint.from(androidContext()).installStateWatcher() }
}
