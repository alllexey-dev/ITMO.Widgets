package me.alllexey123.itmowidgets.providers

import android.content.Context
import api.myitmo.model.Lesson
import api.myitmo.model.Schedule
import com.google.gson.Gson
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val SCHEDULE_CACHE_DIR = "schedule_cache"

object ScheduleProvider {

    private const val CACHE_EXPIRATION_MS = 3 * 60 * 60 * 1000L // 3 hours

    private data class CacheEntry(val timestamp: Long, val data: String?)

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
        if (cached != null) return cached

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
            throw RuntimeException("Could not get lessons", e)
        }
    }

    fun writeSchedule(schedule: Schedule, cacheDir: File, gson: Gson) {
        val string = gson.toJson(schedule)
        writeCache(string, schedule.date.toString(), cacheDir, gson)
    }

    fun readSchedule(date: LocalDate, cacheDir: File, gson: Gson): Schedule? {
        val string = readCache(date.toString(), cacheDir, gson)
        if (string == null) return null
        return gson.fromJson(string, Schedule::class.java)
    }

    fun readCache(name: String, cacheDir: File, gson: Gson): String? {
        val file = File(cacheDir, "$name.json")
        try {
            val entry = gson.fromJson(file.readText(), CacheEntry::class.java)
            if (isExpired(entry.timestamp)) return null
            return entry.data
        } catch (e: Exception) {
            return null
        }
    }

    fun writeCache(data: String, name: String, cacheDir: File, gson: Gson) {
        val file = File(cacheDir, "$name.json")
        val entry = CacheEntry(System.currentTimeMillis(), data)
        try {
            file.writeText(gson.toJson(entry))
        } catch (e: Exception) {
        }
    }

    fun clearOldCache(context: Context) {
        val dir = cacheDir(context)
        val removeBefore = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000 // 1 week
        println(removeBefore)
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