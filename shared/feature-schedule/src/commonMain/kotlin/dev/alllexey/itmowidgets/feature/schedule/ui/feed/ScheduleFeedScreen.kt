package dev.alllexey.itmowidgets.feature.schedule.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import dev.alllexey.itmowidgets.core.lesson.LessonTarget
import dev.alllexey.itmowidgets.core.lesson.TimelineItem
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.designsystem.components.state.AppRefreshBox
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateLoading
import dev.alllexey.itmowidgets.designsystem.gesture.tabSwipeHandover
import dev.alllexey.itmowidgets.designsystem.kit.HeaderAction
import dev.alllexey.itmowidgets.designsystem.kit.ItmoDetailScreen
import dev.alllexey.itmowidgets.designsystem.kit.ItmoRootScreen
import dev.alllexey.itmowidgets.designsystem.kit.LessonTimeline
import dev.alllexey.itmowidgets.designsystem.kit.PillSpec
import dev.alllexey.itmowidgets.designsystem.kit.StatusPill
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.kit.WeekStrip
import dev.alllexey.itmowidgets.designsystem.kit.WeekStripDay
import dev.alllexey.itmowidgets.designsystem.kit.toTimelineRows
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.schedule.presentation.feed.FeedRow
import dev.alllexey.itmowidgets.feature.schedule.presentation.feed.ScheduleFeedBody
import dev.alllexey.itmowidgets.feature.schedule.presentation.feed.ScheduleFeedUiState
import dev.alllexey.itmowidgets.shared.compose.strings.common_back
import dev.alllexey.itmowidgets.shared.compose.strings.common_load_error_title
import dev.alllexey.itmowidgets.shared.compose.strings.common_retry
import dev.alllexey.itmowidgets.shared.designsystem.ic_arrow_upward
import dev.alllexey.itmowidgets.shared.designsystem.ic_event_available
import dev.alllexey.itmowidgets.shared.designsystem.ic_group
import dev.alllexey.itmowidgets.shared.designsystem.ic_undo
import dev.alllexey.itmowidgets.shared.designsystem.ic_weekend
import dev.alllexey.itmowidgets.shared.feature.schedule.Res
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_friends_action
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_month_action
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_return_to_mine
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_today
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_today_pill
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.compose.strings.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** Test tags of [ScheduleFeedScreen]. */
object ScheduleFeedTestTags {
    const val LIST = "schedule:list"
    const val STRIP = "schedule:strip"
    const val LOADING = "schedule:loading"
    const val ERROR = "schedule:error"
    const val TODAY = "schedule:today"
    const val BACK_TO_MINE = "schedule:back_to_mine"
    const val FRIENDS = "schedule:friends"
    const val LESSON_PREFIX = "schedule:lesson:"

    fun row(key: String) = "schedule:row:$key"

    /** The lesson at [index] of the timeline of [date]. */
    fun lesson(date: LocalDate, index: Int) = "$LESSON_PREFIX$date:$index"
}

/** What the feed asks its host to do; [onBack] makes it a pushed screen (another user's schedule). */
data class ScheduleFeedActions(
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onVisibleDate: (LocalDate) -> Unit = {},
    val onNearStart: () -> Unit = {},
    val onNearEnd: () -> Unit = {},
    val onDay: (LocalDate) -> Unit = {},
    val onWeek: (Int) -> Unit = {},
    val onToday: () -> Unit = {},
    val onJumped: (Int) -> Unit = {},
    val onMonth: () -> Unit = {},
    val onPickFriend: () -> Unit = {},
    val onBackToMine: () -> Unit = {},
    val onLesson: (LessonTarget) -> Unit = {},
    val onBack: (() -> Unit)? = null,
)

/**
 * Расписание (docs/features/schedule.md): the static root header (`Сентябрь ▾` opens the month sheet, the friends
 * button the picker), the week strip that swipes by weeks, and the feed of days under it: sticky day headers over the
 * kit's timeline, short `пар нет` rows, collapsed weekends. `Сегодня` floats while the feed is off the current week,
 * `Вернуться к своему` while a friend's schedule is shown. Stateless; [ScheduleFeedRoute] feeds it.
 */
@Composable
fun ScheduleFeedScreen(
    state: ScheduleFeedUiState,
    actions: ScheduleFeedActions,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    snackbars: SnackbarHostState = remember { SnackbarHostState() },
) {
    val title = state.title.asString()
    val subtitle = state.subtitle?.asString()
    val body: @Composable (PaddingValues) -> Unit = { padding -> Body(state, actions, listState, snackbars, padding) }
    val onBack = actions.onBack
    if (onBack != null) {
        ItmoDetailScreen(
            title = title,
            onBack = onBack,
            backLabel = stringResource(CoreRes.string.common_back),
            modifier = modifier,
            subtitle = subtitle,
            floatingActionButton = { Fabs(state, actions) },
            content = body,
        )
    } else {
        ItmoRootScreen(
            title = title,
            modifier = modifier,
            subtitle = subtitle,
            onTitleClick = actions.onMonth.takeIf { state.monthMenu },
            titleClickLabel = stringResource(Res.string.schedule_month_action),
            actions = {
                if (state.canPickFriend) {
                    HeaderAction(
                        painterResource(KitRes.drawable.ic_group),
                        stringResource(Res.string.schedule_friends_action),
                        onClick = actions.onPickFriend,
                        modifier = Modifier.testTag(ScheduleFeedTestTags.FRIENDS),
                    )
                }
            },
            floatingActionButton = { Fabs(state, actions) },
            content = body,
        )
    }
}

