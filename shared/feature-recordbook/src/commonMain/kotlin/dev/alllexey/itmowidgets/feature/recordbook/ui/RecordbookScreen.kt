package dev.alllexey.itmowidgets.feature.recordbook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.textResource
import dev.alllexey.itmowidgets.designsystem.components.state.AppRefreshBox
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.Skeleton
import dev.alllexey.itmowidgets.designsystem.components.state.SkeletonStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookRate
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubjectStatus
import dev.alllexey.itmowidgets.feature.recordbook.domain.subjectNameKey
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookAttentionReason
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookProgress
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSelection
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookUiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookViewModel
import dev.alllexey.itmowidgets.feature.recordbook.presentation.displayedScore
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheetFallback
import dev.alllexey.itmowidgets.shared.core.common_load_error_title
import dev.alllexey.itmowidgets.shared.core.common_retry
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.designsystem.ic_expand_more
import dev.alllexey.itmowidgets.shared.designsystem.ic_menu_book
import dev.alllexey.itmowidgets.shared.designsystem.ic_table
import dev.alllexey.itmowidgets.shared.feature.recordbook.Res
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_attention_section
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_bars_error
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_bars_login
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_bars_login_required
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_bars_missing
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_disciplines_section
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_empty_description
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_empty_title
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_period_title
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_period_value
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_points_out_of
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_refresh_error
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_source_bars
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_subject_new
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_summary_closed
import dev.alllexey.itmowidgets.shared.feature.recordbook.sheet_scores_from_table
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** Test tags of [RecordbookScreen]; a subject row is [subject] of its `entryId`. */
object RecordbookTestTags {
    const val PERIOD = "recordbook_period"
    const val YEAR = "recordbook_year"
    const val BARS = "recordbook_bars"
    const val LOADING = "recordbook_loading"
    const val LIST = "recordbook_list"
    const val SUMMARY = "recordbook_summary"

    /** The empty or error state in the list's place. */
    const val STATE = "recordbook_state"
    const val STATE_ACTION = "recordbook_state_action"
    const val NEW_MARK = "recordbook_new_mark"
    const val SHEET_MARK = "recordbook_sheet_mark"
    const val SCORE = "recordbook_score"
    const val GRADE = "recordbook_grade"
    const val PROGRESS = "recordbook_progress"
    const val SUBJECT_PREFIX = "recordbook_subject_"

    fun subject(entryId: Long): String = "$SUBJECT_PREFIX$entryId"
}

/**
 * The recordbook with its Koin ViewModel: loads on entry (a return to the list does not reload it), shows a failed
 * refresh or a failed BARS overlay in a snackbar and hands the period picker, a subject and the BARS sign-in to the
 * host. Android hosts it in `RecordbookFragment`.
 */
@Composable
fun RecordbookRoute(
    onOpenPeriods: (List<RecordbookProgram>, RecordbookSelection) -> Unit,
    onOpenSubject: (RecordbookSelection, RecordbookSubject) -> Unit,
    onBarsLogin: () -> Unit,
    viewModel: RecordbookViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.refresh(RefreshMode.Silent) }
    RecordbookErrorSnackbars(state, snackbars, onRetry = { viewModel.refresh(RefreshMode.Force) }, onBarsLogin = onBarsLogin)
    RecordbookScreen(
        state = state,
        onOpenPeriods = onOpenPeriods,
        onBarsChange = viewModel::setBarsEnabled,
        onRefresh = { viewModel.refresh(RefreshMode.Pull) },
        onRetry = { viewModel.refresh(RefreshMode.Force) },
        onOpenSubject = onOpenSubject,
        snackbarHostState = snackbars,
    )
}

/**
 * The snackbar of a list that stays on screen: a failed refresh offers a retry; a failed BARS overlay offers the
 * BARS sign-in when the ITMO.ID session ended ([AppError.Unauthorized]), a retry otherwise. A new error replaces the
 * shown one, and the snackbar goes when both are gone.
 */
