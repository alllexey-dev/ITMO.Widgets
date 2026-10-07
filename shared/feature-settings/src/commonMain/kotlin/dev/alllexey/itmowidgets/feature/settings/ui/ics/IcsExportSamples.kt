package dev.alllexey.itmowidgets.feature.settings.ui.ics

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.schedule.IcsFile
import dev.alllexey.itmowidgets.core.schedule.ScheduleExportRange
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.presentation.IcsDateLabels
import dev.alllexey.itmowidgets.feature.settings.presentation.IcsExportUiState
import dev.alllexey.itmowidgets.feature.settings.presentation.IcsRangeKind
import dev.alllexey.itmowidgets.feature.settings.presentation.IcsRangeOption
import dev.alllexey.itmowidgets.shared.feature.settings.Res
import dev.alllexey.itmowidgets.shared.feature.settings.ics_range_custom
import dev.alllexey.itmowidgets.shared.feature.settings.ics_range_custom_caption
import dev.alllexey.itmowidgets.shared.feature.settings.ics_range_semester
import dev.alllexey.itmowidgets.shared.feature.settings.ics_range_two_weeks
import dev.alllexey.itmowidgets.shared.feature.settings.ics_range_until
import dev.alllexey.itmowidgets.shared.feature.settings.ics_range_week
import kotlinx.datetime.LocalDate

/**
 * The sheet's five states on Friday 2 October 2026, as `IcsExportViewModel` builds them; the clock of LT-3a's XML
 * references, so the goldens show the same days.
 */
internal object IcsExportSamples {

    private val today = LocalDate(2026, 10, 2)

    val choose = IcsExportUiState.Choose(
        listOf(
            IcsRangeOption(IcsRangeKind.WEEK, UiText.Res(Res.string.ics_range_week), dates(ScheduleExportRange.Week)),
            IcsRangeOption(
                IcsRangeKind.TWO_WEEKS,
                UiText.Res(Res.string.ics_range_two_weeks),
                dates(ScheduleExportRange.TwoWeeks),
            ),
            IcsRangeOption(
                IcsRangeKind.SEMESTER,
                UiText.Res(Res.string.ics_range_semester),
                UiText.Res(
                    Res.string.ics_range_until,
                    listOf(IcsDateLabels.day(ScheduleExportRange.Semester.dates(today).endInclusive)),
                ),
            ),
            IcsRangeOption(
                IcsRangeKind.CUSTOM,
                UiText.Res(Res.string.ics_range_custom),
                UiText.Res(Res.string.ics_range_custom_caption),
            ),
        ),
    )

    val file = IcsFile("content://dev.alllexey.itmowidgets.files/ics/schedule.ics", "schedule.ics", 23)

    val ready = IcsExportUiState.Ready(file, dates(ScheduleExportRange.Week))

    val failed = IcsExportUiState.Failed(AppError.Network)

    private fun dates(range: ScheduleExportRange): UiText = IcsDateLabels.range(range.dates(today))
}
