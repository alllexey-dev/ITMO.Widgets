package dev.alllexey.itmowidgets.feature.recordbook.domain.sheets

import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkEventKind
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.StudyHalf
import kotlinx.coroutines.flow.Flow

sealed interface SheetInspection {
    /** Every tab of the sheet and where the own row is; lives only in memory. */
    data class Ready(val workbook: SheetWorkbook, val search: RowSearch) : SheetInspection
    data class Failed(val status: SheetStatus) : SheetInspection
}

/**
 * A chosen total: how it is made and chosen, what it reads now ([reading], the new baseline), and for an AI rule the
 * teacher's [official] total and the rule's [summary].
 */
data class SheetTotalPick(
    val formula: SheetFormula,
    val choice: TotalChoice,
    val reading: SheetReading,
    val official: SheetFormula? = null,
    val summary: String? = null,
)

data class SheetChange(val scope: ResourceScope, val kind: MarkEventKind)

/** A background read of the connections of one half-year: what changed and what failed. */
data class SheetCheck(val changes: List<SheetChange>, val errors: List<AppError>)

/** The own totals from public Google Sheets, one connection per subject period; on the device only. */
interface SheetScoresRepository {
    /** Every connection with its last reading; the file is read on the first collection. */
    fun observe(): Flow<List<SheetScore>>
    /** Downloads the connected tab of [scope] and stores what it shows; never news. Nothing without a connection. */
    suspend fun refresh(scope: ResourceScope)
    /** Downloads every tab of [url] and looks for the own row; the start of the connection flow. */
    suspend fun inspect(url: String): SheetInspection
    /**
     * Connects [scope], replacing its previous connection; [total]'s value is the baseline. [linkId] is the subject link
     * the sheet came from when it shares the sheet with a flow or everybody.
     */
    suspend fun connect(scope: ResourceScope, url: String, row: SheetRowMatch, total: SheetTotalPick, linkId: String?): AppResult<Unit>
    /** Another total of the connected sheet; its value is the new baseline; a null [linkId] keeps the stored one. */
    suspend fun changeTotal(scope: ResourceScope, row: SheetRowMatch, total: SheetTotalPick, linkId: String?): AppResult<Unit>
    suspend fun disconnect(scope: ResourceScope)
    /** Reads every connection of [half]; changes count only for tracked connections. */
    suspend fun check(half: StudyHalf): SheetCheck
    /** The next background read of every connection only takes the baseline. */
    suspend fun untrack()
}
