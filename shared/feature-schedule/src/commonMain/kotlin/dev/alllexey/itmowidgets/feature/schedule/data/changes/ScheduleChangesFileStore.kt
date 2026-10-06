package dev.alllexey.itmowidgets.feature.schedule.data.changes

import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import dev.alllexey.itmowidgets.feature.schedule.data.local.ScheduleFileSystem
import dev.alllexey.itmowidgets.feature.schedule.data.local.ScheduleStoreJson
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleSnapshot
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.SnapshotLesson
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import okio.FileSystem
import okio.Path

/** 1: the last academic snapshot of today..today+7 and the changes found against it, newest last. */
private const val FORMAT = 1

/** Everything the change check keeps; the snapshot and the changes are written together. */
@Serializable
data class StoredScheduleChanges(
    val format: Int = FORMAT,
    val snapshot: StoredSnapshot? = null,
    val emptyHeld: Boolean = false,
    val changes: List<StoredChange> = emptyList()
)

@Serializable
data class StoredSnapshot(val start: String, val end: String, val lessons: List<StoredLesson>)

/** A [SnapshotLesson] with ISO dates and times. */
@Serializable
data class StoredLesson(
    val pairId: Long,
    val date: String,
    val start: String,
    val end: String,
    val subjectId: Long,
    val subjectName: String,
    val typeId: Int,
    val flowId: Long,
    val flowName: String? = null,
    val teacherIsu: Long? = null,
    val teacherName: String? = null,
    val room: String? = null,
    val building: String? = null,
    val formatId: Int,
    val format: String? = null
)

@Serializable
data class StoredChange(
    val id: String,
    val detectedAt: Long,
    val kind: String,
    val fields: List<String>,
    val subjectName: String,
    val typeId: Int,
    val flowName: String? = null,
    val before: StoredLesson? = null,
    val after: StoredLesson? = null,
    val read: Boolean,
    val notified: Boolean
)

/**
 * The schedule change state in `filesDir` ([AppDirectories.files]), cleared with the session and kept out of backups.
 * Caller owns IO dispatch and serialization.
 */
class ScheduleChangesFileStore internal constructor(private val directory: Path, private val fileSystem: FileSystem) {
    constructor(directories: AppDirectories) : this(directories.files / "schedule_changes", ScheduleFileSystem)

    private val file = AtomicTextFile(directory / "state.json", fileSystem)

    /** `null` without a file; throws on a corrupt file or one of another format. */
    fun read(): StoredScheduleChanges? {
        val text = file.read() ?: return null
        val state = ScheduleStoreJson.decodeFromString<StoredScheduleChanges>(text)
        check(state.format == FORMAT) { "Unknown schedule changes format ${state.format}" }
        state.snapshot?.toModel()
        state.changes.forEach { it.toModel() }
        return state
    }

    fun write(state: StoredScheduleChanges) = file.write(ScheduleStoreJson.encodeToString(state))

    fun clear() = fileSystem.deleteRecursively(directory)
}

fun StoredSnapshot.toModel() = ScheduleSnapshot(
    start = LocalDate.parse(start),
    end = LocalDate.parse(end),
    lessons = lessons.map { it.toModel() }
)

fun ScheduleSnapshot.toStored() = StoredSnapshot(start.toString(), end.toString(), lessons.map { it.toStored() })

fun StoredLesson.toModel() = SnapshotLesson(
    pairId = pairId,
    date = LocalDate.parse(date),
    start = LocalTime.parse(start),
    end = LocalTime.parse(end),
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

fun SnapshotLesson.toStored() = StoredLesson(
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

fun StoredChange.toModel() = ScheduleChange(
    id = id,
    detectedAt = Instant.fromEpochMilliseconds(detectedAt),
    kind = ScheduleChangeKind.valueOf(kind),
    fields = fields.mapTo(mutableSetOf(), ScheduleChangeField::valueOf),
    subjectName = subjectName,
    typeId = typeId,
    flowName = flowName,
    before = before?.toModel()?.slot(),
    after = after?.toModel()?.slot(),
    read = read,
    notified = notified
)
