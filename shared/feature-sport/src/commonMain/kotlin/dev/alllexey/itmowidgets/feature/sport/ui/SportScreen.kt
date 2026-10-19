package dev.alllexey.itmowidgets.feature.sport.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.designsystem.components.charts.ScoreRing
import dev.alllexey.itmowidgets.designsystem.components.charts.ScoreRingSector
import dev.alllexey.itmowidgets.designsystem.components.controls.ItmoActivityIndicator
import dev.alllexey.itmowidgets.designsystem.components.state.AppRefreshBox
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateLoading
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateSize
import dev.alllexey.itmowidgets.designsystem.kit.ChipSpec
import dev.alllexey.itmowidgets.designsystem.kit.FilterChips
import dev.alllexey.itmowidgets.designsystem.kit.ItmoCard
import dev.alllexey.itmowidgets.designsystem.kit.ItmoRootScreen
import dev.alllexey.itmowidgets.designsystem.kit.LessonCard
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListRow
import dev.alllexey.itmowidgets.designsystem.kit.OccupancyBar
import dev.alllexey.itmowidgets.designsystem.kit.SearchField
import dev.alllexey.itmowidgets.designsystem.kit.SectionHeader
import dev.alllexey.itmowidgets.designsystem.kit.SegmentedChoice
import dev.alllexey.itmowidgets.designsystem.kit.StatusPill
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.kit.WeekStrip
import dev.alllexey.itmowidgets.designsystem.kit.WeekStripDay
import dev.alllexey.itmowidgets.designsystem.kit.ink
import dev.alllexey.itmowidgets.designsystem.kit.tabular
import dev.alllexey.itmowidgets.designsystem.kit.toLessonView
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportMyBody
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportMyUiState
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportScoreUi
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportCatalogRow
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportDayPart
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportEmptyAction
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportQuickFilter
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportRowAction
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignBody
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignUiState
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_chevron_right
import dev.alllexey.itmowidgets.shared.designsystem.ic_event_busy
import dev.alllexey.itmowidgets.shared.designsystem.ic_hourglass_top
import dev.alllexey.itmowidgets.shared.designsystem.ic_lock
import dev.alllexey.itmowidgets.shared.designsystem.ic_sprint
import dev.alllexey.itmowidgets.shared.designsystem.ic_tune
import dev.alllexey.itmowidgets.shared.feature.sport.SportStrings
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.painterResource
import dev.alllexey.itmowidgets.designsystem.components.buttons.TonalButton

/** The two parts of the Спорт tab. */
enum class SportTab { MY, SIGN }

/** Test tags of the Спорт tab. */
object SportTestTags {
    const val TABS = "sport:tabs"
    const val MY_LIST = "sport:my"
    const val SIGN_LIST = "sport:sign"
    const val SCORE = "sport:score"
    const val SECTIONS = "sport:sections"

    fun section(id: Long) = "sport:section:$id"
    fun sectionDate(id: Long, index: Int) = "sport:section:$id:date:$index"
    const val SEARCH = "sport:search"
    const val STRIP = "sport:strip"
    const val LOADING = "sport:loading"
    const val ERROR = "sport:error"
    const val EMPTY = "sport:empty"

    fun tab(tab: SportTab) = "sport:tab:${tab.name.lowercase()}"
    fun booking(lessonId: Long) = "sport:booking:$lessonId"
    fun row(lessonId: Long) = "sport:row:$lessonId"
    fun rowAction(lessonId: Long) = "sport:row_action:$lessonId"
}

/** What `Моё` asks of its host and ViewModel. */
data class SportMyActions(
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onOpenPoints: () -> Unit = {},
    val onOpenBooking: (Long) -> Unit = {},
    /** The off switch of a section. */
    val onStopSection: (Long) -> Unit = {},
    /** One date of a section on or off. */
    val onSectionDate: (sectionId: Long, index: Int, on: Boolean) -> Unit = { _, _, _ -> },
    val onFindLesson: () -> Unit = {},
    val onConfirmCancel: () -> Unit = {},
    val onDismissCancel: () -> Unit = {},
)

