package dev.alllexey.itmowidgets.feature.recordbook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetClose
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetScaffold
import dev.alllexey.itmowidgets.designsystem.kit.IconTile
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListGroupNote
import dev.alllexey.itmowidgets.designsystem.kit.StatusPill
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.kit.tabular
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSource
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSourceRow
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSources
import dev.alllexey.itmowidgets.shared.compose.strings.Res
import dev.alllexey.itmowidgets.shared.compose.strings.common_close
import dev.alllexey.itmowidgets.shared.compose.strings.common_got_it
import dev.alllexey.itmowidgets.shared.compose.strings.recordbook_score_pending
import dev.alllexey.itmowidgets.shared.compose.strings.recordbook_sources_open
import dev.alllexey.itmowidgets.shared.compose.strings.recordbook_sources_shown
import dev.alllexey.itmowidgets.shared.compose.strings.recordbook_sources_title
import dev.alllexey.itmowidgets.shared.designsystem.ic_assignment
import dev.alllexey.itmowidgets.shared.designsystem.ic_school
import dev.alllexey.itmowidgets.shared.designsystem.ic_table
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** Test tags of the sources sheet. */
object RecordbookSourcesTags {
    const val SHEET = "recordbook:sources"
    const val OPEN = "recordbook:sources:open"
}

/** `Откуда баллы` over the list, as tall as its content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordbookSourcesSheet(sources: RecordbookSources, actions: RecordbookActions) {
    ModalBottomSheet(
        onDismissRequest = actions.onCloseSources,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
        containerColor = ItmoTheme.colorScheme.surfaceContainerLow,
    ) {
        RecordbookSourcesSheetContent(
            sources = sources,
            onOpenSubject = {
                actions.onCloseSources()
                actions.onOpen(sources.target)
            },
            onClose = actions.onCloseSources,
        )
    }
}

/**
 * Every source of one subject side by side (DECISIONS.md, Зачётка): the shown one first and marked `показываем`,
 * then why it is the one, `Открыть предмет` and `Понятно`.
 */
@Composable
fun RecordbookSourcesSheetContent(
    sources: RecordbookSources,
    onOpenSubject: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SheetScaffold(
        title = stringResource(Res.string.recordbook_sources_title),
        modifier = modifier.testTag(RecordbookSourcesTags.SHEET),
        close = SheetClose(stringResource(Res.string.common_close), onClose),
    ) {
        Column(
            Modifier.padding(horizontal = ItmoTheme.spacing.section),
            verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
        ) {
            Text(
                sources.caption.asString(),
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.bodyLarge,
            )
            ListGroup { sources.rows.forEach { row -> item { SourceRow(row) } } }
            ListGroupNote(sources.note.asString())
            Row(
                Modifier.padding(top = ItmoTheme.spacing.compact),
                horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
            ) {
                OutlinedButton(onClick = onOpenSubject, modifier = Modifier.weight(1f).testTag(RecordbookSourcesTags.OPEN)) {
                    Text(stringResource(Res.string.recordbook_sources_open))
                }
                Button(onClick = onClose, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.common_got_it))
                }
            }
        }
    }
}

@Composable
private fun SourceRow(row: RecordbookSourceRow) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = ItmoTheme.spacing.cardPadding, vertical = ItmoTheme.spacing.content),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
    ) {
        IconTile(
            painterResource(
                when (row.source) {
                    RecordbookSource.BARS -> KitRes.drawable.ic_assignment
                    RecordbookSource.SHEET -> KitRes.drawable.ic_table
                    RecordbookSource.MY_ITMO, RecordbookSource.SPORT -> KitRes.drawable.ic_school
                },
            ),
            if (row.shown) Tone.Primary else Tone.Neutral,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related)) {
            Text(row.title.asString(), style = ItmoTheme.typography.titleMedium)
            Text(row.note.asString(), color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium)
            if (row.shown) StatusPill(stringResource(Res.string.recordbook_sources_shown), tone = Tone.Primary)
        }
        Text(
            row.value ?: stringResource(Res.string.recordbook_score_pending),
            style = ItmoTheme.typography.headlineSmall.tabular(),
            color = if (row.shown) ItmoTheme.colorScheme.onSurface else ItmoTheme.colorScheme.onSurfaceVariant,
        )
    }
}
