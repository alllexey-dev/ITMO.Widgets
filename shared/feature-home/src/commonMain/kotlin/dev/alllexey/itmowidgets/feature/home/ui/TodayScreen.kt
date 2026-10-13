package dev.alllexey.itmowidgets.feature.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import dev.alllexey.itmowidgets.core.lesson.LessonTarget
import dev.alllexey.itmowidgets.core.lesson.TimelineItem
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.designsystem.components.expressive.ItmoWavyProgress
import dev.alllexey.itmowidgets.designsystem.components.state.AppRefreshBox
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateLoading
import dev.alllexey.itmowidgets.designsystem.icons.drawable
import dev.alllexey.itmowidgets.designsystem.kit.ActionCard
import dev.alllexey.itmowidgets.designsystem.kit.ItmoCard
import dev.alllexey.itmowidgets.designsystem.kit.LessonTimeline
import dev.alllexey.itmowidgets.designsystem.kit.SectionHeader
import dev.alllexey.itmowidgets.designsystem.kit.SmallButton
import dev.alllexey.itmowidgets.designsystem.kit.StaleContent
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.kit.toTimelineRows
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.home.presentation.TodayDay
import dev.alllexey.itmowidgets.feature.home.presentation.TodayHero
import dev.alllexey.itmowidgets.feature.home.presentation.TodayUiState
import dev.alllexey.itmowidgets.shared.compose.strings.common_load_error_title
import dev.alllexey.itmowidgets.shared.compose.strings.common_retry
import dev.alllexey.itmowidgets.shared.feature.home.Res
import dev.alllexey.itmowidgets.shared.feature.home.today_empty_title
import dev.alllexey.itmowidgets.shared.feature.home.today_join
import dev.alllexey.itmowidgets.shared.feature.home.today_online
import dev.alllexey.itmowidgets.shared.feature.home.today_todo_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.compose.strings.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes
import dev.alllexey.itmowidgets.shared.designsystem.ic_today
import dev.alllexey.itmowidgets.shared.designsystem.ic_videocam

/** Test tags of [TodayScreen]. */
object TodayTestTags {
    const val LIST = "today:list"
    const val LOADING = "today:loading"
    const val ERROR = "today:error"
    const val EMPTY = "today:empty"
    const val HERO = "today:hero"
    const val JOIN = "today:join"
    const val DAY = "today:day"

    fun todo(id: String) = "today:todo:$id"
}

/** What Сегодня asks its host to do; navigation and the meeting link stay with the platform. */
data class TodayActions(
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onLesson: (LessonTarget) -> Unit = {},
    val onJoin: (url: String) -> Unit = {},
    val onTodo: (EntryRoute) -> Unit = {},
)

/**
 * Сегодня (docs/features/home.md): the hero with the lesson to go to now, the day as the kit's timeline (tomorrow in
 * the evening), then `Нужно сделать` when there is something. Pull to refresh; the shell's `Пропуск` button floats over
 * the bottom end, so the list keeps clear of it. Stateless; [TodayRoute] feeds it.
 */
@Composable
fun TodayScreen(
    state: TodayUiState,
    actions: TodayActions,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    when (state) {
        TodayUiState.Loading -> ContentStateLoading(modifier.testTag(TodayTestTags.LOADING))
        is TodayUiState.Failed -> AppRefreshBox(state.retrying, actions.onRetry, modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                ContentState(
                    title = stringResource(CoreRes.string.common_load_error_title),
                    modifier = Modifier.testTag(TodayTestTags.ERROR),
                    description = state.error.toUiText().asString(),
                    action = ContentStateAction(stringResource(CoreRes.string.common_retry), actions.onRetry),
                )
            }
        }
        is TodayUiState.Content -> AppRefreshBox(state.refreshing, actions.onRefresh, modifier.fillMaxSize()) {
            Content(state, actions, listState)
        }
    }
}

