package dev.alllexey.itmowidgets.feature.schedule.data

import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import dev.alllexey.itmowidgets.feature.schedule.data.local.ScheduleStoreJson
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.serialization.Serializable
import okio.FileSystem
import okio.Path

/** 1: finished weeks keyed by their Monday, each with its academic lessons that name a teacher. */
private const val FORMAT = 1

/** One academic lesson of a week, newest first within it; [subject] is trimmed and may be empty when My ITMO sent none. */
@Serializable
internal data class WeekLesson(val teacherIsu: Long, val flowId: Long, val subject: String)

@Serializable
internal data class StoredWeeks(val format: Int = FORMAT, val weeks: Map<String, List<WeekLesson>> = emptyMap())

/**
 * Finished weeks of the signed-in account's personal schedule in `filesDir`, cleared with the session. Caller owns IO
 * dispatch and serialization.
 */
class TeacherWeeksFileStore internal constructor(private val directory: Path) {
    @Inject constructor(directories: AppDirectories) : this(directories.files / "teacher_lessons")

    private val file = AtomicTextFile(directory / "weeks.json")

    /** Throws on a corrupt file or one of another format. */
    internal fun read(): Map<LocalDate, List<WeekLesson>> {
        val text = file.read() ?: return emptyMap()
        val state = ScheduleStoreJson.decodeFromString<StoredWeeks>(text)
        check(state.format == FORMAT) { "Unknown teacher weeks format ${state.format}" }
        return state.weeks.entries.associate { (key, lessons) ->
            val monday = LocalDate.parse(key)
            check(monday.dayOfWeek == DayOfWeek.MONDAY) { "A stored week starts on $monday" }
            lessons.forEach { lesson ->
                check(lesson.teacherIsu > 0) { "A stored lesson has no teacher" }
            }
            monday to lessons
        }
    }

    internal fun write(weeks: Map<LocalDate, List<WeekLesson>>) {
        val state = StoredWeeks(weeks = weeks.mapKeys { (monday, _) -> monday.toString() })
        file.write(ScheduleStoreJson.encodeToString(state))
    }

    internal fun clear() = FileSystem.SYSTEM.deleteRecursively(directory)
}
