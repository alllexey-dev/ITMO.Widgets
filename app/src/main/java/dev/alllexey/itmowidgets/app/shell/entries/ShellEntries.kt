package dev.alllexey.itmowidgets.app.shell.entries

import dev.alllexey.itmowidgets.app.shell.EntryRegistry
import dev.alllexey.itmowidgets.app.shell.entryRegistry

/**
 * Every key the Compose shell can show. Each registration card (SH-1b3...SH-1b8) adds one `<Tab>Entries.kt` beside
 * this file with an `EntryRegistry.Builder.<tab>Entries()` extension and one call to it here (recipe `nav3-entry`);
 * until then every key shows the shell's placeholder.
 */
fun shellEntries(): EntryRegistry = entryRegistry { }
