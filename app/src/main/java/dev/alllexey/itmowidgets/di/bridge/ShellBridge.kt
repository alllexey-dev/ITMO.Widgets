package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.feature.update.ui.InstallStateWatcher
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Hilt to Koin for what the Compose shell's host (`app/shell/ShellHost`) reads besides the session and the first-run
 * flag (`CoreBridge`) and the update check (`updateModule` since LA-2b): the flavor's flexible-update watcher, which
 * Hilt keeps constructing (one graph per binding).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ShellBridgeEntryPoint {
    fun installStateWatcher(): InstallStateWatcher

    companion object {
        fun from(context: Context): ShellBridgeEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, ShellBridgeEntryPoint::class.java)
    }
}

/**
 * A lazy factory: Koin starts before Hilt builds its component, and the watcher is unscoped in Hilt, so Koin asks Hilt
 * again for every reader, as `MainActivity`'s field injection does.
 */
val shellBridgeModule = module {
    factory<InstallStateWatcher> { ShellBridgeEntryPoint.from(androidContext()).installStateWatcher() }
}
