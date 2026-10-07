package dev.alllexey.itmowidgets.feature.settings.ui.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import dev.alllexey.itmowidgets.core.diagnostics.DiagnosticEntry
import dev.alllexey.itmowidgets.core.diagnostics.DiagnosticLevel
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBar
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBarBack
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ConfirmDialog
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ConfirmDialogSurface
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateLoading
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.settings.presentation.DiagnosticsUiState
import dev.alllexey.itmowidgets.shared.core.common_back
import dev.alllexey.itmowidgets.shared.core.common_cancel
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import dev.alllexey.itmowidgets.shared.feature.settings.Res
import dev.alllexey.itmowidgets.shared.feature.settings.diagnostics_clear
import dev.alllexey.itmowidgets.shared.feature.settings.diagnostics_clear_confirm_message
import dev.alllexey.itmowidgets.shared.feature.settings.diagnostics_clear_confirm_title
import dev.alllexey.itmowidgets.shared.feature.settings.diagnostics_copy
import dev.alllexey.itmowidgets.shared.feature.settings.diagnostics_description
import dev.alllexey.itmowidgets.shared.feature.settings.diagnostics_empty_title
import dev.alllexey.itmowidgets.shared.feature.settings.diagnostics_level_crash
import dev.alllexey.itmowidgets.shared.feature.settings.diagnostics_level_error
import dev.alllexey.itmowidgets.shared.feature.settings.diagnostics_level_warning
import dev.alllexey.itmowidgets.shared.feature.settings.diagnostics_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** What the journal asks its host: leave, put the journal on the clipboard, clear it once the user confirmed. */
@Immutable
class DiagnosticsActions(
    val onBack: () -> Unit = {},
    val onCopy: () -> Unit = {},
    val onClear: () -> Unit = {},
)

/** Tags named after the View ids of the old `fragment_diagnostics.xml`, for host and instrumented tests. */
object DiagnosticsTestTags {
    const val BACK = "back_button"
    const val COPY = "copy_button"
    const val CLEAR = "clear_button"
    const val LIST = "recycler_view"
    const val LOADING = "loading"
    const val EMPTY = "state_container"
    const val ENTRY = "diagnostic_entry"
    const val STACK_TRACE = "stack_trace"
}

/**
 * «Журнал ошибок»: the entries newest first as the journal hands them, each with its time, level, tag and message; an
 * entry with a stack trace opens it on a tap. Copy and clear act on the whole journal and wait for its first entries;
 * clearing asks first. Loading, the empty journal and the list take the same area under the description. Stateless
 * apart from the confirmation, which survives recreation; [timeLabel] formats an entry's time in the academic zone.
 */
