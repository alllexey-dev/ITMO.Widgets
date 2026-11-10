package dev.alllexey.itmowidgets.feature.recordbook.data.sheets

import dev.alllexey.itmowidgets.core.resources.GoogleSheetUrl
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import dev.alllexey.itmowidgets.feature.recordbook.data.RecordbookFileSystem
import dev.alllexey.itmowidgets.feature.recordbook.data.RecordbookStoreJson
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.BlankPolicy
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.KeyKind
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetCellRef
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetColumnRef
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetFormula
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetNode
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetOp
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScore
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetStatus
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.TotalChoice
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import okio.FileSystem
import okio.Path

/**
 * 1 (2.2): one total column per connection (`headerPath`, `columnIndex`). 2: a [StoredSheetFormula] per connection,
 * an AI rule's official total, summary and the link id. Format 1 is read and migrated; 2.2 deletes a format 2 file.
 */
const val SHEET_SCORES_FORMAT = 2
private const val SHEET_SCORES_FORMAT_1 = 1

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
    /** Which of the rows holding a repeated name is the own one, from the top; absent for the topmost. */
    val rowOccurrence: Int? = null,
    /** Format 1's total column; format 2 has [formula] instead. */
    val headerPath: String? = null,
    val columnIndex: Int? = null,
    /** How the total was chosen; absent for [TotalChoice.AUTO], as every total of 2.2 was. */
    val choice: String? = null,
    val formula: StoredSheetFormula? = null,
    val official: StoredSheetFormula? = null,
    val officialValue: String? = null,
    val summary: String? = null,
    val linkId: String? = null,
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

    /**
     * `null` without a file; format 1 comes back migrated to [SHEET_SCORES_FORMAT]. Throws on a corrupt file, another
     * format or a missing or invalid required field.
     */
    fun read(): StoredSheetScores? {
        val text = file.read() ?: return null
        val stored = RecordbookStoreJson.decodeFromString<StoredSheetScores>(text)
        val state = when (stored.format) {
            SHEET_SCORES_FORMAT -> stored
            SHEET_SCORES_FORMAT_1 -> stored.copy(format = SHEET_SCORES_FORMAT, connections = stored.connections.map { it.fromFormat1() })
            else -> error("Unknown sheet scores format ${stored.format}")
        }
        state.connections.forEach { it.toModel() }
        return state
    }

    fun write(state: StoredSheetScores) = file.write(RecordbookStoreJson.encodeToString(state))

    fun clear() = fileSystem.deleteRecursively(directory)
}

private fun required(text: String): String = text.also { check(it.isNotBlank()) { "Empty field" } }

/** A total of format 2: `direct` (one `cell` node, shown as the sheet shows it) or `computed` with its `blanks`. */
@Serializable
data class StoredSheetFormula(val kind: String, val node: StoredSheetNode, val blanks: String? = null)

/**
 * A node: `cell` ([gid], [headerPath], [column]), `number` ([value]), `sum`, `multiply`, `min`, `max`, `subtract`,
 * `divide` ([args]) or `round` (one of [args], [digits]).
 */
@Serializable
data class StoredSheetNode(
    val op: String,
    val gid: Long? = null,
    val headerPath: String? = null,
    val column: Int? = null,
    val value: Double? = null,
    val args: List<StoredSheetNode>? = null,
    val digits: Int? = null,
)

/** Format 1's column of [StoredSheetConnection.tabGid] as a direct formula; the fields of format 1 are dropped. */
private fun StoredSheetConnection.fromFormat1(): StoredSheetConnection {
    check(formula == null) { "Format 1 has no formula" }
    val ref = SheetCellRef(tabGid, SheetColumnRef(checkNotNull(headerPath), checkNotNull(columnIndex)))
    return copy(headerPath = null, columnIndex = null, formula = SheetFormula.Direct(ref).toStored())
}

private const val CELL = "cell"
private const val NUMBER = "number"
private const val ROUND = "round"
private const val DIRECT = "direct"
private const val COMPUTED = "computed"

internal fun SheetFormula.toStored(): StoredSheetFormula = when (this) {
    is SheetFormula.Direct -> StoredSheetFormula(DIRECT, SheetNode.Cell(cell).toStored())
    is SheetFormula.Computed -> StoredSheetFormula(COMPUTED, node.toStored(), blanks.name)
}

private fun SheetNode.toStored(): StoredSheetNode = when (this) {
    is SheetNode.Cell -> StoredSheetNode(CELL, gid = ref.gid, headerPath = ref.column.headerPath, column = ref.column.index)
    is SheetNode.Number -> StoredSheetNode(NUMBER, value = value)
    is SheetNode.Apply -> StoredSheetNode(op.name.lowercase(), args = args.map { it.toStored() })
    is SheetNode.Round -> StoredSheetNode(ROUND, args = listOf(arg.toStored()), digits = digits)
}

internal fun StoredSheetFormula.toModel(): SheetFormula = when (kind) {
    DIRECT -> SheetFormula.Direct((node.toModel() as? SheetNode.Cell)?.ref ?: error("A direct total is one cell"))
    COMPUTED -> SheetFormula.Computed(node.toModel(), BlankPolicy.valueOf(checkNotNull(blanks)))
    else -> error("Unknown total kind")
}

private fun StoredSheetNode.toModel(): SheetNode = when (op) {
    CELL -> SheetNode.Cell(
        SheetCellRef(checkNotNull(gid), SheetColumnRef(checkNotNull(headerPath), checkNotNull(column).also { check(it >= 0) })),
    )
    NUMBER -> SheetNode.Number(checkNotNull(value).also { check(it.isFinite()) })
    ROUND -> SheetNode.Round(checkNotNull(args).single().toModel(), checkNotNull(digits).also { check(it in 0..6) })
    else -> SheetNode.Apply(
        SheetOp.entries.firstOrNull { it.name.lowercase() == op } ?: error("Unknown node"),
        checkNotNull(args).also { check(it.isNotEmpty()) }.map { it.toModel() },
    )
}

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
        rowOccurrence = (rowOccurrence ?: 0).also { check(it >= 0) },
        formula = checkNotNull(formula) { "No total" }.toModel(),
        choice = choice?.let { TotalChoice.valueOf(it) } ?: TotalChoice.AUTO,
        official = official?.toModel(),
        officialValue = officialValue,
        summary = summary,
        linkId = linkId,
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
    rowOccurrence = rowOccurrence.takeIf { it != 0 },
    choice = choice.takeIf { it != TotalChoice.AUTO }?.name,
    formula = formula.toStored(),
    official = official?.toStored(),
    officialValue = officialValue,
    summary = summary,
    linkId = linkId,
    value = value,
    baseline = baseline,
    tracked = tracked,
    status = status.name,
    updatedAt = updatedAt?.toEpochMilliseconds(),
    connectedAt = connectedAt.toEpochMilliseconds(),
)
