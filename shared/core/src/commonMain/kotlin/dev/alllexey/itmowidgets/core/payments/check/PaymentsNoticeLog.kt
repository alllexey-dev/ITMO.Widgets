package dev.alllexey.itmowidgets.core.payments.check

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class StoredNotices(val sent: Map<String, Long> = emptyMap(), val attempts: Map<String, Long> = emptyMap())

/**
 * The keys of the notices the check has handled (posted, or found with the switch off), with when, and when it last
 * asked my.itmo for each part (`PaymentsCheckPart.feature`): `noBackup/payments_notices.json`, keys of the last
 * [RETENTION_DAYS] days, cleared with the session. Keys hold ITMO's ids and dates, never a number or an amount. A
 * corrupt file starts empty.
 */
class PaymentsNoticeLog private constructor(
    private val file: AtomicTextFile,
    private val clock: Clock,
    private val dispatchers: AppDispatchers,
) : SessionDataCleaner {

    constructor(directories: AppDirectories, clock: Clock, dispatchers: AppDispatchers) :
        this(AtomicTextFile(directories.noBackup / FILE_NAME), clock, dispatchers)

    private val lock = Mutex()
    private var stored: StoredNotices? = null

    suspend fun sentKeys(): Set<String> = lock.withLock { loaded().sent.keys }

    suspend fun record(keys: Collection<String>) = lock.withLock {
        if (keys.isEmpty()) return@withLock
        val now = clock.now().toEpochMilliseconds()
        val oldest = (clock.now() - RETENTION_DAYS.days).toEpochMilliseconds()
        val current = loaded()
        save(current.copy(sent = (current.sent + keys.associateWith { now }).filterValues { it >= oldest }))
    }

    /** When the check last asked my.itmo for each part, by its feature. */
    suspend fun lastAttempts(): Map<String, Instant> =
        lock.withLock { loaded().attempts.mapValues { Instant.fromEpochMilliseconds(it.value) } }

    suspend fun recordAttempts(features: Collection<String>, at: Instant) = lock.withLock {
        if (features.isEmpty()) return@withLock
        val current = loaded()
        save(current.copy(attempts = current.attempts + features.associateWith { at.toEpochMilliseconds() }))
    }

    override suspend fun clearSessionData() = lock.withLock {
        withContext(dispatchers.io) { file.write(null) }
        stored = StoredNotices()
    }

    /** Must hold [lock]. */
    private suspend fun save(next: StoredNotices) {
        withContext(dispatchers.io) { file.write(JSON.encodeToString(next)) }
        stored = next
    }

    /** Must hold [lock]. */
    private suspend fun loaded(): StoredNotices = stored ?: withContext(dispatchers.io) {
        try {
            file.read()?.let { JSON.decodeFromString<StoredNotices>(it) } ?: StoredNotices()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            StoredNotices()
        }
    }.also { stored = it }

    companion object {
        const val FILE_NAME = "payments_notices.json"

        /** The log over [file]: tests pass one on a fake file system; the app graph takes the directories. */
        fun over(file: AtomicTextFile, clock: Clock, dispatchers: AppDispatchers) = PaymentsNoticeLog(file, clock, dispatchers)

        /** Longer than any notice stays relevant: a reminder is due within days, a stage lasts weeks at most. */
        const val RETENTION_DAYS = 120

        private val JSON = Json { ignoreUnknownKeys = true }
    }
}