@Composable
private fun Fabs(state: ScheduleFeedUiState, actions: ScheduleFeedActions) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content)) {
        if (state.offWeek) {
            Fab(
                stringResource(Res.string.schedule_today),
                painterResource(KitRes.drawable.ic_arrow_upward),
                actions.onToday,
                Modifier.testTag(ScheduleFeedTestTags.TODAY),
                tonal = true,
            )
        }
        if (state.friend != null) {
            Fab(
                stringResource(Res.string.schedule_return_to_mine),
                painterResource(KitRes.drawable.ic_undo),
                actions.onBackToMine,
                Modifier.testTag(ScheduleFeedTestTags.BACK_TO_MINE),
            )
        }
    }
}

@Composable
private fun Fab(text: String, icon: Painter, onClick: () -> Unit, modifier: Modifier, tonal: Boolean = false) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        icon = { Icon(icon, contentDescription = null) },
        text = { Text(text) },
        containerColor = if (tonal) ItmoTheme.colorScheme.tertiaryContainer else ItmoTheme.colorScheme.primaryContainer,
        contentColor = if (tonal) ItmoTheme.colorScheme.onTertiaryContainer else ItmoTheme.colorScheme.onPrimaryContainer,
    )
}

@Composable
private fun Body(
    state: ScheduleFeedUiState,
    actions: ScheduleFeedActions,
    listState: LazyListState,
    snackbars: SnackbarHostState,
    padding: PaddingValues,
) {
    Column(Modifier.fillMaxSize().padding(padding)) {
        WeekPager(state, actions)
        HorizontalDivider(color = ItmoTheme.colorScheme.outlineVariant)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val body = state.body) {
                ScheduleFeedBody.Loading -> ContentStateLoading(Modifier.testTag(ScheduleFeedTestTags.LOADING))
                is ScheduleFeedBody.Failed -> AppRefreshBox(state.refreshing, actions.onRetry, Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        ContentState(
                            title = stringResource(CoreRes.string.common_load_error_title),
                            modifier = Modifier.testTag(ScheduleFeedTestTags.ERROR),
                            description = body.error.toUiText().asString(),
                            action = ContentStateAction(stringResource(CoreRes.string.common_retry), actions.onRetry),
                        )
                    }
                }
                is ScheduleFeedBody.Days -> AppRefreshBox(state.refreshing, actions.onRefresh, Modifier.fillMaxSize()) {
                    Feed(state, body, actions, listState)
                }
            }
            SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** The strip pages by weeks; a swipe that settles on another week moves the feed there, the feed's week moves it. */
@Composable
private fun WeekPager(state: ScheduleFeedUiState, actions: ScheduleFeedActions) {
    val weeks = state.weeks
    if (weeks.isEmpty()) return
    val pager = rememberPagerState(initialPage = state.selectedWeek) { weeks.size }
    val firstMonday = weeks.first().monday
    var shownFirst by remember { mutableStateOf(firstMonday) }
    LaunchedEffect(firstMonday, state.selectedWeek) {
        // Earlier weeks joined the strip: the same week moves to a later page without a visible scroll.
        val shift = firstMonday.daysUntil(shownFirst) / DAYS_PER_WEEK
        shownFirst = firstMonday
        if (shift != 0) pager.scrollToPage(pager.currentPage + shift)
        if (pager.currentPage != state.selectedWeek) pager.animateScrollToPage(state.selectedWeek)
    }
    val selectedWeek by rememberUpdatedState(state.selectedWeek)
    val onWeek by rememberUpdatedState(actions.onWeek)
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page -> if (page != selectedWeek) onWeek(page) }
    }
    HorizontalPager(
        pager,
        Modifier.fillMaxWidth().tabSwipeHandover(pager).testTag(ScheduleFeedTestTags.STRIP),
        contentPadding = PaddingValues(horizontal = ItmoTheme.spacing.related),
        key = { weeks.getOrNull(it)?.monday?.toString() ?: "$it" },
    ) { page ->
        val week = weeks[page]
        WeekStrip(
            week.days.map { WeekStripDay(it.weekday, it.number, it.lessons, it.changed, it.today, it.past) },
            selected = week.days.indexOfFirst { it.date == state.selected }.takeIf { it >= 0 },
            onDayClick = { index -> actions.onDay(week.days[index].date) },
            modifier = Modifier.padding(vertical = ItmoTheme.spacing.compact),
        )
    }
}

