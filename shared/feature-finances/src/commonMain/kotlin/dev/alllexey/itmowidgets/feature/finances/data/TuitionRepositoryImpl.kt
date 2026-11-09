package dev.alllexey.itmowidgets.feature.finances.data

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
import dev.alllexey.itmowidgets.feature.finances.data.bell.TuitionBellEvents
import dev.alllexey.itmowidgets.feature.finances.data.demo.DemoTuition
import dev.alllexey.itmowidgets.feature.finances.domain.TuitionAnswer
import dev.alllexey.itmowidgets.feature.finances.domain.TuitionRepository
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
 * One instance per process (the `financesModule` single): whether my.itmo offers tuition to the student and, only
 * then, the Договоры об обучении with their Платежи, through MyItmoApi; the last successful answer in [store] with its
 * time, so the screen and Профиль read it offline; a Платёж that became paid since the previous answer goes to the
 * [bell]. The demo answers from [DemoTuition] and sends nothing. Sign-out removes the file; an answer that lands after
 * it is dropped. Nothing here is logged: numbers and amounts are personal financial data.
 */
@OptIn(ExperimentalAtomicApi::class)
class TuitionRepositoryImpl internal constructor(
    private val client: MyItmoClient,
    private val demo: DemoMode,
    private val store: TuitionFileStore,
    private val bell: TuitionBellEvents,
    private val currentUser: CurrentUserProvider,
    private val clock: Clock,
    private val time: AcademicTimeProvider,
    private val dispatchers: AppDispatchers,
) : TuitionRepository, SessionDataCleaner {

    private val state = MutableStateFlow<Loaded?>(null)
    private val lock = Mutex()
    private val generation = AtomicLong(0L)

    override fun observe(): Flow<TuitionAnswer?> = state
        .onStart { loaded() }
        .filterNotNull()
        .map { it.answer }

    override suspend fun refresh(): AppResult<TuitionAnswer> {
        if (demo.isActive()) return AppResult.Success(DemoTuition.answer(clock.now()).also { state.value = Loaded(it) })
        loaded()
        val started = generation.load()
        val fetchedAt = clock.now()
        val result = appResultOf({ it.toFinancesAppError() }) {
            withContext(dispatchers.io) {
                val available = client.finances.getTuitionAvailability().requireResult()
                StoredTuition(
                    owner = currentUser.getCurrentUser()?.isu,
                    fetchedAt = fetchedAt.toEpochMilliseconds(),
                    contracts = if (available) contracts() else emptyList(),
                )
            }
        }
        return when (result) {
            is AppResult.Failure -> result
            is AppResult.Success -> lock.withLock {
                // Signed out while the answer was on its way: nothing of it stays.
                if (generation.load() != started) return@withLock AppResult.Success(result.value.toAnswer())
                val previous = state.value?.answer
                withContext(dispatchers.io) { store.write(result.value) }
                val answer = result.value.toAnswer()
                state.value = Loaded(answer)
                bell.record(previous, answer)
                AppResult.Success(answer)
            }
        }
    }

    /** Every Договор об обучении with its balance and schedule; my.itmo may answer null for none. */
    private suspend fun contracts(): List<StoredContract> =
        client.finances.getTuitionPayments().optionalResult().orEmpty().map { it.toStored(time.timeZone) }

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
    private class Loaded(val answer: TuitionAnswer?)
}

/** A student without agreements may get a successful envelope with a null result. */
private fun <T : Any> ResultResponse<T>.optionalResult(): T? {
    if (errorCode != 0) throw MyItmoException.Api(200, errorCode, errorMessage)
    return result
}
