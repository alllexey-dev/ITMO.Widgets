package dev.alllexey.itmowidgets.feature.recordbook.data.sheets

import dev.alllexey.itmowidgets.core.resources.GoogleSheetUrl
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import dev.alllexey.itmowidgets.feature.recordbook.data.RecordbookFileSystem
import dev.alllexey.itmowidgets.feature.recordbook.data.RecordbookStoreJson
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.KeyKind
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetColumnRef
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScore
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetStatus
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import okio.FileSystem
import okio.Path

/** 1: the sheet connections of one account with their last readings. */
const val SHEET_SCORES_FORMAT = 1

/** Every sheet connection on the device; [owner] is the ISU of the account they belong to. */
@Serializable
data class StoredSheetScores(
    val format: Int = SHEET_SCORES_FORMAT,
    val owner: Int? = null,
    val connections: List<StoredSheetConnection> = emptyList(),
)

@Serializable
data class StoredSheetConnection(
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
    val value: String? = null,
    val baseline: String? = null,
    val tracked: Boolean,
    val status: String,
    val updatedAt: Long? = null,
    val connectedAt: Long,
)

/**
 * The sheet connections in `filesDir/sheet_scores`, cleared with the session and kept out of backups. Caller owns
 * IO dispatch.
 */
class SheetScoresFileStore internal constructor(private val directory: Path, private val fileSystem: FileSystem) {
    constructor(directories: AppDirectories) : this(directories.files / "sheet_scores", RecordbookFileSystem)

    private val file = AtomicTextFile(directory / "state.json", fileSystem)

    /** `null` without a file; throws on a corrupt file, another format or a missing or invalid required field. */
    fun read(): StoredSheetScores? {
        val text = file.read() ?: return null
        val state = RecordbookStoreJson.decodeFromString<StoredSheetScores>(text)
        check(state.format == SHEET_SCORES_FORMAT) { "Unknown sheet scores format ${state.format}" }
        state.connections.forEach { it.toModel() }
        return state
    }

    fun write(state: StoredSheetScores) = file.write(RecordbookStoreJson.encodeToString(state))

    fun clear() = fileSystem.deleteRecursively(directory)
}

private fun required(text: String): String = text.also { check(it.isNotBlank()) { "Empty field" } }

internal fun StoredSheetConnection.toModel(): SheetScore {
    val url = required(url)
    checkNotNull(GoogleSheetUrl.parse(url)) { "Not a sheet address" }
    return SheetScore(
        scope = ResourceScope(subjectId, required(subjectName), required(periodKey)),
        url = url,
        tabGid = tabGid,
        tabName = tabName,
        rowKey = required(rowKey),
        keyColumn = keyColumn.also { check(it >= 0) },
        keyKind = KeyKind.valueOf(required(keyKind)),
        column = SheetColumnRef(headerPath, columnIndex.also { check(it >= 0) }),
        value = value,
        baseline = baseline,
        tracked = tracked,
        status = SheetStatus.valueOf(required(status)),
        updatedAt = updatedAt?.let(Instant::fromEpochMilliseconds),
        connectedAt = Instant.fromEpochMilliseconds(connectedAt),
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
    updatedAt = updatedAt?.toEpochMilliseconds(),
    connectedAt = connectedAt.toEpochMilliseconds(),
)