@Composable
fun DiagnosticsScreen(
    state: DiagnosticsUiState,
    actions: DiagnosticsActions,
    timeLabel: (DiagnosticEntry) -> String,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val entries = (state as? DiagnosticsUiState.Content)?.entries
    val hasEntries = !entries.isNullOrEmpty()
    Box(modifier.fillMaxSize().background(ItmoTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(
                title = stringResource(Res.string.diagnostics_title),
                navigation = {
                    AppTopBarBack(
                        stringResource(CoreRes.string.common_back),
                        actions.onBack,
                        Modifier.testTag(DiagnosticsTestTags.BACK),
                    )
                },
            )
            Row(
                Modifier
                    .padding(horizontal = ItmoTheme.spacing.screenMargin)
                    .padding(bottom = ItmoTheme.spacing.related),
                horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
            ) {
                ProgressButton(
                    stringResource(Res.string.diagnostics_copy),
                    onClick = actions.onCopy,
                    modifier = Modifier.testTag(DiagnosticsTestTags.COPY),
                    style = ProgressButtonStyle.Tonal,
                    enabled = hasEntries,
                )
                ProgressButton(
                    stringResource(Res.string.diagnostics_clear),
                    onClick = { confirmClear = true },
                    modifier = Modifier.testTag(DiagnosticsTestTags.CLEAR),
                    style = ProgressButtonStyle.Text,
                    enabled = hasEntries,
                )
            }
            Text(
                stringResource(Res.string.diagnostics_description),
                Modifier
                    .padding(horizontal = ItmoTheme.spacing.screenMargin)
                    .padding(bottom = ItmoTheme.spacing.compact),
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.bodyMedium,
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    entries == null -> ContentStateLoading(Modifier.testTag(DiagnosticsTestTags.LOADING))
                    entries.isEmpty() -> ContentState(
                        title = stringResource(Res.string.diagnostics_empty_title),
                        modifier = Modifier.testTag(DiagnosticsTestTags.EMPTY),
                        icon = painterResource(KitRes.drawable.ic_check),
                    )
                    else -> DiagnosticsList(entries, timeLabel)
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
    if (confirmClear) {
        DiagnosticsClearDialog(
            onConfirm = {
                confirmClear = false
                actions.onClear()
            },
            onDismiss = { confirmClear = false },
        )
    }
}

/** «Очистить журнал?» before the entries go; [windowed] false draws it without its window, for previews. */
@Composable
internal fun DiagnosticsClearDialog(onConfirm: () -> Unit, onDismiss: () -> Unit, windowed: Boolean = true) {
    val title = stringResource(Res.string.diagnostics_clear_confirm_title)
    val confirm = stringResource(Res.string.diagnostics_clear)
    val dismiss = stringResource(CoreRes.string.common_cancel)
    val text = stringResource(Res.string.diagnostics_clear_confirm_message)
    if (windowed) {
        ConfirmDialog(title, confirm, dismiss, onConfirm, onDismiss, text = text, destructive = true)
    } else {
        ConfirmDialogSurface(title, confirm, dismiss, onConfirm, onDismiss, text = text, destructive = true)
    }
}

@Composable
private fun DiagnosticsList(entries: List<DiagnosticEntry>, timeLabel: (DiagnosticEntry) -> String) {
    LazyColumn(
        Modifier.fillMaxSize().testTag(DiagnosticsTestTags.LIST),
        contentPadding = PaddingValues(
            start = ItmoTheme.spacing.screenMargin,
            end = ItmoTheme.spacing.screenMargin,
            bottom = ItmoTheme.spacing.section,
        ),
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
    ) {
        items(entries) { entry -> DiagnosticEntryCard(entry, timeLabel(entry)) }
    }
}

/** `item_diagnostic_entry.xml`: a content card whose trace, when it has one, opens and closes on a tap. */
@Composable
private fun DiagnosticEntryCard(entry: DiagnosticEntry, time: String) {
    val trace = entry.stackTrace?.takeIf { it.isNotBlank() }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shape = ItmoTheme.shapes.cardContent
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ItmoTheme.colorScheme.surfaceContainerLow)
            .then(if (trace != null) Modifier.clickable { expanded = !expanded } else Modifier)
            .testTag(DiagnosticsTestTags.ENTRY)
            .padding(ItmoTheme.spacing.cardPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                time,
                Modifier.weight(1f),
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.labelMedium,
            )
            Text(
                stringResource(entry.level.label()),
                Modifier.padding(start = ItmoTheme.spacing.compact),
                color = if (entry.level == DiagnosticLevel.WARNING) {
                    ItmoTheme.colorScheme.onSurfaceVariant
                } else {
                    ItmoTheme.colorScheme.error
                },
                style = ItmoTheme.typography.labelMedium,
            )
        }
        Text(
            entry.tag,
            Modifier.padding(top = ItmoTheme.spacing.related),
            color = ItmoTheme.colorScheme.onSurface,
            style = ItmoTheme.typography.titleSmall,
        )
        Text(
            entry.message,
            Modifier.padding(top = ItmoTheme.spacing.related),
            color = ItmoTheme.colorScheme.onSurface,
            style = ItmoTheme.typography.bodyMedium,
        )
        if (trace != null && expanded) {
            SelectionContainer(Modifier.padding(top = ItmoTheme.spacing.compact)) {
                Text(
                    trace,
                    Modifier.testTag(DiagnosticsTestTags.STACK_TRACE),
                    color = ItmoTheme.colorScheme.onSurfaceVariant,
                    style = ItmoTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
        }
    }
}

private fun DiagnosticLevel.label() = when (this) {
    DiagnosticLevel.WARNING -> Res.string.diagnostics_level_warning
    DiagnosticLevel.ERROR -> Res.string.diagnostics_level_error
    DiagnosticLevel.CRASH -> Res.string.diagnostics_level_crash
}
