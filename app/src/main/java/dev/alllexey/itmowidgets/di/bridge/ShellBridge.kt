package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.navigation.ShareLinkFactory
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreviewFactory
import dev.alllexey.itmowidgets.feature.update.ui.InstallStateWatcher
import dev.alllexey.itmowidgets.feature.update.ui.UpdateAction
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Hilt to Koin for what the Compose shell's host (`app/shell/ShellHost`) reads besides the session and the first-run
 * flag (`CoreBridge`) and the update check (`updateModule` since LA-2b): the flavor's flexible-update watcher, which
 * Hilt keeps constructing (one graph per binding). Also what the shell's account entries (SH-1b8) read beside their
 * Fragment hosts: the first-run flow's widget previews, the share links of the Me tab and the flavor's update action.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ShellBridgeEntryPoint {
    fun installStateWatcher(): InstallStateWatcher
    fun widgetPreviewFactory(): WidgetPreviewFactory
    fun shareLinkFactory(): ShareLinkFactory
    fun updateAction(): UpdateAction

    companion object {
        fun from(context: Context): ShellBridgeEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, ShellBridgeEntryPoint::class.java)
    }
}

/**
 * Lazy factories: Koin starts before Hilt builds its component, and these are unscoped in Hilt, so Koin asks Hilt
 * again for every reader, as the Fragment hosts' field injection does.
 */
val shellBridgeModule = module {
    factory<InstallStateWatcher> { ShellBridgeEntryPoint.from(androidContext()).installStateWatcher() }
    factory<WidgetPreviewFactory> { ShellBridgeEntryPoint.from(androidContext()).widgetPreviewFactory() }
    factory<ShareLinkFactory> { ShellBridgeEntryPoint.from(androidContext()).shareLinkFactory() }
    factory<UpdateAction> { ShellBridgeEntryPoint.from(androidContext()).updateAction() }
}
