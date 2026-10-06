package dev.alllexey.itmowidgets.feature.sport.data

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner

/**
 * Debug-only access to Hilt's `Set<SessionDataCleaner>`, which sign-out iterates, for identity checks against the
 * repositories Koin builds (`sportModule`); no session operations.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SportSessionBindingsEntryPoint {
    fun sessionDataCleaners(): Set<@JvmSuppressWildcards SessionDataCleaner>
}
