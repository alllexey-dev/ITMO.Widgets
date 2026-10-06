package dev.alllexey.itmowidgets.feature.recordbook.data.marks

import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import dev.alllexey.itmowidgets.feature.recordbook.data.RecordbookFileSystem
import dev.alllexey.itmowidgets.feature.recordbook.data.RecordbookStoreJson
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.BarsCheckpointMark
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.BarsMarkSnapshot
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.BarsPlanMarks
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkNews
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MyItmoMarkSnapshot
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MyItmoSubjectMark
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.StudyHalf
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import okio.FileSystem
import okio.Path

/** 1: the last snapshot of each source and the unread subjects of one account. */
private const val FORMAT = 1

/** Everything the mark check keeps; snapshots and unread subjects are written together. [owner] is the ISU. */
@Serializable
data class StoredMarks(
    val format: Int = FORMAT,
    val owner: Int? = null,
    val myItmo: StoredMyItmoSnapshot? = null,
    val bars: StoredBarsSnapshot? = null,
    val news: List<StoredMarkNews> = emptyList()
)

/** [fetchedAt] is the wall-clock start of the request the snapshot came from. */
@Serializable
data class StoredMyItmoSnapshot(val half: String, val fetchedAt: Long, val subjects: List<StoredMyItmoSubject>)

@Serializable
data class StoredMyItmoSubject(
    val programId: Long,
    val semester: Int,
    val entryId: Long,
    val disciplineId: Long,
    val name: String,
    val score: Double? = null,
    val rate: String? = null
)

@Serializable
data class StoredBarsSnapshot(val half: String, val fetchedAt: Long, val plans: List<StoredBarsPlan>)

@Serializable
data class StoredBarsPlan(
    val planId: Long,
    val type: String,
    val identifier: String,
    val name: String,
    val score: Double? = null,
    val rate: String? = null,
    val attempt: Int? = null,
    val absent: Boolean,
    val marks: List<StoredCheckpointMark>
)

@Serializable
data class StoredCheckpointMark(val id: Long, val mark: Double? = null, val absent: Boolean)

@Serializable
data class StoredMarkNews(
    val id: String,
    val half: String,
    val nameKey: String,
    val name: String,
    val detectedAt: Long,
    val notified: Boolean
)

/**
 * The mark tracking state in `filesDir/marks`, cleared with the session and kept out of backups. Caller owns IO
 * dispatch.
 */
class MarksFileStore internal constructor(private val directory: Path, private val fileSystem: FileSystem) {
    constructor(directories: AppDirectories) : this(directories.files / "marks", RecordbookFileSystem)

    private val file = AtomicTextFile(directory / "state.json", fileSystem)

    /** `null` without a file; throws on a corrupt file, another format or a missing or invalid required field. */
    fun read(): StoredMarks? {
        val text = file.read() ?: return null
        val state = RecordbookStoreJson.decodeFromString<StoredMarks>(text)
        check(state.format == FORMAT) { "Unknown marks format ${state.format}" }
        state.myItmo?.toModel()
        state.bars?.toModel()
        state.news.forEach { it.toModel() }
        return state
    }

    fun write(state: StoredMarks) = file.write(RecordbookStoreJson.encodeToString(state))

    fun clear() = fileSystem.deleteRecursively(directory)
}

private fun half(key: String): StudyHalf = checkNotNull(StudyHalf.parse(key)) { "Unknown half-year" }

internal fun StoredMyItmoSnapshot.toModel() = MyItmoMarkSnapshot(
    half = half(half),
    subjects = subjects.map {
        MyItmoSubjectMark(it.programId, it.semester, it.entryId, it.disciplineId, it.name, it.score, it.rate)
    }
)

internal fun MyItmoMarkSnapshot.toStored(fetchedAt: Long) = StoredMyItmoSnapshot(
    half = half.key,
    fetchedAt = fetchedAt,
    subjects = subjects.map { StoredMyItmoSubject(it.programId, it.semester, it.entryId, it.disciplineId, it.name, it.score, it.rate) }
)

internal fun StoredBarsSnapshot.toModel() = BarsMarkSnapshot(
    half = half(half),
    plans = plans.map { plan ->
        BarsPlanMarks(
            planId = plan.planId,
            type = plan.type,
            identifier = plan.identifier,
            name = plan.name,
            score = plan.score,
            rate = plan.rate,
            attempt = plan.attempt,
            absent = plan.absent,
            marks = plan.marks.map { BarsCheckpointMark(it.id, it.mark, it.absent) }
        )
    }
)

internal fun BarsMarkSnapshot.toStored(fetchedAt: Long) = StoredBarsSnapshot(
    half = half.key,
    fetchedAt = fetchedAt,
    plans = plans.map { plan ->
        StoredBarsPlan(
            plan.planId, plan.type, plan.identifier, plan.name, plan.score, plan.rate, plan.attempt, plan.absent,
            plan.marks.map { StoredCheckpointMark(it.id, it.mark, it.absent) }
        )
    }
)

internal fun StoredMarkNews.toModel() = MarkNews(
    id = id,
    half = half(half),
    nameKey = nameKey,
    name = name,
    detectedAt = Instant.fromEpochMilliseconds(detectedAt),
    notified = notified
)

internal fun MarkNews.toStored() = StoredMarkNews(id, half.key, nameKey, name, detectedAt.toEpochMilliseconds(), notified)
