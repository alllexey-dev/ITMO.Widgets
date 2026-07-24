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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
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

    private val memoryCache = ConcurrentHashMap<String, MutableStateFlow<CacheEntry?>>()

    init {
        cacheDir.mkdirs()
    }

    /**
     * Reading the cache touches the disk, so the whole upstream — including the
     * lazy [combineFlows] set-up and JSON deserialization — runs on [Dispatchers.IO].
     */
    override fun observeRange(
        userIsu: Int?,
        start: LocalDate,
        end: LocalDate
    ): Flow<List<DaySchedule>> {

        val keys = ScheduleUtil.generateDates(start, end).map { key(userIsu, it) }

        return flow {
            emitAll(
                combineFlows(keys).map { entries ->
                    entries.mapNotNull { entry ->
                        entry?.takeUnless(::isExpired)?.let(::deserialize)
                    }
                }
            )
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun save(schedule: DaySchedule, userIsu: Int?) {
        val key = key(userIsu, schedule.date)

        val entry = withContext(Dispatchers.IO) {
            CacheEntry(
                userIsu = userIsu,
                date = schedule.date,
                timestamp = clock.millis(),
                data = gson.toJson(schedule)
            ).also { writeToDisk(key, it) }
        }

        cacheFlow(key).value = entry
    }

    override suspend fun get(userIsu: Int?, date: LocalDate): CacheEntry? {
        val key = key(userIsu, date)
        return withContext(Dispatchers.IO) {
            readFromDisk(key)?.takeUnless(::isExpired)
        }
    }

    /**
     * Keeps the per-date flow instances so that collectors started before the
     * cache was dropped keep observing the same keys instead of being orphaned.
     */
    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            cacheDir.deleteRecursively()
            cacheDir.mkdirs()
        }
        memoryCache.values.forEach { it.value = null }
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

    private fun cacheFlow(key: String): MutableStateFlow<CacheEntry?> =
        memoryCache.getOrPut(key) { MutableStateFlow(null) }

    private fun combineFlows(keys: List<String>): Flow<List<CacheEntry?>> {
        val flows = keys.map { key ->
            memoryCache.getOrPut(key) {
                MutableStateFlow(readFromDisk(key))
            }
        }
        return combine(flows) { it.toList() }
    }

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
