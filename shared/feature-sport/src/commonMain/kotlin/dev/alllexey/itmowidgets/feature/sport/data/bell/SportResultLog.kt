package dev.alllexey.itmowidgets.feature.sport.data.bell

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import dev.alllexey.itmowidgets.feature.sport.data.push.SportSignNotice
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

/** A queue's result: MyITMO [booked] the lesson or refused it; [at] is when the push was handled. */
@Serializable
data class SportResult(val lessonId: Long, val sectionName: String, val start: Long, val booked: Boolean, val at: Long) {
    val startsAt: Instant get() = Instant.fromEpochMilliseconds(start)
    val handledAt: Instant get() = Instant.fromEpochMilliseconds(at)
}

@Serializable
private data class StoredSportResults(val results: List<SportResult> = emptyList())

/**
 * What the queue pushes did, for the bell: `noBackup/sport_results.json`, the last [RETENTION_DAYS] days and at most
 * [MAX_RESULTS] results, cleared with the session. A corrupt file starts empty.
 */
class SportResultLog internal constructor(
    private val file: AtomicTextFile,
    private val clock: Clock,
    private val dispatchers: AppDispatchers,
) : SessionDataCleaner {

    constructor(directories: AppDirectories, clock: Clock, dispatchers: AppDispatchers) :
        this(AtomicTextFile(directories.noBackup / FILE_NAME), clock, dispatchers)
    private val lock = Mutex()
    private val state = MutableStateFlow<List<SportResult>?>(null)

    fun observe(): Flow<List<SportResult>> = flow {
        lock.withLock { loaded() }
        emitAll(state.filterNotNull())
    }

    suspend fun add(notice: SportSignNotice) = lock.withLock {
        val now = clock.now()
        val result = SportResult(
            notice.lessonId, notice.sectionName, notice.start.toEpochMilliseconds(), notice.booked, now.toEpochMilliseconds(),
        )
        val oldest = (now - RETENTION_DAYS.days).toEpochMilliseconds()
        val next = (listOf(result) + loaded().filterNot { it.lessonId == notice.lessonId })
            .filter { it.at >= oldest }
            .take(MAX_RESULTS)
        withContext(dispatchers.io) { file.write(JSON.encodeToString(StoredSportResults(next))) }
        state.value = next
    }

    override suspend fun clearSessionData() = lock.withLock {
        withContext(dispatchers.io) { file.write(null) }
        state.value = emptyList()
    }

    /** Must hold [lock]. */
    private suspend fun loaded(): List<SportResult> = state.value ?: withContext(dispatchers.io) {
        try {
            file.read()?.let { JSON.decodeFromString<StoredSportResults>(it).results }.orEmpty()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            emptyList()
        }
    }.also { state.value = it }

    internal companion object {
        const val FILE_NAME = "sport_results.json"
        const val RETENTION_DAYS = 30
        const val MAX_RESULTS = 50
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
