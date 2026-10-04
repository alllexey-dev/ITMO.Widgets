package dev.alllexey.itmowidgets.feature.sport.data.repository

import api.myitmo.MyItmoApi
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.result.valueOrNull
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.data.demo.DemoSport
import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.network.toAppError
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.sport.data.mapper.toModel
import dev.alllexey.itmowidgets.feature.sport.domain.model.FriendSportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAttempts
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignLimits
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueue
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SportDataRepositoryImpl @Inject constructor(
    private val friendRepository: FriendRepository,
    private val backend: BackendGate,
    private val myItmoApi: MyItmoApi,
    private val widgetsApi: ItmoWidgetsApi,
    private val scoreRepository: SportScoreRepositoryImpl,
    private val time: AcademicTimeProvider,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers
) : SportDataRepository, SessionDataCleaner {

    private val queueSessionMutex = Mutex()
    private var queueSessionGeneration = 0L

    private val attemptsFlow = MutableSharedFlow<AppResult<SportAttempts>>(replay = 1)
    private val scoreFlow = MutableSharedFlow<AppResult<SportScore>>(replay = 1)

    private val autoSignLimitsFlow =
        MutableSharedFlow<LoadState<SportAutoSignLimits>>(replay = 1)

    private val queueEntriesFlow =
        MutableSharedFlow<LoadState<List<SportQueueEntry>>>(replay = 1)

    private val queuesFlow =
        MutableSharedFlow<LoadState<List<SportQueue>>>(replay = 1)

    private val friendsBookingsFlow =
        MutableSharedFlow<LoadState<List<FriendSportBooking>>>(replay = 1)

    override fun observeSportScore() = scoreFlow
    override fun observeSportAttempts() = attemptsFlow

    override fun observeSportAutoSignLimits() = autoSignLimitsFlow
    override fun observeSportQueueEntries() = queueEntriesFlow
    override fun observeSportQueues() = queuesFlow
    override fun observeFriendsBookings() = friendsBookingsFlow

    override suspend fun refreshSportScore() {
        scoreFlow.emit(scoreRepository.getSportScore())
    }

    override suspend fun refreshSportAttempts() {
        if (demo.isActive()) {
            attemptsFlow.emit(AppResult.Success(DemoSport.attempts()))
            return
        }
        try {
            val result = withContext(dispatchers.io) {
                val response = myItmoApi.sportAttempts.execute()
                response.body()?.result?.toModel()
            }

            if (result != null) {
                attemptsFlow.emit(AppResult.Success(result))
            } else {
                attemptsFlow.emit(AppResult.Failure(AppError.Unknown()))
            }

        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            attemptsFlow.emit(AppResult.Failure(error.toAppError()))
        }
    }

    override suspend fun refreshSportAutoSignLimits() {
        if (demo.isActive()) {
            autoSignLimitsFlow.emit(LoadState.Content(DemoSport.autoSignLimits(time)))
            return
        }
        if (!backend.mayCallBackend()) {
            autoSignLimitsFlow.emit(LoadState.Disabled)
            return
        }

        try {
            val result = withContext(dispatchers.io) {
                widgetsApi.sportAutoSignLimits().data?.toModel()
            }

            if (result != null) {
                autoSignLimitsFlow.emit(LoadState.Content(result))
            } else {
                autoSignLimitsFlow.emit(LoadState.Error(AppError.Unknown()))
            }

        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            autoSignLimitsFlow.emit(LoadState.Error(error.toAppError()))
        }
    }

    override suspend fun refreshSportQueueEntries() {
        val generation = queueSessionMutex.withLock { queueSessionGeneration }
        if (demo.isActive()) {
            emitQueueState(generation, LoadState.Content(DemoSport.queueEntries(time)))
            return
        }
        if (!backend.mayCallBackend()) {
            emitQueueState(generation, LoadState.Disabled)
            return
        }

        try {
            val result = withContext(dispatchers.io) {
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

            emitQueueState(generation, LoadState.Content(result))

        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            emitQueueState(generation, LoadState.Error(error.toAppError()))
        }
    }

    private suspend fun emitQueueState(generation: Long, state: LoadState<List<SportQueueEntry>>) =
        queueSessionMutex.withLock {
            if (generation == queueSessionGeneration) queueEntriesFlow.emit(state)
        }

    override suspend fun clearSessionData() = queueSessionMutex.withLock {
        queueSessionGeneration++
        queueEntriesFlow.emit(LoadState.Disabled)
    }

    override suspend fun refreshSportQueues() {
        if (demo.isActive()) {
            queuesFlow.emit(LoadState.Content(DemoSport.queues(time)))
            return
        }
        if (!backend.mayCallBackend()) {
            queuesFlow.emit(LoadState.Disabled)
            return
        }

        try {
            val result = withContext(dispatchers.io) {
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

            queuesFlow.emit(LoadState.Content(result))

        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            queuesFlow.emit(LoadState.Error(error.toAppError()))
        }
    }

    override suspend fun refreshFriendsBookings() {
        if (demo.isActive()) {
            friendsBookingsFlow.emit(LoadState.Content(DemoSport.friendsBookings(time)))
            return
        }
        if (!backend.mayCallBackend()) {
            friendsBookingsFlow.emit(LoadState.Disabled)
            return
        }

        try {
            val bookings = withContext(dispatchers.io) {
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

            friendsBookingsFlow.emit(LoadState.Content(mapped))

        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            friendsBookingsFlow.emit(LoadState.Error(error.toAppError()))
        }
    }
}
