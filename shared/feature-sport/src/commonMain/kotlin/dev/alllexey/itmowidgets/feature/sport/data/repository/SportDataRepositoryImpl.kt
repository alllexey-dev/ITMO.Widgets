package dev.alllexey.itmowidgets.feature.sport.data.repository

import dev.alllexey.itmoapi.core.requireResult
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.client.sport.SportApi
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.result.valueOrNull
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.data.demo.DemoSport
import dev.alllexey.itmowidgets.core.friend.FriendRepository
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

class SportDataRepositoryImpl(
    private val friendRepository: FriendRepository,
    private val backend: BackendGate,
    private val myItmo: MyItmoClient,
    private val sportApi: SportApi,
    private val scoreRepository: SportScoreRepositoryImpl,
    private val time: AcademicTimeProvider,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers
) : SportDataRepository, SessionDataCleaner {

    /** Bumped by [clearSessionData], so a response of the previous session cannot land in the next one. */
    private val sessionMutex = Mutex()
    private var sessionGeneration = 0L

    private val attemptsFlow = MutableSharedFlow<LoadState<SportAttempts>>(replay = 1)
    private val scoreFlow = MutableSharedFlow<LoadState<SportScore>>(replay = 1)

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
        val generation = currentGeneration()
        val state = when (val result = scoreRepository.getSportScore()) {
            is AppResult.Success -> LoadState.Content(result.value)
            is AppResult.Failure -> LoadState.Error(result.error)
        }
        emitInSession(generation, scoreFlow, state)
    }

    override suspend fun refreshSportAttempts() {
        val generation = currentGeneration()
        if (demo.isActive()) {
            emitInSession(generation, attemptsFlow, LoadState.Content(DemoSport.attempts()))
            return
        }
        try {
            val result = withContext(dispatchers.io) { myItmo.sport.getSportAttempts().requireResult().toModel() }
            emitInSession(generation, attemptsFlow, LoadState.Content(result))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            emitInSession(generation, attemptsFlow, LoadState.Error(error.toSportAppError()))
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
            val result = withContext(dispatchers.io) { sportApi.sportAutoSignLimits().toModel() }
            autoSignLimitsFlow.emit(LoadState.Content(result))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            autoSignLimitsFlow.emit(LoadState.Error(error.toSportAppError()))
        }
    }

    override suspend fun refreshSportQueueEntries() {
        val generation = currentGeneration()
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
                    val freeSign = async { sportApi.mySportFreeSignEntries() }
                    val autoSign = async { sportApi.mySportAutoSignEntries() }
                    freeSign.await().map { it.toModel() } + autoSign.await().map { it.toModel() }
                }
            }

            emitQueueState(generation, LoadState.Content(result))

        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            emitQueueState(generation, LoadState.Error(error.toSportAppError()))
        }
    }

    private suspend fun emitQueueState(generation: Long, state: LoadState<List<SportQueueEntry>>) =
        emitInSession(generation, queueEntriesFlow, state)

    private suspend fun currentGeneration(): Long = sessionMutex.withLock { sessionGeneration }

    private suspend fun <T> emitInSession(generation: Long, flow: MutableSharedFlow<T>, value: T) =
        sessionMutex.withLock {
            if (generation == sessionGeneration) flow.emit(value)
        }

    /**
     * Forgets the previous session's points, attempts and queues, failures included: `Loading` makes the next
     * entry of the sport tab load them again instead of showing another account's data or its expired session.
     */
    override suspend fun clearSessionData() = sessionMutex.withLock {
        sessionGeneration++
        queueEntriesFlow.emit(LoadState.Disabled)
        attemptsFlow.emit(LoadState.Loading)
        scoreFlow.emit(LoadState.Loading)
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
                    val freeSign = async { sportApi.currentSportFreeSignQueues() }
                    val autoSign = async { sportApi.currentSportAutoSignQueues() }
                    freeSign.await().map { it.toModel() } + autoSign.await().map { it.toModel() }
                }
            }

            queuesFlow.emit(LoadState.Content(result))

        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            queuesFlow.emit(LoadState.Error(error.toSportAppError()))
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
            val bookings = withContext(dispatchers.io) { sportApi.friendsSportBookings().bookings }

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
            friendsBookingsFlow.emit(LoadState.Error(error.toSportAppError()))
        }
    }
}