@Composable
internal fun RecordbookErrorSnackbars(
    state: RecordbookUiState,
    snackbars: SnackbarHostState,
    onRetry: () -> Unit,
    onBarsLogin: () -> Unit,
) {
    val content = state as? RecordbookUiState.Content
    RecordbookErrorSnackbars(content?.refreshError, content?.barsError, snackbars, onRetry, onBarsLogin)
}

/** [RecordbookErrorSnackbars] of any page that keeps its data on screen: the list and the subject page. */
@Composable
internal fun RecordbookErrorSnackbars(
    refreshError: AppError?,
    barsError: AppError?,
    snackbars: SnackbarHostState,
    onRetry: () -> Unit,
    onBarsLogin: () -> Unit,
) {
    LaunchedEffect(refreshError, barsError, snackbars) {
        val (message, action, perform) = when {
            refreshError != null -> Triple(
                getString(Res.string.recordbook_refresh_error, getString(refreshError.textResource())),
                getString(CoreRes.string.common_retry),
                onRetry,
            )
            barsError == AppError.Unauthorized -> Triple(
                getString(Res.string.recordbook_bars_login_required),
                getString(Res.string.recordbook_bars_login),
                onBarsLogin,
            )
            barsError != null -> Triple(
                getString(Res.string.recordbook_bars_error, getString(barsError.textResource())),
                getString(CoreRes.string.common_retry),
                onRetry,
            )
            else -> return@LaunchedEffect
        }
        val result = snackbars.showSnackbar(message, actionLabel = action, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) perform()
    }
}

/**
 * The recordbook list: the period button with its study year and the BARS chip above the subjects of the period.
 * The list puts subjects that need attention (with the reason in place of the assessment kind) above the others, and
 * a pass count once a final result exists. A row ends in its points with a bar, the own sheet total with the table
 * mark while the official points are empty, or the final result as a badge; an unread mark adds a dot after the name.
 *
 * The first load shows card placeholders; empty and error states take the list's place with a retry; a pull shows
 * the indicator. A switch to another period starts at the top; the position otherwise survives recreation.
 */
@Composable
fun RecordbookScreen(
    state: RecordbookUiState,
    onOpenPeriods: (List<RecordbookProgram>, RecordbookSelection) -> Unit,
    onBarsChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onOpenSubject: (RecordbookSelection, RecordbookSubject) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val header = state.header()
    ScrollToTopOnPeriodSwitch(header.selection, listState)
    Box(modifier.fillMaxSize().background(ItmoTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            RecordbookHeader(header, state.barsEnabled, onOpenPeriods, onBarsChange)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                RecordbookBody(state, listState, onRefresh, onRetry, onOpenSubject)
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

/** The periods and the selection a state carries for the header. */
private class RecordbookHeaderState(val programs: List<RecordbookProgram>, val selection: RecordbookSelection?)

private fun RecordbookUiState.header(): RecordbookHeaderState = when (this) {
    is RecordbookUiState.Loading -> RecordbookHeaderState(programs, selection)
    is RecordbookUiState.Content -> RecordbookHeaderState(programs, selection)
    is RecordbookUiState.Error -> RecordbookHeaderState(programs, selection)
    is RecordbookUiState.Empty -> RecordbookHeaderState(emptyList(), null)
}

/** Another period starts at the top of its list; the first selection and a recreation keep the saved position. */
@Composable
private fun ScrollToTopOnPeriodSwitch(selection: RecordbookSelection?, listState: LazyListState) {
    val key = selection?.let { "${it.program.id}:${it.period.semester}" }
    var shown by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(key) {
        if (key == null) return@LaunchedEffect
        if (shown != null && shown != key) listState.requestScrollToItem(0)
        shown = key
    }
}

@Composable
private fun RecordbookHeader(
    header: RecordbookHeaderState,
    barsEnabled: Boolean,
    onOpenPeriods: (List<RecordbookProgram>, RecordbookSelection) -> Unit,
    onBarsChange: (Boolean) -> Unit,
) {
    val selection = header.selection
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ItmoTheme.spacing.compact)
            .padding(top = ItmoTheme.spacing.related),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            PeriodButton(selection) { selection?.let { onOpenPeriods(header.programs, it) } }
            Text(
                // A blank year keeps the line, so the header does not jump when the periods arrive.
                selection?.period?.studyYear ?: " ",
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ItmoTheme.spacing.compact)
                    .padding(bottom = ItmoTheme.spacing.compact)
                    .testTag(RecordbookTestTags.YEAR),
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.bodySmall,
            )
        }
        BarsChip(barsEnabled, onBarsChange)
    }
}

