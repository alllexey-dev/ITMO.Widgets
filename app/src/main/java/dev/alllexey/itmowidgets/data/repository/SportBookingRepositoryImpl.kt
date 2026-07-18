package dev.alllexey.itmowidgets.data.repository

import api.myitmo.MyItmoApi
import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.core.model.SportFreeSignEntry
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.util.DataState
import dev.alllexey.itmowidgets.core.util.MergedDataState
import dev.alllexey.itmowidgets.core.util.dataOrNull
import dev.alllexey.itmowidgets.core.util.throwableOrNull
import dev.alllexey.itmowidgets.data.mapper.toBooking
import dev.alllexey.itmowidgets.data.mapper.toBookings
import dev.alllexey.itmowidgets.domain.model.sport.SportBooking
import dev.alllexey.itmowidgets.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.domain.repository.SportDataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SportBookingRepositoryImpl @Inject constructor(
    val settings: AppSettingsStorage,
    val sportDataRepository: SportDataRepository,
    val myItmoApi: MyItmoApi,
    val widgetsApi: ItmoWidgetsApi
) : SportBookingRepository {

    private val bookingsFlow = MutableSharedFlow<DataState<List<SportBooking>>>(replay = 1)

    private val combined = combine(
        bookingsFlow,
        sportDataRepository.observeSportQueueEntries(),
        sportDataRepository.observeFriendsBookings()
    ) { bookings, queueState, friendsState ->

        bookings.throwableOrNull()?.let {
            return@combine MergedDataState.Error(it)
        }

        val errors = listOfNotNull(
            queueState.throwableOrNull(),
            friendsState.throwableOrNull()
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

            // sync bookings
            if (settings.getCustomServicesEnabled()) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        widgetsApi.syncSportLessons(result.map { it.lessonId })
                    } catch (e: Exception) {
                        // todo: proper exception handling
                        println("couldn't sync lessons")
                        e.printStackTrace()
                    }
                }
            }

            bookingsFlow.emit(DataState.Success(result))

        } catch (e: Exception) {
            bookingsFlow.emit(DataState.Error(e))
        }
    }
}
