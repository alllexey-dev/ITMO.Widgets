package dev.alllexey.itmowidgets.feature.settings.ui.ics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.schedule.IcsFile
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.text.textResource
import dev.alllexey.itmowidgets.designsystem.components.buttons.ButtonRow
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.components.expressive.ItmoLoadingIndicator
import dev.alllexey.itmowidgets.designsystem.components.groups.GroupPosition
import dev.alllexey.itmowidgets.designsystem.components.groups.GroupSurface
import dev.alllexey.itmowidgets.designsystem.components.groups.connectedGroupItem
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetScaffold
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateSize
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.settings.presentation.IcsExportUiState
import dev.alllexey.itmowidgets.feature.settings.presentation.IcsRangeKind
import dev.alllexey.itmowidgets.feature.settings.presentation.IcsRangeOption
import dev.alllexey.itmowidgets.shared.core.common_retry
import dev.alllexey.itmowidgets.shared.core.schedule_lesson_count
import dev.alllexey.itmowidgets.shared.designsystem.ic_chevron_right
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.designsystem.ic_event_note
import dev.alllexey.itmowidgets.shared.feature.settings.Res
import dev.alllexey.itmowidgets.shared.feature.settings.ics_empty
import dev.alllexey.itmowidgets.shared.feature.settings.ics_open
import dev.alllexey.itmowidgets.shared.feature.settings.ics_pick_other
import dev.alllexey.itmowidgets.shared.feature.settings.ics_preparing
import dev.alllexey.itmowidgets.shared.feature.settings.ics_ready_hint
import dev.alllexey.itmowidgets.shared.feature.settings.ics_ready_summary
import dev.alllexey.itmowidgets.shared.feature.settings.ics_ready_title
import dev.alllexey.itmowidgets.shared.feature.settings.ics_send
import dev.alllexey.itmowidgets.shared.feature.settings.ics_sheet_subtitle
import dev.alllexey.itmowidgets.shared.feature.settings.settings_ics_export_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** What the sheet asks of its host and its view model; sending and opening the file are the host's intents. */
@Immutable
class IcsExportActions(
    val onChoose: (IcsRangeKind) -> Unit = {},
    val onSend: (IcsFile) -> Unit = {},
    val onOpen: (IcsFile) -> Unit = {},
    val onChooseAnother: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/** Tags named after the View ids of the old `sheet_ics_export.xml`, for host and instrumented tests. */
object IcsExportTestTags {
    /** The one area every state takes. */
    const val AREA = "content"
    const val RANGES = "ranges"
    const val PREPARING = "preparing"
    const val READY = "ready"
    const val STATE = "state"
    const val SEND = "send"
    const val OPEN = "open"

    /** `range_week`, `range_two_weeks`, `range_semester`, `range_custom`. */
    fun range(kind: IcsRangeKind): String = "range_" + kind.name.lowercase()
}

/**
 * «Выгрузить в .ics»: the four ranges with their days, then the file to send or to open. The choice, the preparing
 * file, the file, an empty range and a failure take one bounded area of at least [ContentMinHeight], as tall as the
 * tallest of them, so the sheet does not jump between them. «Открыть в календаре» shows only when [canOpen] says an
 * app on the device opens the file. Stateless: the state is the view model's, the effects the host's.
 */
@Composable
fun IcsExportSheetContent(
    state: IcsExportUiState,
    actions: IcsExportActions,
    modifier: Modifier = Modifier,
    canOpen: (IcsFile) -> Boolean = { true },
) {
    SheetScaffold(
        title = stringResource(Res.string.settings_ics_export_title),
        modifier = modifier,
        subtitle = stringResource(Res.string.ics_sheet_subtitle),
        contentMinHeight = ContentMinHeight,
    ) {
        val shown = state.layer()
        OneArea(
            shown = shown.ordinal,
            // With the scaffold's own gap, the 16 dp under the subtitle.
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = ItmoTheme.spacing.compact)
                .heightIn(min = ContentMinHeight)
                .testTag(IcsExportTestTags.AREA),
            layers = listOf(
                { Ranges((state as? IcsExportUiState.Choose)?.options ?: BlankOptions, actions) },
                { Preparing(shown == Layer.Preparing) },
                {
                    val ready = state as? IcsExportUiState.Ready
                    Ready(ready, ready == null || canOpen(ready.file), actions)
                },
                { Problem(state, actions) },
            ),
        )
    }
}

