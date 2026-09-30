package dev.alllexey.itmowidgets.feature.schedule.data.changes

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleSnapshot
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.SnapshotLesson
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** 1: the last academic snapshot of today..today+7 and the changes found against it, newest last. */
private const val FORMAT = 1

/** Everything the change check keeps; the snapshot and the changes are written together. */
internal data class StoredScheduleChanges(
    val format: Int = FORMAT,
    val snapshot: StoredSnapshot? = null,
    val emptyHeld: Boolean = false,
    val changes: List<StoredChange> = emptyList()
)

internal data class StoredSnapshot(val start: String, val end: String, val lessons: List<StoredLesson>)

/** A [SnapshotLesson] with ISO dates and times. */
internal data class StoredLesson(
    val pairId: Long,
    val date: String,
    val start: String,
    val end: String,
    val subjectId: Long,
    val subjectName: String,
    val typeId: Int,
    val flowId: Long,
    val flowName: String?,
    val teacherIsu: Long?,
    val teacherName: String?,
    val room: String?,
    val building: String?,
    val formatId: Int,
    val format: String?
)

internal data class StoredChange(
    val id: String,
    val detectedAt: Long,
    val kind: String,
    val fields: List<String>,
    val subjectName: String,
    val typeId: Int,
    val flowName: String?,
    val before: StoredLesson?,
    val after: StoredLesson?,
    val read: Boolean,
    val notified: Boolean
)

/**
 * The schedule change state in `filesDir`, cleared with the session and kept out of backups. Caller owns IO dispatch
 * and serialization.
 */
class ScheduleChangesFileStore internal constructor(private val directory: File, private val gson: Gson) {
    @Inject constructor(@ApplicationContext context: Context, gson: Gson) : this(File(context.filesDir, "schedule_changes"), gson)

    private val file get() = File(directory, "state.json")

    /** `null` without a file; throws on a corrupt file or one of another format. */
    internal fun read(): StoredScheduleChanges? {
        if (!file.exists()) return null
        val state = checkNotNull(gson.fromJson(file.readText(), StoredScheduleChanges::class.java))
        check(state.format == FORMAT) { "Unknown schedule changes format ${state.format}" }
        state.snapshot?.toModel()
        checkNotNull(state.changes).forEach { it.toModel() }
        return state
    }

    internal fun write(state: StoredScheduleChanges) {
        check(directory.isDirectory || directory.mkdirs())
        val temporary = File(directory, "state.json.tmp")
        FileOutputStream(temporary).use { stream ->
            stream.write(gson.toJson(state).toByteArray(Charsets.UTF_8)); stream.fd.sync()
        }
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    internal fun clear() { check(!directory.exists() || directory.deleteRecursively()) }
}

internal fun StoredSnapshot.toModel() = ScheduleSnapshot(
    start = LocalDate.parse(start),
    end = LocalDate.parse(end),
    lessons = checkNotNull(lessons).map { it.toModel() }
)

internal fun ScheduleSnapshot.toStored() = StoredSnapshot(start.toString(), end.toString(), lessons.map { it.toStored() })

internal fun StoredLesson.toModel() = SnapshotLesson(
    pairId = pairId,
    date = LocalDate.parse(date),
    start = LocalTime.parse(start),
    end = LocalTime.parse(end),
    subjectId = subjectId,
    subjectName = checkNotNull(subjectName),
    typeId = typeId,
    flowId = flowId,
    flowName = flowName,
    teacherIsu = teacherIsu,
    teacherName = teacherName,
    room = room,
    building = building,
    formatId = formatId,
    format = format
)

internal fun SnapshotLesson.toStored() = StoredLesson(
    pairId = pairId,
    date = date.toString(),
    start = start.toString(),
    end = end.toString(),
    subjectId = subjectId,
    subjectName = subjectName,
    typeId = typeId,
    flowId = flowId,
    flowName = flowName,
    teacherIsu = teacherIsu,
    teacherName = teacherName,
    room = room,
    building = building,
    formatId = formatId,
    format = format
)

internal fun StoredChange.toModel() = ScheduleChange(
    id = checkNotNull(id),
    detectedAt = Instant.ofEpochMilli(detectedAt),
    kind = ScheduleChangeKind.valueOf(kind),
    fields = checkNotNull(fields).mapTo(mutableSetOf(), ScheduleChangeField::valueOf),
    subjectName = checkNotNull(subjectName),
    typeId = typeId,
    flowName = flowName,
    before = before?.toModel()?.slot(),
    after = after?.toModel()?.slot(),
    read = read,
    notified = notified
)
