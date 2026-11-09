package dev.alllexey.itmowidgets.core.bell

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One event a feature found when its stored answer changed, kept for its [BellSource]: a stable [id], the feature's
 * own [type] and [values], and [at], when the app saw it (epoch milliseconds).
 */
@Serializable
data class BellEvent(val id: String, val type: String, val at: Long, val values: List<String> = emptyList()) {
    val seenAt: Instant get() = Instant.fromEpochMilliseconds(at)
}

@Serializable
private data class StoredBellEvents(val events: List<BellEvent> = emptyList())

/**
 * A feature's bell events in one file: the last [RETENTION_DAYS] days and at most [MAX_EVENTS], newest first, an event
 * added again by its id kept once; cleared with the session. A corrupt file starts empty. Each feature extends it with
 * its own file, so its repository and its [BellSource] share one instance.
 */
open class BellEventStore(
    private val file: AtomicTextFile,
    private val clock: Clock,
    private val dispatchers: AppDispatchers,
) : SessionDataCleaner {

    private val lock = Mutex()
    private val state = MutableStateFlow<List<BellEvent>?>(null)

    fun observe(): Flow<List<BellEvent>> = flow {
        lock.withLock { loaded() }
        emitAll(state.filterNotNull())
    }

    suspend fun add(events: List<BellEvent>) = lock.withLock {
        if (events.isEmpty()) return@withLock
        val oldest = (clock.now() - RETENTION_DAYS.days).toEpochMilliseconds()
        val ids = events.mapTo(mutableSetOf()) { it.id }
        val next = (events + loaded().filterNot { it.id in ids })
            .distinctBy { it.id }
            .sortedByDescending { it.at }
            .filter { it.at >= oldest }
            .take(MAX_EVENTS)
        withContext(dispatchers.io) { file.write(JSON.encodeToString(StoredBellEvents(next))) }
        state.value = next
    }

    override suspend fun clearSessionData() = lock.withLock {
        withContext(dispatchers.io) { file.write(null) }
        state.value = emptyList()
    }

    /** Must hold [lock]. */
    private suspend fun loaded(): List<BellEvent> = state.value ?: withContext(dispatchers.io) {
        try {
            file.read()?.let { JSON.decodeFromString<StoredBellEvents>(it).events }.orEmpty()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            emptyList()
        }
    }.also { state.value = it }

    companion object {
        const val RETENTION_DAYS = 60
        const val MAX_EVENTS = 50
        private val JSON = Json { ignoreUnknownKeys = true }
    }
}
