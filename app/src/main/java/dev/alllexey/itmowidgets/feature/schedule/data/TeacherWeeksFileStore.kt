package dev.alllexey.itmowidgets.feature.schedule.data

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

/** 1: finished weeks keyed by their Monday, each with its academic lessons that name a teacher. */
private const val FORMAT = 1

/** One academic lesson of a week, newest first within it; [subject] is trimmed and may be empty when My ITMO sent none. */
internal data class WeekLesson(val teacherIsu: Long, val flowId: Long, val subject: String)

internal data class StoredWeeks(val format: Int = FORMAT, val weeks: Map<String, List<WeekLesson>> = emptyMap())

/**
 * Finished weeks of the signed-in account's personal schedule in `filesDir`, cleared with the session. Caller owns IO
 * dispatch and serialization.
 */
class TeacherWeeksFileStore internal constructor(private val directory: File, private val gson: Gson) {
    @Inject constructor(@ApplicationContext context: Context, gson: Gson) : this(File(context.filesDir, "teacher_lessons"), gson)

    private val file get() = File(directory, "weeks.json")

    /** Throws on a corrupt file or one of another format. */
    internal fun read(): Map<LocalDate, List<WeekLesson>> {
        if (!file.exists()) return emptyMap()
        val state = checkNotNull(gson.fromJson(file.readText(), StoredWeeks::class.java))
        check(state.format == FORMAT) { "Unknown teacher weeks format ${state.format}" }
        return checkNotNull(state.weeks).entries.associate { (key, lessons) ->
            val monday = LocalDate.parse(key)
            check(monday.dayOfWeek == DayOfWeek.MONDAY) { "A stored week starts on $monday" }
            checkNotNull(lessons).forEach { lesson ->
                checkNotNull(lesson.subject)
                check(lesson.teacherIsu > 0) { "A stored lesson has no teacher" }
            }
            monday to lessons
        }
    }

    internal fun write(weeks: Map<LocalDate, List<WeekLesson>>) {
        check(directory.isDirectory || directory.mkdirs())
        val state = StoredWeeks(weeks = weeks.mapKeys { (monday, _) -> monday.toString() })
        val temporary = File(directory, "weeks.json.tmp")
        FileOutputStream(temporary).use { stream ->
            stream.write(gson.toJson(state).toByteArray(Charsets.UTF_8)); stream.fd.sync()
        }
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    internal fun clear() { check(!directory.exists() || directory.deleteRecursively()) }
}
