package dev.alllexey.itmowidgets.feature.schedule.data.local

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.schedule.ScheduleUtil
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.time.WallClock
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
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate

private const val SCHEDULE_CACHE_EXPIRATION_MS = 24 * 60 * 60 * 1000L

private fun datesBetween(start: LocalDate, end: LocalDate): List<LocalDate> =
    ScheduleUtil.generateDates(start.toKotlinLocalDate(), end.toKotlinLocalDate()).map { it.toJavaLocalDate() }

class ScheduleLocalDataSourceImpl @Inject constructor(
    @param:WallClock private val clock: Clock,
    directories: AppDirectories,
    private val dispatchers: AppDispatchers
) : ScheduleLocalDataSource {

    val cacheDir: File = (directories.cache / "schedule_cache").toFile()

    // A map is published only after a complete mutation. Null remembers a cache miss.
    private val memoryCache = MutableStateFlow<Map<String, CacheEntry?>>(emptyMap())
    private val cacheMutex = Mutex()

    init {
        cacheDir.mkdirs()
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
                val entry = cacheEntry(schedule, userIsu, clock.millis())
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
                val timestamp = clock.millis()
                val replacement = schedules
                    .filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
                    .associate { key(userIsu, it.date) to cacheEntry(it, userIsu, timestamp) }
                val updated = memoryCache.value.toMutableMap()
                dates.forEach { date ->
                    val key = key(userIsu, date)
                    val entry = replacement[key]
                    if (entry == null) file(key).delete() else writeToDisk(key, entry)
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
                cacheDir.listFiles()?.filter { it.name.startsWith(prefix) }?.forEach { it.delete() }
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
                cacheDir.deleteRecursively()
                cacheDir.mkdirs()
                memoryCache.value = emptyMap()
            }
        }
    }

    fun isExpired(entry: CacheEntry): Boolean {
        return clock.millis() - entry.timestamp > SCHEDULE_CACHE_EXPIRATION_MS
    }

    // helpers
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

    private fun file(key: String) = File(cacheDir, "$key.json")

    private fun writeToDisk(key: String, entry: CacheEntry) {
        try {
            val json = ScheduleStoreJson.encodeToString(entry.toStored())
            GZIPOutputStream(file(key).outputStream()).use {
                it.write(json.toByteArray())
            }
        } catch (_: Exception) {}
    }

    /** A missing, unreadable or undecodable entry is a miss, so [deserialize] never sees one it cannot read. */
    private fun readFromDisk(key: String): CacheEntry? {
        return try {
            val json = GZIPInputStream(file(key).inputStream()).use { String(it.readBytes()) }
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
