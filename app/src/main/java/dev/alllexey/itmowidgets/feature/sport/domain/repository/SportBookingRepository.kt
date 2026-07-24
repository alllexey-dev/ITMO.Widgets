package dev.alllexey.itmowidgets.feature.sport.domain.repository

import dev.alllexey.itmowidgets.core.util.MergedDataState
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import kotlinx.coroutines.flow.Flow

interface SportBookingRepository {

    /**
     * Depends on:
     * - Sport bookings (ITMO)
     * - User sign entries
     * - Friends sport bookings
     */
    fun observeSportBookings(): Flow<MergedDataState<List<SportBooking>>>

    suspend fun refreshSportBookings()
}
