package dev.alllexey.itmowidgets.feature.schedule.data.local

import android.content.Context
import com.google.gson.Gson
import dev.alllexey.itmowidgets.core.util.ScheduleUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.alllexey.itmowidgets.core.time.WallClock
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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

    private val memoryCache = ConcurrentHashMap<String, MutableSharedFlow<CacheEntry?>>()

    init {
        cacheDir.mkdirs()
    }

    override fun observeRange(
        userIsu: Int?,
        start: LocalDate,
        end: LocalDate
    ): Flow<List<DaySchedule>> {

        val keys = ScheduleUtil.generateDates(start, end).map { key(userIsu, it) }

        return combineFlows(keys).map { entries ->
            entries.mapNotNull { entry ->
                entry?.takeUnless(::isExpired)?.let(::deserialize)
            }
        }
    }

    override suspend fun save(schedule: DaySchedule, userIsu: Int?) {
        val entry = CacheEntry(
            userIsu = userIsu,
            date = schedule.date,
            timestamp = clock.millis(),
            data = gson.toJson(schedule)
        )

        val key = key(userIsu, schedule.date)

        writeToDisk(key, entry)

        memoryCache.getOrPut(key) { MutableStateFlow(null) }.emit(entry)
    }

    override fun get(userIsu: Int?, date: LocalDate): CacheEntry? {
        val key = key(userIsu, date)
        return readFromDisk(key)?.takeUnless(::isExpired)
    }

    override fun clear() {
        cacheDir.deleteRecursively()
        cacheDir.mkdirs()
        memoryCache.clear()
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
