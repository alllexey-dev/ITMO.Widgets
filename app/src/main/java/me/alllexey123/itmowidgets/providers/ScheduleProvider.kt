package me.alllexey123.itmowidgets.providers

import android.content.Context
import api.myitmo.model.Lesson
import api.myitmo.model.Schedule
import com.google.gson.Gson
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

private const val SCHEDULE_CACHE_DIR = "schedule_cache"

object ScheduleProvider {

    private const val CACHE_EXPIRATION_MS = 3 * 60 * 60 * 1000L // 3 hours

    data class CacheEntry(val timestamp: Long, val data: String?)

    fun findCurrentOrNextLesson(lessons: List<Lesson>, timeContext: LocalDateTime): Lesson? {
        return findCurrentLesson(lessons, timeContext) ?: findNextLesson(lessons, timeContext)
    }

    // suppose the lessons are in the correct order
    fun findCurrentLesson(lessons: List<Lesson>, timeContext: LocalDateTime): Lesson? {
        var result: Lesson? = null

        val currTime = DateTimeFormatter.ofPattern("HH:mm").format(timeContext)

        for (lesson in lessons) {
            if (lesson.timeEnd >= currTime && lesson.timeStart <= currTime) {
                result = lesson
                break
            }
        }

        return result
    }

    // suppose the lessons are in the correct order
    fun findNextLesson(lessons: List<Lesson>, timeContext: LocalDateTime): Lesson? {
        var result: Lesson? = null

        val currTime = DateTimeFormatter.ofPattern("HH:mm").format(timeContext)

        for (lesson in lessons) {
            if (lesson.timeEnd > currTime) {
                result = lesson
                break
            }
        }

        return result
    }

    fun getDaySchedule(context: Context, date: LocalDate): Schedule {
        val cacheDir = cacheDir(context)
        val myItmo = MyItmoProvider.getMyItmo(context)

        val cached = readSchedule(date, cacheDir, myItmo.gson)
        if (cached != null && !isExpired(cached.second)) return cached.first

        try {
            val dataResponse = myItmo.api().getPersonalSchedule(date, date).execute().body()

            if (dataResponse == null) {
                throw RuntimeException("Data response is null")
            }

            if (dataResponse.data == null || dataResponse.data.isEmpty()) {
                throw RuntimeException("Schedule data is empty")
            }

            val schedule = dataResponse.data[0]

            clearOldCache(context)
            writeSchedule(schedule, cacheDir, myItmo.gson)

            return schedule

        } catch (e: Exception) {
            if (cached == null) throw RuntimeException("Could not get lessons", e)
            StorageProvider.getStorage(context).setErrorLog("[WARN] [${javaClass.name}] at ${LocalDateTime.now()}: ${e.stackTraceToString()}")
            return cached.first
        }
    }

    fun writeSchedule(schedule: Schedule, cacheDir: File, gson: Gson) {
        val string = gson.toJson(schedule)
        writeCache(string, schedule.date.toString(), cacheDir, gson)
    }

    fun readSchedule(date: LocalDate, cacheDir: File, gson: Gson): Pair<Schedule, Long>? {
        val entry = readCache(date.toString(), cacheDir, gson)
        if (entry == null) return null
        return gson.fromJson(entry.data, Schedule::class.java).to(entry.timestamp)
    }

    fun readCache(name: String, cacheDir: File, gson: Gson): CacheEntry? {
        val file = File(cacheDir, "$name.json")
        try {
            val fis = FileInputStream(file)
            val gzipIs = GZIPInputStream(fis)
            val buffer = gzipIs.readBytes()
            gzipIs.close()
            val entry = gson.fromJson(String(buffer), CacheEntry::class.java)
            return entry
        } catch (e: Exception) {
            return null
        }
    }

    fun writeCache(data: String, name: String, cacheDir: File, gson: Gson) {
        val file = File(cacheDir, "$name.json")
        val entry = CacheEntry(System.currentTimeMillis(), data)
        try {
            val json = gson.toJson(entry)
            val fos = FileOutputStream(file)
            val gzipOs = GZIPOutputStream(fos)
            val buffer = json.toByteArray()
            gzipOs.write(buffer, 0, buffer.size)
            gzipOs.close()
        } catch (e: Exception) {
        }
    }

    fun clearOldCache(context: Context) {
        val dir = cacheDir(context)
        val removeBefore = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000 // 1 week
        for (file in dir.listFiles()) {
            if (file.lastModified() < removeBefore) {
                file.apply { delete() }
            }
        }
    }

    fun clearCache(context: Context) {
        cacheDir(context).apply { deleteRecursively() }
    }

    fun cacheDir(context: Context): File {
        return File(context.cacheDir, SCHEDULE_CACHE_DIR).apply { mkdirs() }
    }

    fun isExpired(timestamp: Long): Boolean {
        return (System.currentTimeMillis() - timestamp) > CACHE_EXPIRATION_MS
    }
}