/** `Widget.Material3.Button.TextButton` with the text at the start and the expand mark right after it. */
@Composable
private fun PeriodButton(selection: RecordbookSelection?, onClick: () -> Unit) {
    val color = ItmoTheme.colorScheme.onSurface
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ItmoTheme.spacing.touchTarget)
            .testTag(RecordbookTestTags.PERIOD),
        enabled = selection != null,
        contentPadding = PaddingValues(ItmoTheme.spacing.compact),
        // The View's text and icon colours had no disabled state: the button only waits for the periods.
        colors = ButtonDefaults.textButtonColors(contentColor = color, disabledContentColor = color),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                selection?.let { stringResource(Res.string.recordbook_period_value, it.period.course, it.period.semester) }
                    ?: stringResource(Res.string.recordbook_period_title),
                Modifier.weight(1f, fill = false),
                style = ItmoTheme.typography.titleMedium,
            )
            Icon(
                painterResource(KitRes.drawable.ic_expand_more),
                contentDescription = null,
                modifier = Modifier.padding(start = ItmoTheme.spacing.compact).size(ButtonIconSize),
                tint = ItmoTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** `Widget.Material3.Chip.Filter`: a check while BARS is on. It reports only the user's taps. */
@Composable
private fun BarsChip(enabled: Boolean, onChange: (Boolean) -> Unit) {
    FilterChip(
        selected = enabled,
        onClick = { onChange(!enabled) },
        label = { Text(stringResource(Res.string.recordbook_source_bars)) },
        modifier = Modifier.padding(end = ItmoTheme.spacing.compact).testTag(RecordbookTestTags.BARS),
        leadingIcon = if (enabled) {
            {
                Icon(
                    painterResource(KitRes.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
    )
}

@Composable
private fun RecordbookBody(
    state: RecordbookUiState,
    listState: LazyListState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onOpenSubject: (RecordbookSelection, RecordbookSubject) -> Unit,
) {
    when (state) {
        is RecordbookUiState.Loading -> Skeleton(
            SkeletonStyle.Cards,
            Modifier.fillMaxSize().testTag(RecordbookTestTags.LOADING),
            rows = SKELETON_ROWS,
            rowHeight = SkeletonRowHeight,
        )
        is RecordbookUiState.Error -> RecordbookState(
            icon = KitRes.drawable.ic_error,
            title = stringResource(CoreRes.string.common_load_error_title),
            description = stringResource(state.error.textResource()),
            onRetry = onRetry,
        )
        is RecordbookUiState.Empty -> EmptyState(onRetry)
        is RecordbookUiState.Content -> if (state.subjects.isEmpty()) {
            EmptyState(onRetry)
        } else {
            AppRefreshBox(state.refreshing, onRefresh, Modifier.fillMaxSize()) {
                RecordbookList(state, listState) { onOpenSubject(state.selection, it) }
            }
        }
    }
}

@Composable
private fun EmptyState(onRetry: () -> Unit) {
    RecordbookState(
        icon = KitRes.drawable.ic_menu_book,
        title = stringResource(Res.string.recordbook_empty_title),
        description = stringResource(Res.string.recordbook_empty_description),
        onRetry = onRetry,
    )
}

@Composable
private fun RecordbookState(
    icon: DrawableResource,
    title: String,
    description: String,
    onRetry: () -> Unit,
) {
    ContentState(
        title = title,
        modifier = Modifier.fillMaxSize().testTag(RecordbookTestTags.STATE),
        icon = painterResource(icon),
        description = description,
        action = ContentStateAction(
            stringResource(CoreRes.string.common_retry),
            onRetry,
            modifier = Modifier.testTag(RecordbookTestTags.STATE_ACTION),
        ),
    )
}

/** One entry of the list: the pass count, a section title or a subject. */
internal sealed interface RecordbookRow {
    val key: String

    data class Summary(val passed: Int, val total: Int) : RecordbookRow {
        override val key: String get() = "summary"
    }

    data class Section(val title: StringResource) : RecordbookRow {
        override val key: String get() = "section:${title.key}"
    }

    data class Subject(
        val value: RecordbookSubject,
        val sport: RecordbookSportState?,
        val reason: RecordbookAttentionReason? = null,
        val barsMissing: Boolean = false,
        /** An unread new or changed mark: the dot stays until the subject's page opens. */
        val isNew: Boolean = false,
        /** The connected sheet's total while the official points are empty; shown instead of a dash. */
        val sheetTotal: String? = null,
        override val key: String = "subject:${value.entryId}:${value.disciplineId}",
    ) : RecordbookRow
}

/** The rows of [state]: the pass count, then `Требуют внимания` and `Дисциплины` when anything needs attention. */
internal fun recordbookRows(state: RecordbookUiState.Content): List<RecordbookRow> {
    val subjects = state.subjects
    val (attention, regular) = subjects.partition { it.entryId in state.attention }
    val keys = mutableSetOf<String>()
    fun row(subject: RecordbookSubject) = RecordbookRow.Subject(
        subject,
        state.sport.takeIf { subject.isPhysicalEducation },
        reason = state.attention[subject.entryId],
        // PE is graded outside BARS; only other unmatched subjects need the hint.
        barsMissing = state.barsApplied && subject.barsJournal == null && !subject.isPhysicalEducation,
        isNew = subjectNameKey(subject.name) in state.newSubjects,
        sheetTotal = subject.sheetFallback(state.sheetTotals[subject.disciplineId]),
    ).let { item ->
        // A repeated entry stays listed; its key only has to differ from the first one's.
        if (keys.add(item.key)) item else item.copy(key = "${item.key}:${keys.size}").also { keys.add(it.key) }
    }
    return buildList {
        if (state.showSummary) {
            add(RecordbookRow.Summary(subjects.count { it.status == RecordbookSubjectStatus.PASSED }, subjects.size))
        }
        if (attention.isNotEmpty()) {
            add(RecordbookRow.Section(Res.string.recordbook_attention_section))
            addAll(attention.map(::row))
        }
        if (regular.isNotEmpty()) {
            if (attention.isNotEmpty()) add(RecordbookRow.Section(Res.string.recordbook_disciplines_section))
            addAll(regular.map(::row))
        }
    }
}

@Composable
private fun RecordbookList(
    state: RecordbookUiState.Content,
    listState: LazyListState,
    onOpen: (RecordbookSubject) -> Unit,
) {
    val rows = remember(state) { recordbookRows(state) }
    LazyColumn(
        Modifier.fillMaxSize().testTag(RecordbookTestTags.LIST),
        state = listState,
        contentPadding = PaddingValues(
            start = ItmoTheme.spacing.screenMargin,
            end = ItmoTheme.spacing.screenMargin,
            bottom = ItmoTheme.spacing.screenMargin,
        ),
    ) {
        items(rows.size, key = { rows[it].key }, contentType = { rows[it]::class }) { index ->
            when (val row = rows[index]) {
                is RecordbookRow.Summary -> SummaryCard(row)
                is RecordbookRow.Section -> SectionTitle(row.title)
                is RecordbookRow.Subject -> SubjectRow(row, onOpen)
            }
        }
    }
}

/** The pass count: `Сдано 7 из 10` over a thin bar of the share. */
@Composable
private fun SummaryCard(row: RecordbookRow.Summary) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = ItmoTheme.spacing.compact)
            .clip(ItmoTheme.shapes.cardContent)
            .background(ItmoTheme.colorScheme.surfaceContainer)
            .padding(ItmoTheme.spacing.cardPadding)
            .testTag(RecordbookTestTags.SUMMARY),
    ) {
        Text(
            stringResource(Res.string.recordbook_summary_closed, row.passed, row.total),
            color = ItmoTheme.colorScheme.onSurface,
            style = ItmoTheme.typography.titleMedium,
        )
        ProgressBar(
            RecordbookProgress(row.passed.toDouble(), row.total.toDouble()),
            ItmoTheme.colorScheme.primary,
            Modifier.padding(top = ItmoTheme.spacing.content).fillMaxWidth(),
        )
    }
}

/** A section title: `titleSmall` on `onSurface`, close to the cards' edge. */
@Composable
private fun SectionTitle(title: StringResource) {
    Text(
        stringResource(title),
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ItmoTheme.spacing.related)
            .padding(top = ItmoTheme.spacing.content, bottom = ItmoTheme.spacing.related)
            .semantics { heading() },
        color = ItmoTheme.colorScheme.onSurface,
        style = ItmoTheme.typography.titleSmall,
    )
}

/**
 * A subject: the name (two lines) with the new-mark dot, the metadata, and at the end the points
 * with a bar, the sheet total with the table mark, or the final badge. TalkBack reads the row as one description.
 */
@Composable
private fun SubjectRow(row: RecordbookRow.Subject, onOpen: (RecordbookSubject) -> Unit) {
    val subject = row.value
    val progress = subject.displayedScore(row.sport)
    val sheetTotal = row.sheetTotal
    val final = sheetTotal == null &&
        (subject.absent || subject.normalizedRate != RecordbookRate.InProgress || progress.value == null)
    val meta = row.reason?.text() ?: listOf(
        subject.assessmentLabel(),
        row.sport?.takeUnless { it is RecordbookSportState.Content }?.compactText().orEmpty(),
        if (row.barsMissing) stringResource(Res.string.recordbook_bars_missing) else "",
    ).filter(String::isNotBlank).joinToString(META_SEPARATOR)
    val result = when {
        sheetTotal != null -> stringResource(Res.string.sheet_scores_from_table, sheetTotal)
        final -> subject.displayRate()
        else -> stringResource(Res.string.recordbook_points_out_of, formatRecordbookNumber(progress.value!!), FULL_SCORE)
    }
    val description = listOf(
        if (row.isNew) stringResource(Res.string.recordbook_subject_new) else "",
        subject.name,
        meta,
        result,
    ).filter(String::isNotBlank).joinToString(DESCRIPTION_SEPARATOR)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = ItmoTheme.spacing.related)
            .clip(ItmoTheme.shapes.cardContent)
            .background(ItmoTheme.colorScheme.surfaceContainerLow)
            .testTag(RecordbookTestTags.subject(subject.entryId))
            .clickable { onOpen(subject) }
            .clearAndSetSemantics { contentDescription = description }
            .heightIn(min = ItmoTheme.spacing.touchTarget)
            .padding(horizontal = ItmoTheme.spacing.cardPadding, vertical = ItmoTheme.spacing.content),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = ItmoTheme.spacing.content)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    subject.name,
                    Modifier.weight(1f),
                    color = ItmoTheme.colorScheme.onSurface,
                    maxLines = NAME_LINES,
                    overflow = TextOverflow.Ellipsis,
                    style = ItmoTheme.typography.titleMedium.russian(),
                )
                if (row.isNew) {
                    Box(
                        Modifier
                            .padding(start = ItmoTheme.spacing.compact)
                            .size(NewMarkSize)
                            .background(ItmoTheme.colorScheme.primary, CircleShape)
                            .testTag(RecordbookTestTags.NEW_MARK),
                    )
                }
            }
            Text(
                meta,
                Modifier.padding(top = MetaGap),
                color = if (row.reason != null) ItmoTheme.colorScheme.error else ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.bodySmall.russian(),
            )
        }
        if (final) {
            GradeBadge(subject)
        } else {
            val score = sheetTotal ?: formatRecordbookNumber(progress.value!!)
            ScoreGroup(score, sheetTotal != null, progress, progressColor(row))
        }
    }
}

