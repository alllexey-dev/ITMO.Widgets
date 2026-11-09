package dev.alllexey.itmowidgets.feature.dormitory.data

import dev.alllexey.itmoapi.core.MyItmoException
import dev.alllexey.itmoapi.core.ResultResponse
import dev.alllexey.itmoapi.core.requireResult
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.appResultOf
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.dormitory.data.bell.DormitoryBellEvents
import dev.alllexey.itmowidgets.feature.dormitory.data.demo.DemoDormitory
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryAnswer
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryRepository
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One instance per process (the `dormitoryModule` single): the status from my.itmo through MyItmoApi, the Документы
 * для заселения only while the stage needs them, the Договоры найма of every period my.itmo lists, and the last successful
 * answer in [store] with its time, so the screen, Профиль and `Нужно сделать` read it offline, with the time the app
 * first saw `Место предоставлено`; what changed against the previous answer goes to the [bell], whoever asked. The
 * demo answers from [DemoDormitory] and sends nothing. Sign-out removes the file; an answer that lands after it is
 * dropped. Nothing here is logged: numbers and amounts are personal financial data.
 */
@OptIn(ExperimentalAtomicApi::class)
class DormitoryRepositoryImpl internal constructor(
    private val client: MyItmoClient,
    private val demo: DemoMode,
    private val store: DormitoryFileStore,
    private val bell: DormitoryBellEvents,
    private val currentUser: CurrentUserProvider,
    private val clock: Clock,
    private val time: AcademicTimeProvider,
    private val dispatchers: AppDispatchers,
) : DormitoryRepository, SessionDataCleaner {

    private val state = MutableStateFlow<Loaded?>(null)
    private val lock = Mutex()
    private val generation = AtomicLong(0L)

    override fun observe(): Flow<DormitoryAnswer?> = state
        .onStart { loaded() }
        .filterNotNull()
        .map { it.answer }

    override suspend fun refresh(): AppResult<DormitoryAnswer> {
        if (demo.isActive()) {
            return AppResult.Success(DemoDormitory.answer(clock.now(), time.today()).also { state.value = Loaded(it) })
        }
        // The stored answer first: the time `Место предоставлено` was first seen carries over from it.
        loaded()
        val started = generation.load()
        val fetchedAt = clock.now()
        val result = appResultOf({ it.toDormitoryAppError() }) {
            withContext(dispatchers.io) {
                val status = client.dormitory.getDormitoryStatus().optionalResult()
                val documents = if (status != null && stageOf(status.statusId)?.needsDocuments == true) {
                    client.dormitory.getDormitoryDocuments().requireResult()
                } else {
                    emptyList()
                }
                StoredDormitory(
                    owner = currentUser.getCurrentUser()?.isu,
                    fetchedAt = fetchedAt.toEpochMilliseconds(),
                    status = status?.toStored(documents),
                    contracts = contracts(),
                )
            }
        }
        return when (result) {
            is AppResult.Failure -> result
            is AppResult.Success -> lock.withLock {
                // Signed out while the answer was on its way: nothing of it stays.
                if (generation.load() != started) return@withLock AppResult.Success(result.value.toAnswer())
                val previous = state.value?.answer
                val stored = result.value.copy(status = result.value.status?.seenSince(previous?.status, fetchedAt))
                withContext(dispatchers.io) { store.write(stored) }
                val answer = stored.toAnswer()
                state.value = Loaded(answer)
                bell.record(previous, answer)
                AppResult.Success(answer)
            }
        }
    }

    /**
     * The Договоры найма of every period my.itmo lists, one request per period. Periods are calendar years and each
     * answer has every Договор overlapping it, so one running from September to June comes in two: it is kept once,
     * as the latest period has it.
     */
    private suspend fun contracts(): List<StoredContract> =
        client.dormitory.getDormitoryContractPeriods().requireResult().sortedBy { it.dateFrom }
            .flatMap { period -> client.dormitory.getDormitoryContracts(period.dateFrom, period.dateTo).requireResult() }
            .associateBy { it.contractId }.values
            .map { it.toStored(time.timeZone) }

    override suspend fun clearSessionData() = lock.withLock {
        generation.addAndFetch(1L)
        withContext(dispatchers.io) { store.clear() }
        state.value = Loaded(null)
    }

    /** The stored answer once per process; a corrupt file or another account's file is removed. */
    private suspend fun loaded() {
        if (state.value != null) return
        lock.withLock {
            if (state.value != null) return
            val owner = currentUser.getCurrentUser()?.isu
            val stored = withContext(dispatchers.io) {
                val read = try {
                    store.read()
                } catch (_: Exception) {
                    store.clear()
                    null
                }
                if (read?.owner != null && read.owner != owner) {
                    store.clear()
                    null
                } else {
                    read
                }
            }
            state.value = Loaded(stored?.toAnswer())
        }
    }

    /** The answer in memory: [answer] null before the first successful one. */
    private class Loaded(val answer: DormitoryAnswer?)
}

/** A student without an application gets a successful envelope with a null result. */
private fun <T : Any> ResultResponse<T>.optionalResult(): T? {
    if (errorCode != 0) throw MyItmoException.Api(200, errorCode, errorMessage)
    return result
}
