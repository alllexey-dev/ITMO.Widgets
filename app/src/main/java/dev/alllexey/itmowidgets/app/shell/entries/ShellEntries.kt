package dev.alllexey.itmowidgets.app.shell.entries

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.app.shell.EntryRegistry
import dev.alllexey.itmowidgets.app.shell.entryRegistry
import dev.alllexey.itmowidgets.app.shell.shellHostEntries
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab

/**
 * Every key the Compose shell can show. Each registration card (SH-1b3...SH-1b8) adds one `<Tab>Entries.kt` beside
 * this file with an `EntryRegistry.Builder.<tab>Entries()` extension and one call to it here (recipe `nav3-entry`),
 * and its tab's branch of the one [AppRoutes.TabRoot] registration; until then a key shows the shell's placeholder.
 * [debugTools] registers the debug tools; only a debug build does, and a release build's registry has no such key.
 */
fun shellEntries(debugTools: Boolean = BuildConfig.DEBUG): EntryRegistry = entryRegistry {
    shellHostEntries()
    entry<AppRoutes.TabRoot> { key, navigator ->
        when (key.tab) {
            AppTab.HOME -> HomeTabRoot(navigator)
            AppTab.ME -> MeTabRoot(navigator)
            AppTab.RECORDBOOK, AppTab.SCHEDULE, AppTab.SPORT -> UnregisteredTabRoot(key)
        }
    }
    homeEntries()
    socialEntries()
    accountEntries(debugTools)
}

/** The placeholder of a tab whose root is not registered yet, tagged as the registry's own placeholder. */
@Composable
private fun UnregisteredTabRoot(key: AppRoutes.TabRoot) {
    Surface(Modifier.fillMaxSize().testTag(EntryRegistry.placeholderTag(key))) {}
}
