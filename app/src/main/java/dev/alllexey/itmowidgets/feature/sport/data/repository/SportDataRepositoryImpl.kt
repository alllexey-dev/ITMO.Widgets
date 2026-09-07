package dev.alllexey.itmowidgets.feature.sport.data.repository

import api.myitmo.MyItmoApi
import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.network.toAppError
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.util.CustomDataState
import dev.alllexey.itmowidgets.core.util.DataState
import dev.alllexey.itmowidgets.core.util.dataOrNull
import dev.alllexey.itmowidgets.feature.sport.data.mapper.toModel
import dev.alllexey.itmowidgets.feature.sport.domain.model.FriendSportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAttempts
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignLimits
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueue
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SportDataRepositoryImpl @Inject constructor(
    private val friendRepository: FriendRepository,
    private val settings: AppSettingsStorage,
    private val myItmoApi: MyItmoApi,
    private val widgetsApi: ItmoWidgetsApi,
    private val scoreRepository: SportScoreRepositoryImpl
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
        val state = when (val result = scoreRepository.getSportScore()) {
            is AppResult.Success -> DataState.Success(result.value)
            is AppResult.Failure -> DataState.Error(result.error)
        }
        scoreFlow.emit(state)
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
                attemptsFlow.emit(DataState.Error(AppError.Unknown()))
            }

        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            attemptsFlow.emit(DataState.Error(error.toAppError()))
        }
    }

    override suspend fun refreshSportAutoSignLimits() {
        if (!settings.getCustomServicesEnabled()) {
            autoSignLimitsFlow.emit(CustomDataState.Disabled)
            return
        }

        try {
            val result = withContext(Dispatchers.IO) {
                widgetsApi.sportAutoSignLimits().data?.toModel()
            }

            if (result != null) {
                autoSignLimitsFlow.emit(CustomDataState.Success(result))
            } else {
                autoSignLimitsFlow.emit(CustomDataState.Error(AppError.Unknown()))
            }

        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            autoSignLimitsFlow.emit(CustomDataState.Error(error.toAppError()))
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

                    (
                        freeSign.await()
                            ?: throw RuntimeException("FreeSignEntries response is null")
                    ).map { it.toModel() } + (
                        autoSign.await()
                            ?: throw RuntimeException("AutoSignEntries response is null")
                    ).map { it.toModel() }
                }
            }

            queueEntriesFlow.emit(CustomDataState.Success(result))

        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            queueEntriesFlow.emit(CustomDataState.Error(error.toAppError()))
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

                    (
                        freeSign.await()
                            ?: throw RuntimeException("FreeSignQueues response is null")
                    ).map { it.toModel() } + (
                        autoSign.await()
                            ?: throw RuntimeException("AutoSignQueues response is null")
                    ).map { it.toModel() }
                }
            }

            queuesFlow.emit(CustomDataState.Success(result))

        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            queuesFlow.emit(CustomDataState.Error(error.toAppError()))
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
                val friends = friendRepository.currentFriends.orEmpty()
                return bookings.mapNotNull { booking ->
                    val friend = friends.find { it.isu == booking.isu }
                    friend?.let {
                        FriendSportBooking(
                            friend = it,
                            lessonId = booking.lessonId,
                            entry = booking.entry?.toModel()
                        )
                    }
                }
            }

            var mapped = map()

            if (mapped.size != bookings.size) {
                friendRepository.refreshFriendList()
                mapped = map()
            }

            friendsBookingsFlow.emit(CustomDataState.Success(mapped))

        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            friendsBookingsFlow.emit(CustomDataState.Error(error.toAppError()))
        }
    }
}
