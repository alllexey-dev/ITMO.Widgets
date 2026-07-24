package dev.alllexey.itmowidgets.feature.sport.data.repository

import api.myitmo.MyItmoApi
import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.network.toAppError
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.util.DataState
import dev.alllexey.itmowidgets.core.util.MergedDataState
import dev.alllexey.itmowidgets.core.util.dataOrNull
import dev.alllexey.itmowidgets.core.util.errorOrNull
import dev.alllexey.itmowidgets.feature.sport.data.mapper.toBooking
import dev.alllexey.itmowidgets.feature.sport.data.mapper.toBookings
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFreeSignEntry
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SportBookingRepositoryImpl @Inject constructor(
    private val settings: AppSettingsStorage,
    private val sportDataRepository: SportDataRepository,
    private val myItmoApi: MyItmoApi,
    private val widgetsApi: ItmoWidgetsApi
) : SportBookingRepository {

    private val bookingsFlow = MutableSharedFlow<DataState<List<SportBooking>>>(replay = 1)

    private val combined = combine(
        bookingsFlow,
        sportDataRepository.observeSportQueueEntries(),
        sportDataRepository.observeFriendsBookings()
    ) { bookings, queueState, friendsState ->

        bookings.errorOrNull()?.let {
            return@combine MergedDataState.Error(it)
        }

        val errors = listOfNotNull(
            queueState.errorOrNull(),
            friendsState.errorOrNull()
        )

        val queueEntries = queueState.dataOrNull().orEmpty()
        val friendsBookings = friendsState.dataOrNull().orEmpty()
        val friendsBookingsByLesson = friendsBookings.groupBy {
            if (it.entry is SportAutoSignEntry) it.entry.realLesson?.id ?: -it.entry.targetLesson.id
            else it.entry?.targetLesson?.id ?: it.lessonId
        }

        val bookingsByLesson = bookings.dataOrNull().orEmpty()
            .associateBy { it.lessonId }.toMutableMap()

        queueEntries.forEach { entry ->
            val lessonId = when (entry) {
                is SportFreeSignEntry -> entry.targetLesson.id
                is SportAutoSignEntry -> entry.realLesson?.id ?: -entry.targetLesson.id
            }

            val existing = bookingsByLesson[lessonId]

            bookingsByLesson[lessonId] =
                existing?.copy(signEntry = entry) ?: entry.toBooking()
        }

        friendsBookingsByLesson.forEach { (lessonId, bookings) ->
            bookingsByLesson[lessonId]?.let {
                bookingsByLesson[lessonId] = it.copy(friendsBookings = bookings)
            }
        }

        val data = bookingsByLesson.values.sortedBy { it.start }
        MergedDataState.of(data, errors.firstOrNull())
    }

    override fun observeSportBookings() = combined

    override suspend fun refreshSportBookings() {
        try {
            val result = withContext(Dispatchers.IO) {
                val response = myItmoApi.chosenSportSections.execute()
                response.body()?.result?.flatMap { it.toBookings() }
            } ?: emptyList()

            if (settings.getCustomServicesEnabled()) {
                try {
                    withContext(Dispatchers.IO) {
                        widgetsApi.syncSportLessons(result.map { it.lessonId })
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    // Synchronization is optional and must not hide valid MyITMO bookings.
                }
            }

            bookingsFlow.emit(DataState.Success(result))

        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            bookingsFlow.emit(DataState.Error(error.toAppError()))
        }
    }
}
