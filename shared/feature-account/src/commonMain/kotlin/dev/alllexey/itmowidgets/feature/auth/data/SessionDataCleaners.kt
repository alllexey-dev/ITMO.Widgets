package dev.alllexey.itmowidgets.feature.auth.data

import dev.alllexey.itmowidgets.core.session.SessionDataCleaner

/**
 * Every [SessionDataCleaner] of the platform's graph, read again on each session transition, so a cleaner contributed
 * later is never missed. Each cleaner appears once: Android hands over Hilt's set, which already holds Koin's
 * qualified cleaners through `SessionCleanersBridge`, so adding Koin's `getAll()` on top would run those twice; iOS,
 * whose graph is Koin only, reads `getAll()`.
 */
fun interface SessionDataCleaners {
    fun current(): Collection<SessionDataCleaner>
}
