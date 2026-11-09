package dev.alllexey.itmowidgets.feature.dormitory.data

import dev.alllexey.itmoapi.core.MyItmoException
import dev.alllexey.itmoapi.core.ResultResponse
import dev.alllexey.itmoapi.core.requireResult
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.appResultOf
import dev.alllexey.itmowidgets.core.result.valueOrNull
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
            return AppResult.Success(DemoDormitory.answer(clock.now(), time.today()).also { state.value = Loaded(null, it) })
        }
        // The stored answer first: the time `Место предоставлено` was first seen and the Договоры carry over from it.
        loaded()
        val started = generation.load()
        val fetchedAt = clock.now()
        val status = when (val result = appResultOf({ it.toDormitoryAppError() }) { withContext(dispatchers.io) { status() } }) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        // The Договоры are asked apart: their failure keeps the stored ones and never discards a status that loaded.
        val contracts = appResultOf({ it.toDormitoryAppError() }) { withContext(dispatchers.io) { contracts() } }
        return lock.withLock {
            val previous = state.value?.stored
            val stored = StoredDormitory(
                owner = currentUser.getCurrentUser()?.isu,
                fetchedAt = fetchedAt.toEpochMilliseconds(),
                status = status?.seenSince(state.value?.answer?.status, fetchedAt),
                contracts = contracts.valueOrNull() ?: previous?.contracts.orEmpty(),
                contractsFetchedAt = if (contracts is AppResult.Success) fetchedAt.toEpochMilliseconds() else previous?.contractsFetchedAt,
            )
            // Signed out while the answer was on its way: nothing of it stays.
            if (generation.load() != started) return@withLock AppResult.Success(stored.toAnswer())
            val written = appResultOf({ AppError.Unknown(it) }) { withContext(dispatchers.io) { store.write(stored) } }
            if (written is AppResult.Failure) return@withLock written
            val answer = stored.toAnswer()
            val before = state.value?.answer
            state.value = Loaded(stored, answer)
            bell.record(before, answer)
            (contracts as? AppResult.Failure) ?: AppResult.Success(answer)
        }
    }

    /** The status, null for no application, with the Документы для заселения only while the stage needs them. */
    private suspend fun status(): StoredStatus? {
        val status = client.dormitory.getDormitoryStatus().optionalResult() ?: return null
        val documents = if (stageOf(status.statusId)?.needsDocuments == true) {
            client.dormitory.getDormitoryDocuments().requireResult()
        } else {
            emptyList()
        }
        return status.toStored(documents)
    }

    /**
     * The Договоры найма of every period my.itmo lists, one request per period. Periods are calendar years and each
     * answer has every Договор overlapping it, so one running from September to June comes in two: it is kept once,
     * as the latest period has it.
     */
    private suspend fun contracts(): List<StoredContract> =
        client.dormitory.getDormitoryContractPeriods().optionalResult().orEmpty().sortedBy { it.dateFrom }
            .flatMap { period -> client.dormitory.getDormitoryContracts(period.dateFrom, period.dateTo).optionalResult().orEmpty() }
            .associateBy { it.contractId }.values
            .map { it.toStored(time.timeZone) }

    override suspend fun clearSessionData() = lock.withLock {
        generation.addAndFetch(1L)
        withContext(dispatchers.io) { store.clear() }
        state.value = Loaded(null, null)
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
            state.value = Loaded(stored, stored?.toAnswer())
        }
    }

    /** The stored answer in memory and what it reads as: both null before the first successful one. */
    private class Loaded(val stored: StoredDormitory?, val answer: DormitoryAnswer?)
}

/** A student without an application gets a successful envelope with a null result. */
private fun <T : Any> ResultResponse<T>.optionalResult(): T? {
    if (errorCode != 0) throw MyItmoException.Api(200, errorCode, errorMessage)
    return result
}
