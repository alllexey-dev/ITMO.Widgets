package dev.alllexey.itmowidgets.core.debug

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.IcsFile
import dev.alllexey.itmowidgets.core.schedule.ScheduleExportRange
import dev.alllexey.itmowidgets.core.schedule.ScheduleIcsExport

/** A `.ics` export for debug hosts that never reads My ITMO: every range is empty. */
object NoIcsExport : ScheduleIcsExport {
    override suspend fun export(range: ScheduleExportRange): AppResult<IcsFile?> = AppResult.Success(null)
}