/** The four layers of the area, in drawing order; the choice sits at the top, the others in the middle. */
private enum class Layer { Ranges, Preparing, Ready, Problem }

private fun IcsExportUiState.layer(): Layer = when (this) {
    is IcsExportUiState.Choose -> Layer.Ranges
    IcsExportUiState.Preparing -> Layer.Preparing
    is IcsExportUiState.Ready -> Layer.Ready
    IcsExportUiState.Empty, is IcsExportUiState.Failed -> Layer.Problem
}

/**
 * Measures every layer and places only [shown]: the area is as tall as the tallest layer (at a large font scale that
 * is the list of ranges rather than the minimum height), as the View sheet kept its hidden states invisible. The
 * hidden layers are neither drawn nor touchable and say nothing to TalkBack.
 */
@Composable
private fun OneArea(shown: Int, modifier: Modifier, layers: List<@Composable () -> Unit>) {
    val contents = layers.mapIndexed { index, layer ->
        @Composable {
            Box(if (index == shown) Modifier else Modifier.clearAndSetSemantics {}) { layer() }
        }
    }
    Layout(contents = contents, modifier = modifier) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0)
        val placeables = measurables.map { it.single().measure(loose) }
        val height = maxOf(placeables.maxOf { it.height }, constraints.minHeight)
        layout(constraints.maxWidth, height) {
            val placeable = placeables[shown]
            val top = if (shown == Layer.Ranges.ordinal) 0 else (height - placeable.height) / 2
            placeable.placeRelative(0, top)
        }
    }
}

@Composable
private fun Ranges(options: List<IcsRangeOption>, actions: IcsExportActions) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ItmoTheme.spacing.screenMargin)
            .testTag(IcsExportTestTags.RANGES),
    ) {
        options.forEachIndexed { index, option ->
            RangeRow(option, GroupPosition.of(index, options.size)) { actions.onChoose(option.kind) }
        }
    }
}

