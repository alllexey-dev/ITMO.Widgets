package dev.alllexey.itmowidgets.feature.auth.data

import dev.alllexey.itmowidgets.core.session.SessionDataCleaner

/**
 * Every [SessionDataCleaner] of the platform's graph, read again on each session transition, so a cleaner contributed
 * later is never missed. Both platforms read Koin's `getAll()`; each contribution carries its own qualifier.
 */
fun interface SessionDataCleaners {
    fun current(): Collection<SessionDataCleaner>
}
