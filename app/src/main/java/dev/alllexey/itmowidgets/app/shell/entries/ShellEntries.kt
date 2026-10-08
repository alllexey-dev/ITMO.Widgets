package dev.alllexey.itmowidgets.app.shell.entries

import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.app.shell.EntryRegistry
import dev.alllexey.itmowidgets.app.shell.entryRegistry
import dev.alllexey.itmowidgets.app.shell.shellHostEntries
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab

/**
 * Every key the Compose shell can show. Each registration card (SH-1b3...SH-1b8) adds one `<Tab>Entries.kt` beside
 * this file with an `EntryRegistry.Builder.<tab>Entries()` extension and one call to it here (recipe `nav3-entry`),
 * and its tab's branch of the one [AppRoutes.TabRoot] registration; a key without a registration shows the shell's
 * placeholder.
 * [debugTools] registers the debug tools; only a debug build does, and a release build's registry has no such key.
 */
fun shellEntries(debugTools: Boolean = BuildConfig.DEBUG): EntryRegistry = entryRegistry {
    shellHostEntries()
    entry<AppRoutes.TabRoot> { key, navigator ->
        when (key.tab) {
            AppTab.HOME -> HomeTabRoot(navigator)
            AppTab.SCHEDULE -> ScheduleTabRoot(navigator)
            AppTab.SPORT -> SportTabRoot(navigator)
            AppTab.ME -> MeTabRoot(navigator)
            AppTab.RECORDBOOK -> RecordbookTabRoot(navigator)
        }
    }
    homeEntries()
    scheduleEntries()
    socialEntries()
    accountEntries(debugTools)
    sportEntries()
    settingsEntries()
    resourcesEntries()
    reviewsEntries()
    recordbookEntries()
}