@Composable
private fun ScoreGroup(score: String, fromSheet: Boolean, progress: RecordbookProgress, color: Color) {
    Column(Modifier.widthIn(min = ScoreMinWidth), horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (fromSheet) {
                Icon(
                    painterResource(KitRes.drawable.ic_table),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = ItmoTheme.spacing.related)
                        .size(SheetMarkSize)
                        .testTag(RecordbookTestTags.SHEET_MARK),
                    tint = ItmoTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                score,
                Modifier.widthIn(max = ScoreMaxWidth).testTag(RecordbookTestTags.SCORE),
                color = ItmoTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = ItmoTheme.typography.titleMedium.tabular(),
            )
        }
        if (!fromSheet) {
            ProgressBar(
                progress,
                color,
                Modifier.padding(top = ItmoTheme.spacing.related).width(ScoreBarWidth).testTag(RecordbookTestTags.PROGRESS),
            )
        }
    }
}

/** Passed in green, failed in the error colour, anything else neutral; always on a quiet container of its colour. */
@Composable
private fun GradeBadge(subject: RecordbookSubject) {
    val colors = ItmoTheme.colorScheme
    val color = when (subject.status) {
        RecordbookSubjectStatus.PASSED -> ItmoTheme.extendedColors.recordbookPassed
        RecordbookSubjectStatus.ATTENTION -> colors.error
        RecordbookSubjectStatus.IN_PROGRESS -> colors.onSurfaceVariant
    }
    val container = if (subject.status == RecordbookSubjectStatus.IN_PROGRESS) {
        colors.surfaceContainerHighest
    } else {
        color.copy(alpha = BADGE_ALPHA)
    }
    Text(
        subject.badgeText(),
        Modifier
            .testTag(RecordbookTestTags.GRADE)
            .widthIn(min = BadgeMinWidth)
            .background(container, ItmoTheme.shapes.small)
            .padding(horizontal = ItmoTheme.spacing.compact, vertical = ItmoTheme.spacing.related),
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1,
        style = ItmoTheme.typography.labelLarge.tabular(),
    )
}

