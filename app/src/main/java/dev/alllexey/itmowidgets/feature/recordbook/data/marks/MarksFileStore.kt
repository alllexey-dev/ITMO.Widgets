package dev.alllexey.itmowidgets.feature.recordbook.data.marks

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.BarsCheckpointMark
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.BarsMarkSnapshot
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.BarsPlanMarks
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkNews
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MyItmoMarkSnapshot
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MyItmoSubjectMark
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.StudyHalf
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import javax.inject.Inject

/** 1: the last snapshot of each source and the unread subjects of one account. */
private const val FORMAT = 1

/** Everything the mark check keeps; snapshots and unread subjects are written together. [owner] is the ISU. */
internal data class StoredMarks(
    val format: Int = FORMAT,
    val owner: Int? = null,
    val myItmo: StoredMyItmoSnapshot? = null,
    val bars: StoredBarsSnapshot? = null,
    val news: List<StoredMarkNews> = emptyList()
)

/** [fetchedAt] is the wall-clock start of the request the snapshot came from. */
internal data class StoredMyItmoSnapshot(val half: String, val fetchedAt: Long, val subjects: List<StoredMyItmoSubject>)

internal data class StoredMyItmoSubject(
    val programId: Long,
    val semester: Int,
    val entryId: Long,
    val disciplineId: Long,
    val name: String,
    val score: Double?,
    val rate: String?
)

internal data class StoredBarsSnapshot(val half: String, val fetchedAt: Long, val plans: List<StoredBarsPlan>)

internal data class StoredBarsPlan(
    val planId: Long,
    val type: String,
    val identifier: String,
    val name: String,
    val score: Double?,
    val rate: String?,
    val attempt: Int?,
    val absent: Boolean,
    val marks: List<StoredCheckpointMark>
)

internal data class StoredCheckpointMark(val id: Long, val mark: Double?, val absent: Boolean)

internal data class StoredMarkNews(
    val id: String,
    val half: String,
    val nameKey: String,
    val name: String,
    val detectedAt: Long,
    val notified: Boolean
)

/**
 * The mark tracking state in `filesDir/marks`, cleared with the session and kept out of backups. Caller owns IO
 * dispatch. Gson bypasses Kotlin constructors, so reading checks every required field.
 */
class MarksFileStore internal constructor(private val directory: File, private val gson: Gson) {
    @Inject constructor(@ApplicationContext context: Context, gson: Gson) : this(File(context.filesDir, "marks"), gson)

    private val file get() = File(directory, "state.json")

    /** `null` without a file; throws on a corrupt file, another format or a missing required field. */
    internal fun read(): StoredMarks? {
        if (!file.exists()) return null
        val state = checkNotNull(gson.fromJson(file.readText(), StoredMarks::class.java))
        check(state.format == FORMAT) { "Unknown marks format ${state.format}" }
        state.myItmo?.toModel()
        state.bars?.toModel()
        checkNotNull(state.news).forEach { it.toModel() }
        return state
    }

    internal fun write(state: StoredMarks) {
        check(directory.isDirectory || directory.mkdirs())
        val temporary = File(directory, "state.json.tmp")
        FileOutputStream(temporary).use { stream ->
            stream.write(gson.toJson(state).toByteArray(Charsets.UTF_8)); stream.fd.sync()
        }
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    internal fun clear() { check(!directory.exists() || directory.deleteRecursively()) }
}

private fun half(key: String?): StudyHalf = checkNotNull(StudyHalf.parse(checkNotNull(key))) { "Unknown half-year" }

internal fun StoredMyItmoSnapshot.toModel() = MyItmoMarkSnapshot(
    half = half(half),
    subjects = checkNotNull(subjects).map {
        MyItmoSubjectMark(it.programId, it.semester, it.entryId, it.disciplineId, checkNotNull(it.name), it.score, it.rate)
    }
)

internal fun MyItmoMarkSnapshot.toStored(fetchedAt: Long) = StoredMyItmoSnapshot(
    half = half.key,
    fetchedAt = fetchedAt,
    subjects = subjects.map { StoredMyItmoSubject(it.programId, it.semester, it.entryId, it.disciplineId, it.name, it.score, it.rate) }
)

internal fun StoredBarsSnapshot.toModel() = BarsMarkSnapshot(
    half = half(half),
    plans = checkNotNull(plans).map { plan ->
        BarsPlanMarks(
            planId = plan.planId,
            type = checkNotNull(plan.type),
            identifier = checkNotNull(plan.identifier),
            name = checkNotNull(plan.name),
            score = plan.score,
            rate = plan.rate,
            attempt = plan.attempt,
            absent = plan.absent,
            marks = checkNotNull(plan.marks).map { BarsCheckpointMark(it.id, it.mark, it.absent) }
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
    id = checkNotNull(id),
    half = half(half),
    nameKey = checkNotNull(nameKey),
    name = checkNotNull(name),
    detectedAt = Instant.ofEpochMilli(detectedAt),
    notified = notified
)

internal fun MarkNews.toStored() = StoredMarkNews(id, half.key, nameKey, name, detectedAt.toEpochMilli(), notified)
