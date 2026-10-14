package dev.alllexey.itmowidgets.feature.schedule.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetClose
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetScaffold
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.schedule.presentation.month.MonthDay
import dev.alllexey.itmowidgets.feature.schedule.presentation.month.ScheduleMonthUiState
import dev.alllexey.itmowidgets.feature.schedule.presentation.month.ScheduleMonthViewModel
import dev.alllexey.itmowidgets.shared.compose.strings.common_close
import dev.alllexey.itmowidgets.shared.designsystem.ic_chevron_left
import dev.alllexey.itmowidgets.shared.designsystem.ic_chevron_right
import dev.alllexey.itmowidgets.shared.feature.schedule.Res
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_month_next
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_month_previous
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_month_title
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.compose.strings.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** Test tags of [ScheduleMonthSheetContent]. */
object ScheduleMonthTestTags {
    const val OPEN = "schedule_month:open"
    const val PREVIOUS = "schedule_month:previous"
    const val NEXT = "schedule_month:next"

    fun day(date: LocalDate) = "schedule_month:day:$date"
}

/** What the month sheet asks of its host and its ViewModel. */
data class ScheduleMonthActions(
    val onPrevious: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onSelect: (LocalDate) -> Unit = {},
    val onOpen: (LocalDate) -> Unit = {},
    val onClose: () -> Unit = {},
)

/** The month sheet with its ViewModel; `Открыть` hands the day to [onOpen] (the host closes the sheet). */
@Composable
fun ScheduleMonthRoute(viewModel: ScheduleMonthViewModel, onOpen: (LocalDate) -> Unit, onClose: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ScheduleMonthSheetContent(
        state,
        ScheduleMonthActions(viewModel::previousMonth, viewModel::nextMonth, viewModel::select, onOpen, onClose),
    )
}

/**
 * `Переход к дате` (docs/features/schedule.md): the month with a dot per lesson, today ringed, the chosen day filled,
 * and one `Открыть четверг, 17 сентября`. No day summary and no quick chips. Stateless.
 */
@Composable
fun ScheduleMonthSheetContent(state: ScheduleMonthUiState, actions: ScheduleMonthActions, modifier: Modifier = Modifier) {
    SheetScaffold(
        title = stringResource(Res.string.schedule_month_title),
        modifier = modifier,
        close = SheetClose(stringResource(CoreRes.string.common_close), actions.onClose),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ItmoTheme.spacing.screenMargin)
                .padding(bottom = ItmoTheme.spacing.group),
            verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(state.title, Modifier.weight(1f), style = ItmoTheme.typography.titleMedium)
                // The kit's tonal family (TonalButton): Material's secondaryContainer reads as disabled here.
                val arrows = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = ItmoTheme.colorScheme.primaryContainer,
                    contentColor = ItmoTheme.colorScheme.onPrimaryContainer,
                )
                FilledTonalIconButton(actions.onPrevious, Modifier.testTag(ScheduleMonthTestTags.PREVIOUS), colors = arrows) {
                    Icon(painterResource(KitRes.drawable.ic_chevron_left), stringResource(Res.string.schedule_month_previous))
                }
                FilledTonalIconButton(actions.onNext, Modifier.testTag(ScheduleMonthTestTags.NEXT), colors = arrows) {
                    Icon(painterResource(KitRes.drawable.ic_chevron_right), stringResource(Res.string.schedule_month_next))
                }
            }
            MonthGrid(state, actions.onSelect)
            Button(
                onClick = { actions.onOpen(state.selected) },
                modifier = Modifier.fillMaxWidth().testTag(ScheduleMonthTestTags.OPEN),
            ) {
                Text(state.open.asString())
            }
        }
    }
}

@Composable
private fun MonthGrid(state: ScheduleMonthUiState, onSelect: (LocalDate) -> Unit) {
    val colors = ItmoTheme.colorScheme
    Column {
        Row(Modifier.fillMaxWidth()) {
            state.weekdays.forEach {
                Text(
                    it,
                    Modifier.weight(1f),
                    color = colors.onSurfaceVariant,
                    style = ItmoTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
        val cells: List<MonthDay?> = List(state.leading) { null } + state.days
        cells.chunked(DAYS_PER_WEEK).forEach { week ->
            Row(Modifier.fillMaxWidth().padding(top = CellGap)) {
                week.forEach { day ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (day != null) DayCell(day, onSelect)
                    }
                }
                repeat(DAYS_PER_WEEK - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(day: MonthDay, onSelect: (LocalDate) -> Unit) {
    val colors = ItmoTheme.colorScheme
    Column(
        Modifier
            .clip(ItmoTheme.shapes.medium)
            .selectable(day.selected, role = Role.Button, onClick = { onSelect(day.date) })
            .heightIn(min = ItmoTheme.spacing.touchTarget)
            .padding(vertical = CellGap)
            .testTag(ScheduleMonthTestTags.day(day.date)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(DayCircle)
                .clip(CircleShape)
                .background(if (day.selected) colors.primary else Color.Transparent)
                .then(if (day.today && !day.selected) Modifier.border(1.5.dp, colors.primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.number,
                color = when {
                    day.selected -> colors.onPrimary
                    day.today -> colors.primary
                    day.past -> colors.onSurfaceVariant
                    else -> colors.onSurface
                },
                style = ItmoTheme.typography.bodyLarge,
                fontWeight = if (day.today || day.selected) FontWeight.Bold else null,
                maxLines = 1,
                softWrap = false,
            )
        }
        Row(Modifier.padding(top = CellGap).height(DotSize), horizontalArrangement = Arrangement.spacedBy(CellGap)) {
            repeat(day.lessons) {
                Box(
                    Modifier.size(DotSize).clip(CircleShape)
                        .background(colors.primary.copy(alpha = if (day.past) PAST_DOT_ALPHA else DOT_ALPHA)),
                )
            }
        }
    }
}

private const val DAYS_PER_WEEK = 7
private const val DOT_ALPHA = 0.7f
private const val PAST_DOT_ALPHA = 0.35f
private val DayCircle = 36.dp
private val DotSize = 4.dp
private val CellGap = 2.dp
