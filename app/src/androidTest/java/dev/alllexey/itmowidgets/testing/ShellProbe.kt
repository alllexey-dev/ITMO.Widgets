package dev.alllexey.itmowidgets.testing

import android.view.accessibility.AccessibilityManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dev.alllexey.itmowidgets.app.MainActivity
import dev.alllexey.itmowidgets.app.shell.ShellHost
import dev.alllexey.itmowidgets.app.shell.TabPages
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.ShellSurface

/**
 * What `MainActivity` shows, in the shell's own values (SH-1a2): the [surface], the selected [tab] (null off the
 * tabs), the [overlays] bottom to top and the top sheet or dialog ([floating]). [swipeEnabled]: a horizontal swipe on
 * the tab content switches the tab now (SH-SW1).
 */
data class ShellView(
    val surface: ShellSurface,
    val tab: AppTab?,
    val overlays: List<AppRoute>,
    val floating: AppRoute?,
    val swipeEnabled: Boolean = false,
) {
    /** The key class name of [floating]. */
    val floatingName: String?
        get() = floating?.let { it::class.simpleName }
}

/** Reads [ShellView] from `MainActivity` through `ShellHost`'s read-only snapshot. */
object ShellProbe {

    /** The view of the one resumed `MainActivity`, read on the main thread. */
    fun current(): ShellView {
        var view: ShellView? = null
        var failure: Throwable? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().single()
                view = read(activity)
            } catch (error: Throwable) {
                failure = error
            }
        }
        failure?.let { throw it }
        return checkNotNull(view)
    }

    /** Call on the main thread. */
    fun read(activity: MainActivity): ShellView {
        val (surface, stack) = checkNotNull(ShellHost.of(activity)) { "MainActivity has no shell yet" }.snapshot
        val tabs = surface is ShellSurface.Tabs
        val exploring = activity.getSystemService(AccessibilityManager::class.java).isTouchExplorationEnabled
        return ShellView(
            surface = surface,
            tab = stack.tab.takeIf { tabs },
            overlays = if (tabs) stack.overlays else emptyList(),
            floating = if (tabs) stack.floating.lastOrNull() else null,
            swipeEnabled = TabPages.swipeEnabled(surface, stack, exploring),
        )
    }
}