@Composable
private fun Feed(state: ScheduleFeedUiState, body: ScheduleFeedBody.Days, actions: ScheduleFeedActions, listState: LazyListState) {
    val rows = body.rows
    val callbacks by rememberUpdatedState(actions)
    LaunchedEffect(state.jump, rows, state.loading) {
        val jump = state.jump ?: return@LaunchedEffect
        // While the day's week loads its row may still arrive; jump to it then.
        if (rows.none { jump.date in it.dates } && state.loading) return@LaunchedEffect
        val index = body.indexOf(jump.date)
        if (index >= 0) listState.scrollToItem(index)
        callbacks.onJumped(jump.id)
    }
    val jumping = state.jump != null
    LaunchedEffect(listState, rows, jumping) {
        if (jumping) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }
            .map { rows.getOrNull(it)?.date }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { callbacks.onVisibleDate(it) }
    }
    LaunchedEffect(listState, rows, jumping) {
        if (jumping || rows.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - EDGE_ROWS
        }.distinctUntilChanged().filter { it }.collect { callbacks.onNearEnd() }
    }
    LaunchedEffect(listState, rows, jumping) {
        if (jumping || rows.isEmpty()) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
            .distinctUntilChanged()
            .filter { it }
            .collect { callbacks.onNearStart() }
    }
    LazyColumn(
        Modifier.fillMaxSize().testTag(ScheduleFeedTestTags.LIST),
        state = listState,
        contentPadding = PaddingValues(bottom = ItmoTheme.spacing.fabClearance),
    ) {
        rows.forEach { row ->
            when (row) {
                // Every row that starts a day (and a week) is a sticky header, so a day's header never stays pinned over
                // the free days after it.
                is FeedRow.WeekLabel -> stickyHeader(row.key) { WeekLabel(row.text.asString()) }
                is FeedRow.DayHeader -> stickyHeader(row.key) { DayHeader(row) }
                is FeedRow.Lessons -> item(row.key) {
                    LessonTimeline(
                        row.items.toTimelineRows(),
                        Modifier
                            .padding(horizontal = ItmoTheme.spacing.screenMargin)
                            .padding(bottom = ItmoTheme.spacing.related)
                            .testTag(ScheduleFeedTestTags.row(row.key)),
                        showNow = row.showNow,
                        onLessonClick = { index ->
                            (row.items[index] as? TimelineItem.Lesson)?.let { actions.onLesson(it.lesson.target) }
                        },
                        lessonTag = { index -> ScheduleFeedTestTags.lesson(row.date, index) },
                    )
                }
                is FeedRow.NoLessons -> stickyHeader(row.key) {
                    QuietRow(painterResource(KitRes.drawable.ic_event_available), row.text.asString(), row.key)
                }
                is FeedRow.Weekend -> stickyHeader(row.key) {
                    QuietRow(painterResource(KitRes.drawable.ic_weekend), row.text.asString(), row.key)
                }
            }
        }
    }
}

@Composable
private fun WeekLabel(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(ItmoTheme.colorScheme.background)
            .padding(horizontal = ItmoTheme.spacing.screenMargin)
            .padding(top = ItmoTheme.spacing.group, bottom = ItmoTheme.spacing.related),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = ItmoTheme.colorScheme.primary, style = ItmoTheme.typography.labelLarge)
        HorizontalDivider(Modifier.weight(1f).padding(start = ItmoTheme.spacing.content), color = ItmoTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun DayHeader(row: FeedRow.DayHeader) {
    val colors = ItmoTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = ItmoTheme.spacing.screenMargin)
            .padding(top = ItmoTheme.spacing.content, bottom = ItmoTheme.spacing.compact)
            .semantics(mergeDescendants = true) { heading() }
            .testTag(ScheduleFeedTestTags.row(row.key)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
    ) {
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
        ) {
            Text(
                row.title,
                Modifier.weight(1f, fill = false),
                color = if (row.today) colors.primary else colors.onSurface,
                style = ItmoTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (row.today) StatusPill(PillSpec(stringResource(Res.string.schedule_today_pill), Tone.Primary, solid = true))
        }
        Text(row.count.asString(), color = colors.onSurfaceVariant, style = ItmoTheme.typography.labelLarge)
    }
}

/** `пар нет` and a free weekend: one quiet row each. */
@Composable
private fun QuietRow(icon: Painter, text: String, key: String) {
    Surface(
        Modifier
            .fillMaxWidth()
            .background(ItmoTheme.colorScheme.background)
            .padding(horizontal = ItmoTheme.spacing.screenMargin, vertical = ItmoTheme.spacing.compact)
            .testTag(ScheduleFeedTestTags.row(key)),
        shape = ItmoTheme.shapes.cardContent,
        color = ItmoTheme.colorScheme.surfaceContainerLow,
        contentColor = ItmoTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            Modifier.padding(horizontal = ItmoTheme.spacing.cardPadding, vertical = ItmoTheme.spacing.content),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
        ) {
            Icon(icon, contentDescription = null, Modifier.size(ItmoTheme.spacing.stateInlineIcon))
            Text(text, style = ItmoTheme.typography.bodyLarge)
        }
    }
}

private const val DAYS_PER_WEEK = 7

/** The feed asks for more weeks this many rows before its end. */
private const val EDGE_ROWS = 4