@Composable
private fun Content(state: TodayUiState.Content, actions: TodayActions, listState: LazyListState) {
    LazyColumn(
        Modifier.fillMaxSize().testTag(TodayTestTags.LIST),
        state = listState,
        contentPadding = PaddingValues(
            start = ItmoTheme.spacing.screenMargin,
            end = ItmoTheme.spacing.screenMargin,
            top = ItmoTheme.spacing.related,
            bottom = ItmoTheme.spacing.fabClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
    ) {
        state.hero?.let { hero -> item(key = "hero") { Hero(hero, actions) } }
        val day = state.day
        if (day != null) {
            item(key = "day-title") { SectionHeader(day.title.asString(), count = day.count.asString()) }
            item(key = "day") {
                val stale = state.stale
                if (stale == null) Day(day, actions) else StaleContent(stale.asString()) { Day(day, actions) }
            }
        } else if (state.hero == null) {
            item(key = "empty") {
                ContentState(
                    title = stringResource(Res.string.today_empty_title),
                    modifier = Modifier.testTag(TodayTestTags.EMPTY),
                    icon = painterResource(KitRes.drawable.ic_today),
                )
            }
        }
        if (state.todos.isNotEmpty()) {
            item(key = "todo-title") { SectionHeader(stringResource(Res.string.today_todo_title)) }
            items(state.todos, key = { "todo-${it.id}" }) { todo ->
                ActionCard(
                    title = todo.title.asString(),
                    reason = todo.reason.asString(),
                    action = todo.action.asString(),
                    onAction = { actions.onTodo(todo.target) },
                    icon = painterResource(todo.icon.drawable),
                    modifier = Modifier.testTag(TodayTestTags.todo(todo.id)),
                    tone = Tone.Warning,
                )
            }
        }
    }
}

@Composable
private fun Day(day: TodayDay, actions: TodayActions) {
    LessonTimeline(
        day.items.toTimelineRows(),
        Modifier.testTag(TodayTestTags.DAY),
        showNow = day.showNow,
        onLessonClick = { index -> (day.items[index] as? TimelineItem.Lesson)?.let { actions.onLesson(it.lesson.target) } },
    )
}

/**
 * The flow's one hero: where to go (the big room and the building in full) or `Онлайн` with `Войти`, how long
 * until it starts or ends, what it is; the current lesson's elapsed part on the wavy bar. The way there
 * (`Маршрут`) waits for the map.
 */
@Composable
private fun Hero(hero: TodayHero, actions: TodayActions) {
    ItmoCard(
        modifier = Modifier.testTag(TodayTestTags.HERO),
        tone = Tone.Primary,
        hero = true,
        onClick = { actions.onLesson(hero.target) },
    ) {
        val quiet = LocalContentColor.current.copy(alpha = QUIET_ALPHA)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                hero.label.asString(),
                Modifier.weight(1f),
                style = ItmoTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(hero.countdown.asString(), color = quiet, style = ItmoTheme.typography.labelLarge)
        }
        Text(
            hero.subject,
            style = ItmoTheme.emphasizedTypography.headlineSmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(hero.byline.asString(), color = quiet, style = ItmoTheme.typography.bodyMedium)
        when {
            hero.online -> Online(hero, actions)
            hero.room != null || hero.address != null -> Place(hero, quiet)
            else -> Text(hero.time, color = quiet, style = ItmoTheme.typography.titleSmall)
        }
        if (!hero.online && hero.note != null) {
            Text(hero.note, color = quiet, style = ItmoTheme.typography.bodyMedium)
        }
    }
}

/**
 * `Онлайн` with the small `Войти` next to it (owner, 2026-10-13, hero lab variant 2): at least a `content` gap
 * between them, the button under the word when the row is too narrow for both.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Online(hero: TodayHero, actions: TodayActions) {
    val quiet = LocalContentColor.current.copy(alpha = QUIET_ALPHA)
    val gap = ItmoTheme.spacing.content
    FlowRow(
        Modifier.fillMaxWidth().padding(top = ItmoTheme.spacing.related),
        horizontalArrangement = remember(gap) { SpaceBetweenAtLeast(gap) },
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
    ) {
        Text(
            stringResource(Res.string.today_online),
            Modifier.align(Alignment.CenterVertically),
            style = ItmoTheme.emphasizedTypography.displaySmall,
        )
        hero.videoUrl?.let { url ->
            SmallButton(
                stringResource(Res.string.today_join),
                onClick = { actions.onJoin(url) },
                modifier = Modifier.align(Alignment.CenterVertically).testTag(TodayTestTags.JOIN),
                icon = painterResource(KitRes.drawable.ic_videocam),
            )
        }
    }
    Text(hero.time, color = quiet, style = ItmoTheme.typography.titleSmall)
    hero.note?.let { Text(it, color = quiet, style = ItmoTheme.typography.bodyMedium) }
}

/** [Arrangement.SpaceBetween] with a minimum gap: a `FlowRow` wraps by [spacing], which `SpaceBetween` has as zero. */
private class SpaceBetweenAtLeast(override val spacing: Dp) : Arrangement.Horizontal {
    override fun Density.arrange(totalSize: Int, sizes: IntArray, layoutDirection: LayoutDirection, outPositions: IntArray) =
        with(Arrangement.SpaceBetween) { arrange(totalSize, sizes, layoutDirection, outPositions) }
}

/** The big room never clips: the address goes under it when the row is too narrow for both. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Place(hero: TodayHero, quiet: Color) {
    FlowRow(
        Modifier.padding(top = ItmoTheme.spacing.related),
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
    ) {
        hero.room?.let {
            Text(it.asString(), Modifier.align(Alignment.Bottom), style = ItmoTheme.emphasizedTypography.displayMedium)
        }
        hero.address?.let {
            Text(
                it,
                Modifier.align(Alignment.Bottom).padding(bottom = ItmoTheme.spacing.compact),
                style = ItmoTheme.typography.titleMedium,
            )
        }
    }
    val progress = hero.progress
    if (progress != null) {
        ItmoWavyProgress({ progress }, Modifier.fillMaxWidth().padding(top = ItmoTheme.spacing.related))
        Row {
            Text(hero.start, Modifier.weight(1f), color = quiet, style = ItmoTheme.typography.labelMedium)
            Text(hero.end, color = quiet, style = ItmoTheme.typography.labelMedium)
        }
    } else {
        Text(hero.time, color = quiet, style = ItmoTheme.typography.titleSmall)
    }
}

/** Secondary text on the hero's `primaryContainer`, as the kit's lesson card does. */
private const val QUIET_ALPHA = 0.75f
