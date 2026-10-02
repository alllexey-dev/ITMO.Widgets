package dev.alllexey.itmowidgets.core.debug

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.IcsFile
import dev.alllexey.itmowidgets.core.schedule.ScheduleExportRange
import dev.alllexey.itmowidgets.core.schedule.ScheduleIcsExport
import kotlinx.coroutines.CompletableDeferred

/** A `.ics` export for debug hosts that never reads My ITMO: every range answers [result], after [gate] when set. */
class MemoryIcsExport : ScheduleIcsExport {
    @Volatile var result: AppResult<IcsFile?> = AppResult.Success(null)
    @Volatile var gate: CompletableDeferred<Unit>? = null
    val ranges = mutableListOf<ScheduleExportRange>()

    override suspend fun export(range: ScheduleExportRange): AppResult<IcsFile?> {
        ranges += range
        gate?.await()
        return result
    }
}