/** What `Запись` asks of its host and ViewModel; the more-filters sheet has its own [filters]. */
data class SportSignActions(
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onChip: (SportQuickFilter) -> Unit = {},
    val onDay: (LocalDate) -> Unit = {},
    val onWeek: (Int) -> Unit = {},
    val onOpenFilters: () -> Unit = {},
    val onEmptyAction: (SportEmptyAction) -> Unit = {},
    val onResetFilters: () -> Unit = {},
    val onOpenLesson: (Long) -> Unit = {},
    val onRowAction: (Long) -> Unit = {},
    val filters: SportFiltersActions = SportFiltersActions(),
    val prompt: SportPromptActions = SportPromptActions(),
)

/**
 * The Спорт tab (docs/features/sport.md): the static root header [title], `Моё` / `Запись` as a [SegmentedChoice] over
 * a pager of the two parts, so a horizontal swipe switches them too. Stateless: [SportRoute] feeds it; the
 * more-filters and queue sheets open from the state.
 */
@Composable
fun SportScreen(
    title: String,
    tab: SportTab,
    onTab: (SportTab) -> Unit,
    my: SportMyUiState,
    sign: SportSignUiState,
    myActions: SportMyActions,
    signActions: SportSignActions,
    modifier: Modifier = Modifier,
    snackbars: SnackbarHostState = remember { SnackbarHostState() },
) {
    ItmoRootScreen(title = title, modifier = modifier) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                SegmentedChoice(
                    options = listOf(text(SportStrings.sport_tab_my), text(SportStrings.sport_tab_sign)),
                    selectedIndex = tab.ordinal,
                    onSelect = { onTab(SportTab.entries[it]) },
                    modifier = Modifier
                        .padding(horizontal = ItmoTheme.spacing.screenMargin)
                        .padding(top = ItmoTheme.spacing.related)
                        .testTag(SportTestTags.TABS),
                    testTags = SportTab.entries.map(SportTestTags::tab),
                )
                TabPager(tab, onTab) { page ->
                    when (page) {
                        SportTab.MY -> MyPart(my, myActions)
                        SportTab.SIGN -> SignPart(sign, signActions)
                    }
                }
            }
            SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter))
        }
    }
    if (tab == SportTab.SIGN) {
        sign.filters?.let { SportFiltersSheet(it, signActions.filters) }
        sign.prompt?.let { SportPromptSheet(it, signActions.prompt) }
    }
    if (tab == SportTab.MY) my.cancel?.let { SportCancelDialog(it, myActions.onConfirmCancel, myActions.onDismissCancel) }
}

/** The parts side by side: a tap on the choice scrolls the pager, a swipe that settles on a part selects it. */
@Composable
private fun TabPager(tab: SportTab, onTab: (SportTab) -> Unit, page: @Composable (SportTab) -> Unit) {
    val pager = rememberPagerState(initialPage = tab.ordinal) { SportTab.entries.size }
    LaunchedEffect(tab) {
        if (pager.targetPage != tab.ordinal) pager.animateScrollToPage(tab.ordinal)
    }
    val selected by rememberUpdatedState(tab)
    val select by rememberUpdatedState(onTab)
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { if (it != selected.ordinal) select(SportTab.entries[it]) }
    }
    HorizontalPager(pager, Modifier.fillMaxSize(), key = { SportTab.entries[it].name }) {
        page(SportTab.entries[it])
    }
}

@Composable
private fun SportList(tag: String, content: LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().testTag(tag),
        contentPadding = PaddingValues(
            start = ItmoTheme.spacing.screenMargin,
            end = ItmoTheme.spacing.screenMargin,
            top = ItmoTheme.spacing.content,
            bottom = ItmoTheme.spacing.section,
        ),
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
        content = content,
    )
}

