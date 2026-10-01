package dev.alllexey.itmowidgets.feature.recordbook.data.sheets

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.alllexey.itmowidgets.core.resources.GoogleSheetUrl
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.KeyKind
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetColumnRef
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScore
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetStatus
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import javax.inject.Inject

/** 1: the sheet connections of one account with their last readings. */
internal const val SHEET_SCORES_FORMAT = 1

/** Every sheet connection on the device; [owner] is the ISU of the account they belong to. */
internal data class StoredSheetScores(
    val format: Int = SHEET_SCORES_FORMAT,
    val owner: Int? = null,
    val connections: List<StoredSheetConnection> = emptyList(),
)

internal data class StoredSheetConnection(
    val subjectId: Long,
    val subjectName: String,
    val periodKey: String,
    val url: String,
    val tabGid: Long,
    val tabName: String,
    val rowKey: String,
    val keyColumn: Int,
    val keyKind: String,
    val headerPath: String,
    val columnIndex: Int,
    val value: String?,
    val baseline: String?,
    val tracked: Boolean,
    val status: String,
    val updatedAt: Long?,
    val connectedAt: Long,
)

/**
 * The sheet connections in `filesDir/sheet_scores`, cleared with the session and kept out of backups. Caller owns
 * IO dispatch. Gson bypasses Kotlin constructors, so reading checks every required field.
 */
class SheetScoresFileStore internal constructor(private val directory: File, private val gson: Gson) {
    @Inject constructor(@ApplicationContext context: Context, gson: Gson) :
        this(File(context.filesDir, "sheet_scores"), gson)

    private val file get() = File(directory, "state.json")

    /** `null` without a file; throws on a corrupt file, another format or a missing required field. */
    internal fun read(): StoredSheetScores? {
        if (!file.exists()) return null
        val state = checkNotNull(gson.fromJson(file.readText(), StoredSheetScores::class.java))
        check(state.format == SHEET_SCORES_FORMAT) { "Unknown sheet scores format ${state.format}" }
        checkNotNull(state.connections).forEach { it.toModel() }
        return state
    }

    internal fun write(state: StoredSheetScores) {
        check(directory.isDirectory || directory.mkdirs())
        val temporary = File(directory, "state.json.tmp")
        FileOutputStream(temporary).use { stream ->
            stream.write(gson.toJson(state).toByteArray(Charsets.UTF_8)); stream.fd.sync()
        }
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    internal fun clear() { check(!directory.exists() || directory.deleteRecursively()) }
}

private fun required(text: String?): String = checkNotNull(text).also { check(it.isNotBlank()) { "Empty field" } }

internal fun StoredSheetConnection.toModel(): SheetScore {
    val url = required(url)
    checkNotNull(GoogleSheetUrl.parse(url)) { "Not a sheet address" }
    return SheetScore(
        scope = ResourceScope(subjectId, required(subjectName), required(periodKey)),
        url = url,
        tabGid = tabGid,
        tabName = checkNotNull(tabName),
        rowKey = required(rowKey),
        keyColumn = keyColumn.also { check(it >= 0) },
        keyKind = KeyKind.valueOf(required(keyKind)),
        column = SheetColumnRef(checkNotNull(headerPath), columnIndex.also { check(it >= 0) }),
        value = value,
        baseline = baseline,
        tracked = tracked,
        status = SheetStatus.valueOf(required(status)),
        updatedAt = updatedAt?.let(Instant::ofEpochMilli),
        connectedAt = Instant.ofEpochMilli(connectedAt),
    )
}

internal fun SheetScore.toStored() = StoredSheetConnection(
    subjectId = scope.subjectId,
    subjectName = scope.subjectName,
    periodKey = scope.periodKey,
    url = url,
    tabGid = tabGid,
    tabName = tabName,
    rowKey = rowKey,
    keyColumn = keyColumn,
    keyKind = keyKind.name,
    headerPath = column.headerPath,
    columnIndex = column.index,
    value = value,
    baseline = baseline,
    tracked = tracked,
    status = status.name,
    updatedAt = updatedAt?.toEpochMilli(),
    connectedAt = connectedAt.toEpochMilli(),
)
