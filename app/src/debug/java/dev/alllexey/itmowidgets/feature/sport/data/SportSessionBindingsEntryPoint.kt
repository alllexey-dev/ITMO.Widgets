package dev.alllexey.itmowidgets.feature.sport.data

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository

/** Debug-only access to real bindings for identity checks; no session operations. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SportSessionBindingsEntryPoint {
    fun sportBookings(): SportBookingRepository
    fun sportData(): SportDataRepository
    fun sessionDataCleaners(): Set<@JvmSuppressWildcards SessionDataCleaner>
}