@Composable
private fun MyPart(state: SportMyUiState, actions: SportMyActions) {
    val body = state.body
    AppRefreshBox((body as? SportMyBody.Content)?.refreshing == true, actions.onRefresh, Modifier.fillMaxSize()) {
        SportList(SportTestTags.MY_LIST) {
            state.score?.let { score -> item(key = "score") { ScoreCard(score, actions.onOpenPoints) } }
            when (body) {
                SportMyBody.Loading -> item(key = "loading") { ContentStateLoading(Modifier.testTag(SportTestTags.LOADING), ContentStateSize.Compact) }
                is SportMyBody.Failed -> item(key = "error") {
                    ContentState(
                        title = text(CoreStrings.common_load_error_title),
                        modifier = Modifier.testTag(SportTestTags.ERROR),
                        size = ContentStateSize.Compact,
                        description = body.error.toUiText().asString(),
                        action = ContentStateAction(text(CoreStrings.common_retry), actions.onRetry),
                    )
                }
                is SportMyBody.Content -> {
                    item(key = "bookings-title") { SectionHeader(text(SportStrings.sport_bookings_title)) }
                    if (body.days.isEmpty()) {
                        item(key = "empty") {
                            ContentState(
                                title = text(SportStrings.sport_bookings_empty_title),
                                modifier = Modifier.testTag(SportTestTags.EMPTY),
                                size = ContentStateSize.Compact,
                                icon = painterResource(Res.drawable.ic_sprint),
                                description = text(SportStrings.sport_bookings_empty_description),
                                action = ContentStateAction(text(SportStrings.sport_bookings_open_schedule), actions.onFindLesson),
                            )
                        }
                    }
                    body.days.forEach { day ->
                        item(key = "day-${day.title}") { DayLabel(day.title.asString()) }
                        items(day.bookings, key = { "booking-${it.lessonId}" }) { booking ->
                            Box(Modifier.testTag(SportTestTags.booking(booking.lessonId))) {
                                LessonCard(booking.lesson.toLessonView(), onClick = { actions.onOpenBooking(booking.lessonId) })
                            }
                        }
                    }
                }
            }
            sections(state.sections, actions)
        }
    }
}

/** The score ring with the formula; it opens `Откуда баллы`. */
@Composable
internal fun ScoreCard(score: SportScoreUi, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val open = text(SportStrings.sport_points_title)
    ItmoCard(
        modifier.testTag(SportTestTags.SCORE).semantics(mergeDescendants = true) { contentDescription = open },
        tone = if (score.done) Tone.Success else Tone.Primary,
        hero = true,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.group)) {
            SportScoreRing(score, Modifier.size(SportSizes.ring))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related)) {
                Text(text(SportStrings.sport_score_title), style = ItmoTheme.typography.titleMedium)
                Text(score.formula.asString(), style = ItmoTheme.typography.bodyMedium)
                Text(score.status.asString(), style = ItmoTheme.typography.bodyMedium)
            }
            if (onClick != null) {
                Icon(painterResource(Res.drawable.ic_chevron_right), contentDescription = null)
            }
        }
    }
}

/** The ring of visits and bonus with the total inside; at 100 one closed ring in the success colour. */
@Composable
internal fun SportScoreRing(score: SportScoreUi, modifier: Modifier = Modifier, large: Boolean = false) {
    val colors = ItmoTheme.extendedColors
    val sectors = if (score.done) {
        listOf(ScoreRingSector(colors.success, PERCENT))
    } else {
        listOf(
            ScoreRingSector(colors.sportScoreAttendance, score.visitsShare * PERCENT),
            ScoreRingSector(colors.sportScoreBonus, score.bonusShare * PERCENT),
        )
    }
    ScoreRing(sectors, modifier, thickness = if (large) SportSizes.ringThicknessLarge else SportSizes.ringThickness) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${score.total}",
                style = (if (large) ItmoTheme.typography.headlineLarge else ItmoTheme.typography.titleLarge).tabular(),
                fontWeight = FontWeight.SemiBold,
            )
            Text(text(SportStrings.sport_score_of), color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.labelMedium)
        }
    }
}

/** The day over a booking: `Сегодня, 7 сент.`. */
@Composable
internal fun DayLabel(label: String) {
    Text(
        label,
        Modifier.padding(start = ItmoTheme.spacing.related, top = ItmoTheme.spacing.related),
        color = ItmoTheme.colorScheme.onSurfaceVariant,
        style = ItmoTheme.typography.labelLarge,
    )
}

