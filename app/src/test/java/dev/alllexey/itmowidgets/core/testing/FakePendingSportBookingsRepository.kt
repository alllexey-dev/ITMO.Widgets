package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.util.DataState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Own pending sport in [values], a success of [initial] at first; observations and refreshes are counted. */
class FakePendingSportBookingsRepository(vararg initial: PendingSportBooking) : PendingSportBookingsRepository {
    val values = MutableStateFlow<DataState<List<PendingSportBooking>>>(DataState.Success(initial.toList()))
    var observations = 0
        private set
    var refreshes = 0
        private set
    var refreshBlock: suspend () -> Unit = {}

    override fun observePendingBookings(): Flow<DataState<List<PendingSportBooking>>> = values.also { observations++ }

    override suspend fun refresh() {
        refreshes++
        refreshBlock()
    }
}
