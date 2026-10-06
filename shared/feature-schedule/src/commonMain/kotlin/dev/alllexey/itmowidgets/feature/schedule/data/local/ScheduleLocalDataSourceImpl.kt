package dev.alllexey.itmowidgets.feature.schedule.data.local

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.schedule.ScheduleUtil
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.gzip
import okio.use
import kotlin.time.Clock

private const val SCHEDULE_CACHE_EXPIRATION_MS = 24 * 60 * 60 * 1000L

private fun datesBetween(start: LocalDate, end: LocalDate): List<LocalDate> = ScheduleUtil.generateDates(start, end)

/**
 * Days of the signed-in account and of friends in `cacheDir/schedule_cache` ([AppDirectories.cache]), one gzipped
 * [StoredCacheEntry] per user and date as 2.2 wrote it. An entry expires 24 hours after it was saved on the wall
 * [clock], never on academic time.
 */
class ScheduleLocalDataSourceImpl internal constructor(
    private val clock: Clock,
    internal val cacheDir: Path,
    private val dispatchers: AppDispatchers,
    private val fileSystem: FileSystem
) : ScheduleLocalDataSource {

    constructor(clock: Clock, directories: AppDirectories, dispatchers: AppDispatchers) :
        this(clock, directories.cache / "schedule_cache", dispatchers, ScheduleFileSystem)

    // A map is published only after a complete mutation. Null remembers a cache miss.
    private val memoryCache = MutableStateFlow<Map<String, CacheEntry?>>(emptyMap())
    private val cacheMutex = Mutex()

    init {
        try {
            fileSystem.createDirectories(cacheDir)
        } catch (_: Exception) {}
    }

    /**
     * Reading the cache touches the disk, so the whole upstream — including the
     * lazy range hydration and JSON deserialization — runs on the IO dispatcher.
     */
    override fun observeRange(
        userIsu: Int?,
        start: LocalDate,
        end: LocalDate
    ): Flow<List<DaySchedule>> {

        val keys = datesBetween(start, end).map { key(userIsu, it) }

        return flow {
            cacheMutex.withLock {
                val snapshot = memoryCache.value
                val missing = keys.filterNot(snapshot::containsKey)
                if (missing.isNotEmpty()) {
                    memoryCache.value = snapshot + missing.associateWith(::readFromDisk)
                }
            }
            emitAll(
                memoryCache.map { snapshot ->
                    keys.mapNotNull { key ->
                        val entry = snapshot[key]
                        entry?.takeUnless(::isExpired)?.let(::deserialize)
                    }
                }.distinctUntilChanged()
            )
        }.flowOn(dispatchers.io)
    }

    override fun peekRange(userIsu: Int?, start: LocalDate, end: LocalDate): List<DaySchedule>? {
        val snapshot = memoryCache.value
        val keys = datesBetween(start, end).map { key(userIsu, it) }
        if (!keys.all(snapshot::containsKey)) return null
        return keys.mapNotNull { key -> snapshot[key]?.takeUnless(::isExpired)?.let(::deserialize) }
    }

    override suspend fun save(schedule: DaySchedule, userIsu: Int?) {
        withContext(dispatchers.io) {
            cacheMutex.withLock {
                val key = key(userIsu, schedule.date)
                val entry = cacheEntry(schedule, userIsu, nowMillis())
                writeToDisk(key, entry)
                memoryCache.value = memoryCache.value + (key to entry)
            }
        }
    }

    override suspend fun replaceRange(
        userIsu: Int?,
        start: LocalDate,
        end: LocalDate,
        schedules: List<DaySchedule>
    ) {
        val dates = datesBetween(start, end)
        if (dates.isEmpty()) return
        withContext(dispatchers.io) {
            cacheMutex.withLock {
                val timestamp = nowMillis()
                val replacement = schedules
                    .filter { it.date in start..end }
                    .associate { key(userIsu, it.date) to cacheEntry(it, userIsu, timestamp) }
                val updated = memoryCache.value.toMutableMap()
                dates.forEach { date ->
                    val key = key(userIsu, date)
                    val entry = replacement[key]
                    if (entry == null) delete(file(key)) else writeToDisk(key, entry)
                    updated[key] = entry
                }
                memoryCache.value = updated
            }
        }
    }

    override suspend fun get(userIsu: Int?, date: LocalDate): CacheEntry? {
        val key = key(userIsu, date)
        return withContext(dispatchers.io) {
            cacheMutex.withLock { readFromDisk(key)?.takeUnless(::isExpired) }
        }
    }

    override suspend fun clearUser(userIsu: Int) {
        withContext(dispatchers.io) {
            cacheMutex.withLock {
                val prefix = "${userIsu}_"
                fileSystem.listOrNull(cacheDir)?.filter { it.name.startsWith(prefix) }?.forEach(::delete)
                memoryCache.value = memoryCache.value.filterKeys { !it.startsWith(prefix) }
            }
        }
    }

    /**
     * Existing observers remain attached to the same snapshot flow across a clear.
     */
    override suspend fun clear() {
        withContext(dispatchers.io) {
            cacheMutex.withLock {
                try {
                    fileSystem.deleteRecursively(cacheDir)
                    fileSystem.createDirectories(cacheDir)
                } catch (_: Exception) {}
                memoryCache.value = emptyMap()
            }
        }
    }

    fun isExpired(entry: CacheEntry): Boolean {
        return nowMillis() - entry.timestamp > SCHEDULE_CACHE_EXPIRATION_MS
    }

    private fun nowMillis(): Long = clock.now().toEpochMilliseconds()

    private fun key(userIsu: Int?, date: LocalDate) =
        "${userIsu ?: "default"}_$date"

    private fun deserialize(entry: CacheEntry): DaySchedule =
        ScheduleStoreJson.decodeFromString<StoredDaySchedule>(entry.data).toModel()

    private fun cacheEntry(schedule: DaySchedule, userIsu: Int?, timestamp: Long) = CacheEntry(
        userIsu = userIsu,
        date = schedule.date,
        timestamp = timestamp,
        data = ScheduleStoreJson.encodeToString(schedule.toStored())
    )

    private fun file(key: String) = cacheDir / "$key.json"

    private fun delete(file: Path) {
        try {
            fileSystem.delete(file)
        } catch (_: Exception) {}
    }

    private fun writeToDisk(key: String, entry: CacheEntry) {
        try {
            val json = ScheduleStoreJson.encodeToString(entry.toStored())
            fileSystem.sink(file(key)).gzip().buffer().use { it.writeUtf8(json) }
        } catch (_: Exception) {}
    }

    /** A missing, unreadable or undecodable entry is a miss, so [deserialize] never sees one it cannot read. */
    private fun readFromDisk(key: String): CacheEntry? {
        return try {
            val json = fileSystem.source(file(key)).gzip().buffer().use { it.readUtf8() }
            ScheduleStoreJson.decodeFromString<StoredCacheEntry>(json).toEntry().also(::deserialize)
        } catch (_: Exception) {
            null
        }
    }
}

/** A cached day in memory; [data] is the JSON of a [StoredDaySchedule]. */
data class CacheEntry(
    val userIsu: Int?,
    val date: LocalDate,
    val timestamp: Long,
    val data: String
)