@Composable
private fun SignPart(state: SportSignUiState, actions: SportSignActions) {
    val body = state.body
    AppRefreshBox((body as? SportSignBody.Day)?.refreshing == true, actions.onRefresh, Modifier.fillMaxSize()) {
        SportList(SportTestTags.SIGN_LIST) {
            item(key = "search") {
                SearchField(
                    query = state.query,
                    onQueryChange = actions.onQuery,
                    placeholder = text(SportStrings.sport_search_hint),
                    clearLabel = text(SportStrings.sport_search_clear),
                    modifier = Modifier.testTag(SportTestTags.SEARCH),
                    action = painterResource(Res.drawable.ic_tune),
                    actionLabel = text(SportStrings.sport_filters_title),
                    badge = state.filterCount,
                    onAction = actions.onOpenFilters,
                )
            }
            item(key = "chips") {
                FilterChips(
                    chips = state.chips.map { ChipSpec(it.label.asString(), it.selected) },
                    onClick = { actions.onChip(state.chips[it].filter) },
                )
            }
            item(key = "strip") { WeekPager(state, actions) }
            when (body) {
                SportSignBody.Loading -> item(key = "loading") { ContentStateLoading(Modifier.testTag(SportTestTags.LOADING), ContentStateSize.Compact) }
                is SportSignBody.Failed -> item(key = "error") {
                    ContentState(
                        title = text(CoreStrings.common_load_error_title),
                        modifier = Modifier.testTag(SportTestTags.ERROR),
                        size = ContentStateSize.Compact,
                        description = body.error.toUiText().asString(),
                        action = ContentStateAction(text(CoreStrings.common_retry), actions.onRetry),
                    )
                }
                is SportSignBody.Day -> day(body, actions)
            }
        }
    }
}

private fun LazyListScope.day(body: SportSignBody.Day, actions: SportSignActions) {
    val empty = body.empty
    if (empty != null) {
        item(key = "empty") {
            ContentState(
                title = empty.title.asString(),
                modifier = Modifier.testTag(SportTestTags.EMPTY),
                size = ContentStateSize.Compact,
                icon = painterResource(if (empty.filtered) Res.drawable.ic_tune else Res.drawable.ic_event_busy),
                description = empty.text?.asString(),
                action = empty.action?.let { ContentStateAction(it.label.asString(), { actions.onEmptyAction(it) }) },
            )
        }
        return
    }
    item(key = "day-title") { SectionHeader(body.title.asString(), count = body.count?.asString()) }
    item(key = "rows") { ListGroup { body.rows.forEach { row -> item { CatalogRow(row, actions) } } } }
    body.hidden?.let { hidden ->
        item(key = "hidden") {
            Row(Modifier.fillMaxWidth().padding(start = ItmoTheme.spacing.related), verticalAlignment = Alignment.CenterVertically) {
                Text(hidden.asString(), Modifier.weight(1f), color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium)
                TextButton(onClick = actions.onResetFilters) { Text(text(SportStrings.common_reset)) }
            }
        }
    }
}

/**
 * The strip pages by weeks; a swipe that settles on another week selects a day in it. Its swipes stay with the weeks:
 * at the first or the last week they do not reach the tab's pager.
 */
@Composable
private fun WeekPager(state: SportSignUiState, actions: SportSignActions) {
    val weeks = state.weeks
    if (weeks.isEmpty()) return
    val pager = rememberPagerState(initialPage = state.selectedWeek) { weeks.size }
    LaunchedEffect(state.selectedWeek) {
        if (pager.currentPage != state.selectedWeek) pager.animateScrollToPage(state.selectedWeek)
    }
    val selectedWeek by rememberUpdatedState(state.selectedWeek)
    val onWeek by rememberUpdatedState(actions.onWeek)
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page -> if (page != selectedWeek) onWeek(page) }
    }
    HorizontalPager(pager, Modifier.fillMaxWidth().nestedScroll(KeepHorizontalSwipes).testTag(SportTestTags.STRIP)) { page ->
        val week = weeks[page]
        WeekStrip(
            days = week.map { WeekStripDay(it.weekday, it.number, it.lessons, changed = false, today = it.today, past = it.past) },
            selected = week.indexOfFirst { it.selected }.takeIf { it >= 0 },
            onDayClick = { actions.onDay(week[it].date) },
        )
    }
}

