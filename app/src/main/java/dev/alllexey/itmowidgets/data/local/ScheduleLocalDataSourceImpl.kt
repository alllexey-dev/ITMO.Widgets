package dev.alllexey.itmowidgets.data.local

import api.myitmo.model.schedule.Schedule
import com.google.gson.Gson
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDate
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

private const val CACHE_EXPIRATION_MS = 3 * 24 * 60 * 60 * 1000L // 3 days

class ScheduleLocalDataSourceImpl(
    private val gson: Gson,
    private val cacheDir: File
) : ScheduleLocalDataSource {

    private data class CacheEntry(val timestamp: Long, val data: String?)

    override fun getSchedule(date: LocalDate): Pair<Schedule, Long>? {
        val entry = readCache(date.toString()) ?: return null
        val schedule = gson.fromJson(entry.data, Schedule::class.java)
        return schedule to entry.timestamp
    }

    override fun saveSchedule(schedule: Schedule) {
        val stringData = gson.toJson(schedule)
        writeCache(stringData, schedule.date.toString())
        clearOldCache()
    }

    override fun isExpired(timestamp: Long): Boolean {
        return (System.currentTimeMillis() - timestamp) > CACHE_EXPIRATION_MS
    }

    override fun clearCache() {
        cacheDir.deleteRecursively()
        cacheDir.mkdirs()
    }

    override fun getSchedulesForRange(startDate: LocalDate, endDate: LocalDate): List<Schedule> {
        val cachedSchedules = mutableListOf<Schedule>()
        var currentDate = startDate
        while (!currentDate.isAfter(endDate)) {
            getSchedule(currentDate)?.let { (schedule, _) ->
                cachedSchedules.add(schedule)
            }
            currentDate = currentDate.plusDays(1)
        }
        return cachedSchedules
    }

    private fun readCache(name: String): CacheEntry? {
        val file = File(cacheDir, "$name.json")
        try {
            GZIPInputStream(FileInputStream(file)).use { gzipIs ->
                val buffer = gzipIs.readBytes()
                return gson.fromJson(String(buffer), CacheEntry::class.java)
            }
        } catch (_: Exception) {
            return null
        }
    }

    private fun writeCache(data: String, name: String) {
        val file = File(cacheDir, "$name.json")
        val entry = CacheEntry(System.currentTimeMillis(), data)
        try {
            val json = gson.toJson(entry)
            GZIPOutputStream(FileOutputStream(file)).use { gzipOs ->
                val buffer = json.toByteArray()
                gzipOs.write(buffer)
            }
        } catch (_: Exception) {
        }
    }

    private fun clearOldCache() {
        val removeBefore = LocalDate.now().minusDays(30) // 1 month
        cacheDir.listFiles()?.forEach { file ->
            try {
                if (LocalDate.parse(file.name.substringBefore('.')) < removeBefore) {
                    file.delete()
                }
            } catch (_: Exception) {
                file.delete()
            }
        }
    }


}