package dev.alllexey.itmowidgets.feature.sport.domain.repository

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import kotlinx.coroutines.flow.Flow

interface SportBookingRepository {

    /** Official own bookings, independent of queues and friend enrichment. */
    fun observeConfirmedSportBookings(): Flow<AppResult<List<SportBooking>>>

    /**
     * Depends on:
     * - Sport bookings (ITMO)
     * - User sign entries
     * - Friends sport bookings
     */
    fun observeSportBookings(): Flow<LoadState<List<SportBooking>>>

    suspend fun refreshSportBookings()
}