/**
 * One catalog lesson: the time, the section, its kind and place, the places, friends, a clash and one action; on a
 * narrow row the action moves under the lines, so the section keeps its width.
 */
@Composable
private fun CatalogRow(row: SportCatalogRow, actions: SportSignActions) {
    BoxWithConstraints {
        val compact = maxWidth < SportSizes.compactRow
        ListRow(
            title = row.section,
            modifier = Modifier.testTag(SportTestTags.row(row.lessonId)),
            leading = {
                Column(Modifier.timeColumn()) {
                    Text(row.start, style = ItmoTheme.typography.titleSmall.tabular(), fontWeight = FontWeight.SemiBold, softWrap = false)
                    Text(row.end, color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodySmall.tabular(), softWrap = false)
                }
            },
            below = {
                KindTag(row.kind.asString(), row.kindTone, row.place?.asString())
                if (row.occupancy != null || row.friends.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact)) {
                        // Weighted, so the bar gives way first and the friends' avatars keep their size.
                        row.occupancy?.let { OccupancyBar(it.text.asString(), it.share, it.tone.tone(), Modifier.weight(1f, fill = false)) }
                        FriendAvatars(row.friends)
                    }
                }
                row.clash?.let { clash ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related)) {
                        Icon(painterResource(Res.drawable.ic_event_busy), null, Modifier.size(SportSizes.smallIcon), tint = Tone.Warning.ink())
                        Text(clash.asString(), color = Tone.Warning.ink(), style = ItmoTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (compact) RowAction(row.action, row.lessonId, actions.onRowAction)
            },
            accessory = if (compact) null else ({ RowAction(row.action, row.lessonId, actions.onRowAction) }),
            onClick = { actions.onOpenLesson(row.lessonId) },
        )
    }
}

/** `Записаться`, `В очередь`, a status pill or the lock with the reason. */
@Composable
internal fun RowAction(action: SportRowAction, lessonId: Long, onAction: (Long) -> Unit) {
    val tag = Modifier.testTag(SportTestTags.rowAction(lessonId))
    when (action) {
        is SportRowAction.Sign -> TonalButton(onClick = { onAction(lessonId) }, modifier = tag, enabled = !action.busy, contentPadding = SportSizes.smallButton) {
            if (action.busy) ItmoActivityIndicator() else Text(text(SportStrings.sport_lesson_sign_up))
        }
        is SportRowAction.Queue -> OutlinedButton(onClick = { onAction(lessonId) }, modifier = tag, enabled = !action.busy, contentPadding = SportSizes.smallButton) {
            if (action.busy) ItmoActivityIndicator() else Text(text(SportStrings.sport_row_queue))
        }
        is SportRowAction.Status -> StatusPill(
            action.text.asString(),
            tag,
            tone = if (action.booked) Tone.Success else Tone.Warning,
            icon = if (action.booked) null else painterResource(Res.drawable.ic_hourglass_top),
        )
        is SportRowAction.Blocked -> Row(tag, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related)) {
            Icon(painterResource(Res.drawable.ic_lock), null, Modifier.size(SportSizes.smallIcon), tint = ItmoTheme.colorScheme.onSurfaceVariant)
            Text(
                action.reason.asString(),
                Modifier.widthIn(max = SportSizes.blockedReason),
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Consumes the horizontal rest of a nested swipe or fling, so it never scrolls a pager around it. */
private val KeepHorizontalSwipes = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available.copy(y = 0f)

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available.copy(y = 0f)
}

/** The part of the day as the sheet's choice. */
internal val SportDayPart.labelKey: String
    get() = when (this) {
        SportDayPart.ANYTIME -> SportStrings.sport_part_any
        SportDayPart.MORNING -> SportStrings.sport_part_morning
        SportDayPart.DAY -> SportStrings.sport_part_day
        SportDayPart.EVENING -> SportStrings.sport_part_evening
    }

private const val PERCENT = 100f
