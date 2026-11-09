package dev.alllexey.itmowidgets.core.payments.check

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class StoredNotices(val sent: Map<String, Long> = emptyMap())

/**
 * The keys of the notices the check has posted, with when: `noBackup/payments_notices.json`, the last
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
    private var sent: Map<String, Long>? = null

    suspend fun sentKeys(): Set<String> = lock.withLock { loaded().keys }

    suspend fun record(keys: Collection<String>) = lock.withLock {
        if (keys.isEmpty()) return@withLock
        val now = clock.now().toEpochMilliseconds()
        val oldest = (clock.now() - RETENTION_DAYS.days).toEpochMilliseconds()
        val next = (loaded() + keys.associateWith { now }).filterValues { it >= oldest }
        withContext(dispatchers.io) { file.write(JSON.encodeToString(StoredNotices(next))) }
        sent = next
    }

    override suspend fun clearSessionData() = lock.withLock {
        withContext(dispatchers.io) { file.write(null) }
        sent = emptyMap()
    }

    /** Must hold [lock]. */
    private suspend fun loaded(): Map<String, Long> = sent ?: withContext(dispatchers.io) {
        try {
            file.read()?.let { JSON.decodeFromString<StoredNotices>(it).sent }.orEmpty()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            emptyMap()
        }
    }.also { sent = it }

    companion object {
        const val FILE_NAME = "payments_notices.json"

        /** The log over [file]: tests pass one on a fake file system; the app graph takes the directories. */
        fun over(file: AtomicTextFile, clock: Clock, dispatchers: AppDispatchers) = PaymentsNoticeLog(file, clock, dispatchers)

        /** Longer than any notice stays relevant: a reminder is due within days, a stage lasts weeks at most. */
        const val RETENTION_DAYS = 120

        private val JSON = Json { ignoreUnknownKeys = true }
    }
}
