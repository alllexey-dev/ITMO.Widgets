package dev.alllexey.itmowidgets.domain.repository

import dev.alllexey.itmowidgets.core.util.MergedDataState
import dev.alllexey.itmowidgets.domain.model.sport.SportBooking
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
