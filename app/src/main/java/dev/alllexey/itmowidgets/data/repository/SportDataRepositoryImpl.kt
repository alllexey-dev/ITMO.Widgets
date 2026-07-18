package dev.alllexey.itmowidgets.data.repository

import api.myitmo.MyItmoApi
import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.debug.SportScoreOverrideProvider
import dev.alllexey.itmowidgets.core.model.SportAutoSignLimits
import dev.alllexey.itmowidgets.core.model.SportQueue
import dev.alllexey.itmowidgets.core.model.SportQueueEntry
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.util.CustomDataState
import dev.alllexey.itmowidgets.core.util.DataState
import dev.alllexey.itmowidgets.core.util.dataOrNull
import dev.alllexey.itmowidgets.data.mapper.toModel
import dev.alllexey.itmowidgets.domain.model.sport.FriendSportBooking
import dev.alllexey.itmowidgets.domain.model.sport.SportAttempts
import dev.alllexey.itmowidgets.domain.model.sport.SportScore
import dev.alllexey.itmowidgets.domain.repository.FriendRepository
import dev.alllexey.itmowidgets.domain.repository.SportDataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SportDataRepositoryImpl @Inject constructor(
    val friendRepository: FriendRepository,
    val settings: AppSettingsStorage,
    val myItmoApi: MyItmoApi,
    val widgetsApi: ItmoWidgetsApi,
    private val sportScoreOverrideProvider: SportScoreOverrideProvider
) : SportDataRepository {

    private val attemptsFlow = MutableSharedFlow<DataState<SportAttempts>>(replay = 1)
    private val scoreFlow = MutableSharedFlow<DataState<SportScore>>(replay = 1)

    private val autoSignLimitsFlow =
        MutableSharedFlow<CustomDataState<SportAutoSignLimits>>(replay = 1)

    private val queueEntriesFlow =
        MutableSharedFlow<CustomDataState<List<SportQueueEntry>>>(replay = 1)

    private val queuesFlow =
        MutableSharedFlow<CustomDataState<List<SportQueue>>>(replay = 1)

    private val friendsBookingsFlow =
        MutableSharedFlow<CustomDataState<List<FriendSportBooking>>>(replay = 1)

    override fun observeSportScore() = scoreFlow
    override fun observeSportAttempts() = attemptsFlow

    override fun observeSportAutoSignLimits() = autoSignLimitsFlow
    override fun observeSportQueueEntries() = queueEntriesFlow
    override fun observeSportQueues() = queuesFlow
    override fun observeFriendsBookings() = friendsBookingsFlow

    override suspend fun refreshSportScore() {
        try {
            val result = withContext(Dispatchers.IO) {
                val response = myItmoApi.getSportScore(null).execute()
                response.body()?.result?.toModel()?.let(sportScoreOverrideProvider::apply)
            }

            if (result != null) {
                scoreFlow.emit(DataState.Success(result))
            } else {
                scoreFlow.emit(DataState.Error(Exception("Empty body")))
            }

        } catch (e: Exception) {
            scoreFlow.emit(DataState.Error(e))
        }
    }

    override suspend fun refreshSportAttempts() {
        try {
            val result = withContext(Dispatchers.IO) {
                val response = myItmoApi.sportAttempts.execute()
                response.body()?.result?.toModel()
            }

            if (result != null) {
                attemptsFlow.emit(DataState.Success(result))
            } else {
                attemptsFlow.emit(DataState.Error(Exception("Empty body")))
            }

        } catch (e: Exception) {
            attemptsFlow.emit(DataState.Error(e))
        }
    }

    override suspend fun refreshSportAutoSignLimits() {
        if (!settings.getCustomServicesEnabled()) {
            autoSignLimitsFlow.emit(CustomDataState.Disabled)
            return
        }

        try {
            val result = withContext(Dispatchers.IO) {
                widgetsApi.sportAutoSignLimits().data
            }

            if (result != null) {
                autoSignLimitsFlow.emit(CustomDataState.Success(result))
            } else {
                autoSignLimitsFlow.emit(CustomDataState.Error(Exception("Empty data")))
            }

        } catch (e: Exception) {
            autoSignLimitsFlow.emit(CustomDataState.Error(e))
        }
    }

    override suspend fun refreshSportQueueEntries() {
        if (!settings.getCustomServicesEnabled()) {
            queueEntriesFlow.emit(CustomDataState.Disabled)
            return
        }

        try {
            val result = withContext(Dispatchers.IO) {
                supervisorScope {
                    val freeSign = async {
                        widgetsApi.mySportFreeSignEntries().data
                    }
                    val autoSign = async {
                        widgetsApi.mySportAutoSignEntries().data
                    }

                    (freeSign.await() ?: throw RuntimeException("FreeSignEntries response is null")) +
                            (autoSign.await() ?: throw RuntimeException("AutoSignEntries response is null"))
                }
            }

            queueEntriesFlow.emit(CustomDataState.Success(result))

        } catch (e: Exception) {
            queueEntriesFlow.emit(CustomDataState.Error(e))
        }
    }

    override suspend fun refreshSportQueues() {
        if (!settings.getCustomServicesEnabled()) {
            queuesFlow.emit(CustomDataState.Disabled)
            return
        }

        try {
            val result = withContext(Dispatchers.IO) {
                supervisorScope {
                    val freeSign = async {
                        widgetsApi.currentSportFreeSignQueues().data
                    }
                    val autoSign = async {
                        widgetsApi.currentSportAutoSignQueues().data
                    }

                    (freeSign.await() ?: throw RuntimeException("FreeSignQueues response is null")) +
                            (autoSign.await() ?: throw RuntimeException("AutoSignQueues response is null"))
                }
            }

            queuesFlow.emit(CustomDataState.Success(result))

        } catch (e: Exception) {
            queuesFlow.emit(CustomDataState.Error(e))
        }
    }

    override suspend fun refreshFriendsBookings() {
        if (!settings.getCustomServicesEnabled()) {
            friendsBookingsFlow.emit(CustomDataState.Disabled)
            return
        }

        try {
            val bookings = withContext(Dispatchers.IO) {
                widgetsApi.friendsSportBookings().data?.bookings
            } ?: throw RuntimeException("FriendSportBookings response is null")

            fun map(): List<FriendSportBooking> {
                val friends = friendRepository.currentFriends?.dataOrNull().orEmpty()
                return bookings.mapNotNull { booking ->
                    val friend = friends.find { it.isu == booking.isu }
                    friend?.let {
                        FriendSportBooking(it, booking.lessonId, booking.entry)
                    }
                }
            }

            var mapped = map()

            if (mapped.size != bookings.size) {
                friendRepository.refreshFriendList()
                mapped = map()
            }

            friendsBookingsFlow.emit(CustomDataState.Success(mapped))

        } catch (e: Exception) {
            friendsBookingsFlow.emit(CustomDataState.Error(e))
        }
    }
}
