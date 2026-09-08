package dev.alllexey.itmowidgets.feature.schedule.data.local

import android.content.Context
import com.google.gson.Gson
import dev.alllexey.itmowidgets.core.util.ScheduleUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.alllexey.itmowidgets.core.time.WallClock
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import kotlinx.coroutines.Dispatchers
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

private const val SCHEDULE_CACHE_EXPIRATION_MS = 24 * 60 * 60 * 1000L

class ScheduleLocalDataSourceImpl @Inject constructor(
    private val gson: Gson,
    @param:WallClock private val clock: Clock,
    @ApplicationContext context: Context
) : ScheduleLocalDataSource {

    val cacheDir = File(context.cacheDir, "schedule_cache")

    // A map is published only after a complete mutation. Null remembers a cache miss.
    private val memoryCache = MutableStateFlow<Map<String, CacheEntry?>>(emptyMap())
    private val cacheMutex = Mutex()

    init {
        cacheDir.mkdirs()
    }

    /**
     * Reading the cache touches the disk, so the whole upstream — including the
     * lazy range hydration and JSON deserialization — runs on [Dispatchers.IO].
     */
    override fun observeRange(
        userIsu: Int?,
        start: LocalDate,
        end: LocalDate
    ): Flow<List<DaySchedule>> {

        val keys = ScheduleUtil.generateDates(start, end).map { key(userIsu, it) }

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
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun save(schedule: DaySchedule, userIsu: Int?) {
        withContext(Dispatchers.IO) {
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
        val dates = ScheduleUtil.generateDates(start, end)
        if (dates.isEmpty()) return
        withContext(Dispatchers.IO) {
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
        return withContext(Dispatchers.IO) {
            cacheMutex.withLock { readFromDisk(key)?.takeUnless(::isExpired) }
        }
    }

    /**
     * Existing observers remain attached to the same snapshot flow across a clear.
     */
    override suspend fun clear() {
        withContext(Dispatchers.IO) {
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

    private fun deserialize(entry: CacheEntry): DaySchedule {
        return gson.fromJson(entry.data, DaySchedule::class.java)
    }

    private fun cacheEntry(schedule: DaySchedule, userIsu: Int?, timestamp: Long) = CacheEntry(
        userIsu = userIsu,
        date = schedule.date,
        timestamp = timestamp,
        data = gson.toJson(schedule)
    )

    private fun file(key: String) = File(cacheDir, "$key.json")

    private fun writeToDisk(key: String, entry: CacheEntry) {
        try {
            val json = gson.toJson(entry)
            GZIPOutputStream(file(key).outputStream()).use {
                it.write(json.toByteArray())
            }
        } catch (_: Exception) {}
    }

    private fun readFromDisk(key: String): CacheEntry? {
        return try {
            GZIPInputStream(file(key).inputStream()).use {
                gson.fromJson(String(it.readBytes()), CacheEntry::class.java)
            }
        } catch (_: Exception) {
            null
        }
    }
}

data class CacheEntry(
    val userIsu: Int?,
    val date: LocalDate,
    val timestamp: Long,
    val data: String
)