/** `item_ics_range.xml`: the range and the days it covers; the whole row is the target. */
@Composable
private fun RangeRow(option: IcsRangeOption, position: GroupPosition, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .connectedGroupItem(position, GroupSurface.Sheet)
            .clickable(onClick = onClick)
            .heightIn(min = RangeRowMinHeight)
            .testTag(IcsExportTestTags.range(option.kind))
            .padding(horizontal = ItmoTheme.spacing.cardPadding, vertical = ItmoTheme.spacing.content),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(option.title.asString(), color = ItmoTheme.colorScheme.onSurface, style = ItmoTheme.typography.bodyLarge)
            Text(
                option.dates.asString(),
                Modifier.padding(top = ItmoTheme.spacing.related),
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.bodySmall,
            )
        }
        Icon(
            painterResource(KitRes.drawable.ic_chevron_right),
            contentDescription = null,
            Modifier.padding(start = ItmoTheme.spacing.content).size(ChevronSize),
            tint = ItmoTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The indicator turns only while preparing is shown; hidden, a box of its size keeps the layer's height. */
@Composable
private fun Preparing(shown: Boolean) {
    Column(
        Modifier.fillMaxWidth().testTag(IcsExportTestTags.PREPARING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (shown) ItmoLoadingIndicator() else Box(Modifier.size(IndicatorSize))
        Text(
            stringResource(Res.string.ics_preparing),
            Modifier.padding(top = ItmoTheme.spacing.content),
            color = ItmoTheme.colorScheme.onSurfaceVariant,
            style = ItmoTheme.typography.bodyMedium,
        )
    }
}

/** The written file: how many pairs over which days, send it or open it, and the advice to import it separately. */
@Composable
private fun Ready(ready: IcsExportUiState.Ready?, openable: Boolean, actions: IcsExportActions) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ItmoTheme.spacing.screenMargin)
            .testTag(IcsExportTestTags.READY),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(KitRes.drawable.ic_event_note),
            contentDescription = null,
            Modifier.size(ItmoTheme.spacing.stateInlineIcon),
            tint = ItmoTheme.colorScheme.primary,
        )
        Text(
            stringResource(Res.string.ics_ready_title),
            Modifier.padding(top = ItmoTheme.spacing.content).semantics { heading() },
            color = ItmoTheme.colorScheme.onSurface,
            style = ItmoTheme.typography.titleMedium,
        )
        Text(
            ready?.let { readySummary(it) }.orEmpty(),
            Modifier.padding(top = ItmoTheme.spacing.related),
            color = ItmoTheme.colorScheme.onSurfaceVariant,
            style = ItmoTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        ButtonRow(Modifier.fillMaxWidth().padding(top = ItmoTheme.spacing.section)) {
            ProgressButton(
                stringResource(Res.string.ics_send),
                onClick = { ready?.let { actions.onSend(it.file) } },
                modifier = Modifier.testTag(IcsExportTestTags.SEND),
            )
            if (openable) {
                ProgressButton(
                    stringResource(Res.string.ics_open),
                    onClick = { ready?.let { actions.onOpen(it.file) } },
                    modifier = Modifier.testTag(IcsExportTestTags.OPEN),
                    style = ProgressButtonStyle.Tonal,
                )
            }
        }
        Text(
            stringResource(Res.string.ics_ready_hint),
            Modifier.fillMaxWidth().padding(top = ItmoTheme.spacing.content),
            color = ItmoTheme.colorScheme.onSurfaceVariant,
            style = ItmoTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}

/** «23 пары, 5–11 октября». */
@Composable
private fun readySummary(ready: IcsExportUiState.Ready): String = stringResource(
    Res.string.ics_ready_summary,
    pluralStringResource(CoreRes.plurals.schedule_lesson_count, ready.file.lessons, ready.file.lessons),
    ready.dates.asString(),
)

/** An empty range offers another one; a failure says why and offers to try again. */
@Composable
private fun Problem(state: IcsExportUiState, actions: IcsExportActions) {
    val failed = state as? IcsExportUiState.Failed
    ContentState(
        title = when {
            failed != null -> stringResource(failed.error.textResource())
            state == IcsExportUiState.Empty -> stringResource(Res.string.ics_empty)
            else -> ""
        },
        modifier = Modifier.testTag(IcsExportTestTags.STATE),
        size = ContentStateSize.Compact,
        icon = painterResource(if (failed != null) KitRes.drawable.ic_error else KitRes.drawable.ic_event_note),
        action = if (failed != null) {
            ContentStateAction(stringResource(CoreRes.string.common_retry), actions.onRetry)
        } else {
            ContentStateAction(stringResource(Res.string.ics_pick_other), actions.onChooseAnother)
        },
    )
}

/** The rows a hidden choice is measured with: one line each, as the real ranges take. */
private val BlankOptions = IcsRangeKind.entries.map { IcsRangeOption(it, UiText.Dynamic(""), UiText.Dynamic("")) }

/** `ics_export_content_min_height`: the area every state shares is at least this tall. */
private val ContentMinHeight = 288.dp

/** `item_ics_range.xml`'s `minHeight`, the rows of a connected group. */
private val RangeRowMinHeight = 56.dp

private val ChevronSize = 24.dp

/** The Material indicator's own size, kept by a hidden layer. */
private val IndicatorSize = 48.dp