/** The bar's colour: the error for an attention reason, the sport colour for PE, else the subject's status. */
@Composable
private fun progressColor(row: RecordbookRow.Subject): Color = when {
    row.reason != null -> ItmoTheme.colorScheme.error
    row.value.isPhysicalEducation -> ItmoTheme.extendedColors.sportScoreAttendance
    else -> when (row.value.status) {
        RecordbookSubjectStatus.PASSED -> ItmoTheme.extendedColors.recordbookPassed
        RecordbookSubjectStatus.ATTENTION -> ItmoTheme.colorScheme.error
        RecordbookSubjectStatus.IN_PROGRESS -> ItmoTheme.colorScheme.primary
    }
}

/** `LinearProgressIndicator` with a 4 dp rounded track in `outlineVariant`, no gap and no stop mark. */
@Composable
private fun ProgressBar(progress: RecordbookProgress, color: Color, modifier: Modifier) {
    val fraction = progress.progress.toFloat() / RecordbookProgress.SCALE
    Box(
        modifier
            .height(BarThickness)
            .clip(CircleShape)
            .background(ItmoTheme.colorScheme.outlineVariant),
    ) {
        if (fraction > 0f) {
            Box(Modifier.fillMaxWidth(fraction).height(BarThickness).clip(CircleShape).background(color))
        }
    }
}

/** Russian hyphenation and the balanced line breaks of `breakStrategy="high_quality"`. */
private fun TextStyle.russian(): TextStyle =
    copy(localeList = LocaleList("ru"), hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph)

private fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = TABULAR_FIGURES)

private const val NAME_LINES = 2
private const val SKELETON_ROWS = 5
private const val FULL_SCORE = "100"
private const val DESCRIPTION_SEPARATOR = ". "
private const val TABULAR_FIGURES = "tnum"

/** `bg_grade_badge`'s tint: the result colour at alpha 0x29. */
private const val BADGE_ALPHA = 0x29 / 255f

private val SkeletonRowHeight = 88.dp
private val ButtonIconSize = 18.dp
private val NewMarkSize = 8.dp
private val SheetMarkSize = 16.dp
private val MetaGap = 2.dp
private val ScoreMinWidth = 64.dp
private val ScoreMaxWidth = 96.dp
private val ScoreBarWidth = 64.dp
private val BadgeMinWidth = 48.dp
private val BarThickness = 4.dp

