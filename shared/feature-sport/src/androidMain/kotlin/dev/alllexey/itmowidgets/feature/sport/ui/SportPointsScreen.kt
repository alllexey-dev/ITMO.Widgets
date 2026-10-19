package dev.alllexey.itmowidgets.feature.sport.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.designsystem.components.state.AppRefreshBox
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateLoading
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateSize
import dev.alllexey.itmowidgets.designsystem.kit.ChipSpec
import dev.alllexey.itmowidgets.designsystem.kit.FilterChips
import dev.alllexey.itmowidgets.designsystem.kit.ItmoCard
import dev.alllexey.itmowidgets.designsystem.kit.ItmoDetailScreen
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListRow
import dev.alllexey.itmowidgets.designsystem.kit.SectionHeader
import dev.alllexey.itmowidgets.designsystem.kit.StatusPill
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.kit.tabular
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.presentation.points.SportPointsFilter
import dev.alllexey.itmowidgets.feature.sport.presentation.points.SportPointsUiState
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import dev.alllexey.itmowidgets.shared.designsystem.ic_emoji_events
import dev.alllexey.itmowidgets.shared.designsystem.ic_sprint
import dev.alllexey.itmowidgets.shared.feature.sport.SportStrings
import org.jetbrains.compose.resources.painterResource

/** Test tags of `Откуда баллы`. */
object SportPointsTags {
    const val LIST = "sport_points:list"
    const val CAP = "sport_points:cap"
    const val DONE = "sport_points:done"
    const val EMPTY = "sport_points:empty"
}

/** What `Откуда баллы` asks of its host and ViewModel. */
data class SportPointsActions(
    val onBack: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onFilter: (SportPointsFilter) -> Unit = {},
)

/**
 * `Откуда баллы` (DECISIONS.md, Спорт): visits plus the bonus make the total in the ring, the bonus counts up to 40,
 * then My ITMO's awards by month. No forecast.
 */
@Composable
fun SportPointsScreen(state: SportPointsUiState, actions: SportPointsActions, modifier: Modifier = Modifier) {
    ItmoDetailScreen(
        title = text(SportStrings.sport_points_title),
        onBack = actions.onBack,
        backLabel = text(CoreStrings.common_back),
        modifier = modifier,
        subtitle = text(SportStrings.sport_points_subtitle),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                SportPointsUiState.Loading -> ContentStateLoading()
                is SportPointsUiState.Failed -> ContentState(
                    title = text(CoreStrings.common_load_error_title),
                    description = state.error.toUiText().asString(),
                    action = ContentStateAction(text(CoreStrings.common_retry), actions.onRetry),
                )
                is SportPointsUiState.Content -> AppRefreshBox(state.refreshing, actions.onRefresh, Modifier.fillMaxSize()) {
                    Content(state, actions)
                }
            }
        }
    }
}

@Composable
private fun Content(state: SportPointsUiState.Content, actions: SportPointsActions) {
    LazyColumn(
        Modifier.fillMaxSize().testTag(SportPointsTags.LIST),
        contentPadding = PaddingValues(ItmoTheme.spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
    ) {
        item(key = "formula") { Formula(state) }
        item(key = "cap") {
            ItmoCard(Modifier.testTag(SportPointsTags.CAP)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text(SportStrings.sport_points_cap_title), Modifier.weight(1f), style = ItmoTheme.typography.titleMedium)
                    Text(state.cap.asString(), color = ItmoTheme.colorScheme.tertiary, style = ItmoTheme.typography.titleMedium.tabular())
                }
                LinearProgressIndicator(
                    progress = { state.capShare.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = ItmoTheme.colorScheme.tertiary,
                    trackColor = ItmoTheme.progressTrack,
                )
                Text(text(SportStrings.sport_points_cap_text), color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium)
            }
        }
        state.done?.let { done ->
            item(key = "done") {
                ItmoCard(Modifier.testTag(SportPointsTags.DONE), tone = Tone.Tertiary) {
                    Row(horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content)) {
                        Icon(painterResource(Res.drawable.ic_check), null)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related)) {
                            Text(done.asString(), style = ItmoTheme.typography.titleMedium)
                            Text(text(SportStrings.sport_points_done_text), style = ItmoTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        item(key = "history") { SectionHeader(text(SportStrings.sport_points_history)) }
        item(key = "chips") {
            FilterChips(
                state.chips.map { ChipSpec(it.label.asString(), it.selected, it.count) },
                { actions.onFilter(state.chips[it].filter) },
            )
        }
        if (state.months.isEmpty()) {
            item(key = "empty") {
                ContentState(
                    title = text(SportStrings.sport_points_empty),
                    modifier = Modifier.testTag(SportPointsTags.EMPTY),
                    size = ContentStateSize.Compact,
                )
            }
        }
        state.months.forEach { month ->
            item(key = "month-${month.title}") { DayLabel(month.title.asString()) }
            item(key = "rows-${month.title}") {
                ListGroup {
                    month.rows.forEach { row ->
                        item {
                            ListRow(
                                title = row.title,
                                subtitle = row.subtitle.asString(),
                                icon = painterResource(if (row.bonus) Res.drawable.ic_emoji_events else Res.drawable.ic_sprint),
                                iconTone = if (row.bonus) Tone.Tertiary else Tone.Primary,
                                trailing = row.points,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The ring and `15 + 15`: visits and the credited bonus in the ring's colours; the hero of the page. */
@Composable
private fun Formula(state: SportPointsUiState.Content) {
    val score = state.score
    val extended = ItmoTheme.extendedColors
    ItmoCard(tone = if (score.done) Tone.Success else Tone.Primary, hero = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.section)) {
            SportScoreRing(score, Modifier.size(SportSizes.ringLarge), large = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content)) {
                StatusPill(
                    state.status.asString(),
                    tone = if (score.done) Tone.Success else Tone.Neutral,
                    icon = if (score.done) painterResource(Res.drawable.ic_check) else null,
                )
                Term("${score.visits}", state.visitsLabel.asString(), if (score.done) extended.success else extended.sportScoreAttendance)
                Term("+ ${score.bonus}", text(SportStrings.sport_score_bonus), if (score.done) extended.success else extended.sportScoreBonus)
            }
        }
    }
}

@Composable
private fun Term(value: String, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content)) {
        Box(Modifier.size(SportSizes.dot).background(color, CircleShape))
        Column {
            Text(value, style = ItmoTheme.typography.titleLarge.tabular())
            Text(label, style = ItmoTheme.typography.labelMedium)
        }
    }
}
