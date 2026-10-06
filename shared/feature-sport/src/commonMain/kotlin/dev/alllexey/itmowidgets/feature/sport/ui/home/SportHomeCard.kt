package dev.alllexey.itmowidgets.feature.sport.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeCardActions
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.home.HomeCardRenderer
import dev.alllexey.itmowidgets.core.home.HomeCardTestTags
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.cards.FeedCard
import dev.alllexey.itmowidgets.designsystem.components.cards.FeedCardHeader
import dev.alllexey.itmowidgets.designsystem.components.cards.FeedRowBadge
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.core.sport_score_remaining_status
import dev.alllexey.itmowidgets.shared.designsystem.ic_exercise
import dev.alllexey.itmowidgets.shared.feature.sport.Res
import dev.alllexey.itmowidgets.shared.feature.sport.home_sport_more
import dev.alllexey.itmowidgets.shared.feature.sport.home_sport_score
import dev.alllexey.itmowidgets.shared.feature.sport.home_sport_title
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** The sport home card: the score out of 100 and the first own queue entries, in [timeZone]. */
class SportHomeCardRenderer(timeZone: TimeZone) : HomeCardRenderer {

    private val formatter = SportHomeCardFormatter(timeZone)

    override val kinds: Set<HomeCardKind> = setOf(HomeCardKind.SPORT)

    @Composable
    override fun Content(card: HomeCard, actions: HomeCardActions, modifier: Modifier) {
        check(card is HomeCard.Sport) { "${card.kind} is not the sport card" }
        SportCard(remember(card) { formatter.format(card) }, actions, modifier)
    }
}

@Composable
private fun SportCard(card: HomeSportCardUi, actions: HomeCardActions, modifier: Modifier) {
    FeedCard(modifier, onClick = actions.onOpenSport) {
        Column(Modifier.padding(horizontal = ItmoTheme.spacing.compact, vertical = ItmoTheme.spacing.cardPadding)) {
            val score = card.score
            FeedCardHeader(
                painterResource(KitRes.drawable.ic_exercise),
                stringResource(Res.string.home_sport_title),
                Modifier.padding(horizontal = ItmoTheme.spacing.compact),
            ) {
                if (score != null) {
                    Text(
                        stringResource(Res.string.home_sport_score, score.total),
                        color = ItmoTheme.colorScheme.primary,
                        style = ItmoTheme.typography.labelLarge,
                    )
                }
            }
            if (score != null) {
                LinearProgressIndicator(
                    progress = { score.total / SCORE_MAX },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ItmoTheme.spacing.compact)
                        .padding(top = ItmoTheme.spacing.compact)
                        .height(ScoreProgressHeight),
                )
                Text(
                    pluralStringResource(CoreRes.plurals.sport_score_remaining_status, score.remaining, score.remaining),
                    Modifier.padding(top = ItmoTheme.spacing.related).padding(horizontal = ItmoTheme.spacing.compact),
                    color = ItmoTheme.colorScheme.onSurfaceVariant,
                    style = ItmoTheme.typography.bodySmall,
                )
            }
            if (card.queue.isNotEmpty()) {
                Column(Modifier.padding(top = ItmoTheme.spacing.compact)) {
                    card.queue.forEach { row -> SportRow(row, actions) }
                }
            }
            if (card.more > 0) {
                Text(
                    pluralStringResource(Res.plurals.home_sport_more, card.more, card.more),
                    Modifier.padding(top = ItmoTheme.spacing.related).padding(horizontal = ItmoTheme.spacing.compact),
                    color = ItmoTheme.colorScheme.onSurfaceVariant,
                    style = ItmoTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SportRow(row: HomeSportRowUi, actions: HomeCardActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .clickable { actions.onPendingSport(row.args) }
            .testTag(HomeCardTestTags.SPORT_ROW)
            .heightIn(min = ItmoTheme.spacing.touchTarget)
            .padding(horizontal = ItmoTheme.spacing.compact, vertical = RowPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                row.title,
                color = ItmoTheme.colorScheme.onSurface,
                style = ItmoTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                row.subtitle,
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FeedRowBadge(row.badge.asString(), Modifier.padding(start = ItmoTheme.spacing.compact))
    }
}

private const val SCORE_MAX = 100f
private val RowShape = RoundedCornerShape(12.dp)
private val RowPaddingVertical = 6.dp
private val ScoreProgressHeight = 8.dp
