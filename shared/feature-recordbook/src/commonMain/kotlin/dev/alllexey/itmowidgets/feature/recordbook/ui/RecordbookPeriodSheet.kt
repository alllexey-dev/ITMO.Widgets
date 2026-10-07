package dev.alllexey.itmowidgets.feature.recordbook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetClose
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetScaffold
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.shared.core.common_close
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import dev.alllexey.itmowidgets.shared.feature.recordbook.Res
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_current_period
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_period_program
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_period_title
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_period_value
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** One study period in the picker; [programName] is said only when the student has several programs. */
@Immutable
data class RecordbookPeriodOption(
    val programId: Long,
    val semester: Int,
    val course: Int,
    val studyYear: String,
    val actual: Boolean,
    val programName: String,
)

/** Every period of [programs], each program's latest semester first. */
fun recordbookPeriodOptions(programs: List<RecordbookProgram>): List<RecordbookPeriodOption> =
    programs.flatMap { program ->
        program.periods.sortedByDescending { it.semester }.map { period ->
            RecordbookPeriodOption(program.id, period.semester, period.course, period.studyYear, period.actual, program.name)
        }
    }

/** Test tags of [RecordbookPeriodSheetContent]. */
object RecordbookPeriodSheetTestTags {
    const val CHECK = "recordbook_period_check"

    fun option(programId: Long, semester: Int): String = "recordbook_period_${programId}_$semester"
}

/**
 * The period picker: `Учебный период` with the program's name when there is one
 * program, then a card per period with its course and semester over the study year, `текущий` for the actual one and
 * the program's name when there are several. The selected period carries a check on `secondaryContainer`. A tap
 * answers through [onSelect]; the host closes the sheet.
 */
@Composable
fun RecordbookPeriodSheetContent(
    options: List<RecordbookPeriodOption>,
    programName: String,
    selectedProgram: Long,
    selectedSemester: Int,
    onSelect: (RecordbookPeriodOption) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val severalPrograms = options.map { it.programId }.distinct().size > 1
    SheetScaffold(
        title = stringResource(Res.string.recordbook_period_title),
        modifier = modifier,
        subtitle = programName.takeIf { !severalPrograms && it.isNotBlank() },
        close = SheetClose(stringResource(CoreRes.string.common_close), onClose),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ItmoTheme.spacing.screenMargin)
                .padding(top = ItmoTheme.spacing.related),
        ) {
            options.forEach { option ->
                val selected = option.programId == selectedProgram && option.semester == selectedSemester
                PeriodRow(option, selected, severalPrograms) { onSelect(option) }
            }
        }
    }
}

/** A period: a 20 dp card, at least 72 dp tall, its texts and the check of the selection. */
@Composable
private fun PeriodRow(option: RecordbookPeriodOption, selected: Boolean, severalPrograms: Boolean, onClick: () -> Unit) {
    val title = stringResource(Res.string.recordbook_period_value, option.course, option.semester)
    val year = if (option.actual) stringResource(Res.string.recordbook_current_period, option.studyYear) else option.studyYear
    val subtitle = if (severalPrograms) stringResource(Res.string.recordbook_period_program, year, option.programName) else year
    val shape = ItmoTheme.shapes.cardContent
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = ItmoTheme.spacing.related)
            .clip(shape)
            .background(if (selected) ItmoTheme.colorScheme.secondaryContainer else ItmoTheme.colorScheme.surfaceContainerLow)
            .border(ItmoTheme.shapes.cardStroke, ItmoTheme.colorScheme.outlineVariant, shape)
            .testTag(RecordbookPeriodSheetTestTags.option(option.programId, option.semester))
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = listOf(title, subtitle).joinToString(DESCRIPTION_SEPARATOR)
                this.selected = selected
            }
            .heightIn(min = RowMinHeight)
            .padding(horizontal = ItmoTheme.spacing.cardPadding, vertical = ItmoTheme.spacing.content),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = ItmoTheme.colorScheme.onSurface,
                style = ItmoTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(subtitle, color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodySmall)
        }
        if (selected) {
            Icon(
                painterResource(KitRes.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.size(CheckSize).testTag(RecordbookPeriodSheetTestTags.CHECK),
                tint = ItmoTheme.colorScheme.primary,
            )
        }
    }
}

private const val DESCRIPTION_SEPARATOR = ". "
private val RowMinHeight = 72.dp
private val CheckSize = 24.dp
