package dev.alllexey.itmowidgets.feature.recordbook.ui.subject

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import dev.alllexey.itmowidgets.designsystem.components.charts.ScoreRing
import dev.alllexey.itmowidgets.designsystem.components.charts.ScoreRingSector
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookRate
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubjectStatus
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSportProgress
import dev.alllexey.itmowidgets.feature.recordbook.ui.displayRate
import dev.alllexey.itmowidgets.feature.recordbook.ui.formatRecordbookNumber
import dev.alllexey.itmowidgets.feature.recordbook.ui.preview.RecordbookPreviewSamples
import dev.alllexey.itmowidgets.shared.core.common_retry
import dev.alllexey.itmowidgets.shared.core.sport_score_goal
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import dev.alllexey.itmowidgets.shared.designsystem.ic_close
import dev.alllexey.itmowidgets.shared.feature.recordbook.Res
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_official_pending
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_official_result
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_points_out_of
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_sport_attendance
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_sport_bonus
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_sport_bonus_limit
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_sport_no_period
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_sport_retry_description
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_sport_source
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/**
 * Physical education's result card in place of the hero (2.2's `item_recordbook_sport.xml`): My Sport's period and
 * score as a ring of attendance and bonus points with both numbers beside it, the bonus cap when it cut points, and
 * MyITMO's official result under a hairline. Without a matching sport period, or when the score failed ([onRetry]), the
 * ring gives way to the reason.
 */
@Composable
fun SubjectSportOverview(
    subject: RecordbookSubject,
    sport: RecordbookSportState?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = sport as? RecordbookSportState.Content
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = ItmoTheme.spacing.compact)
            .clip(ItmoTheme.shapes.cardSummary)
            .background(ItmoTheme.colorScheme.surfaceContainerLow)
            .padding(ItmoTheme.spacing.cardPadding),
    ) {
        Text(
            stringResource(Res.string.recordbook_sport_source),
            color = ItmoTheme.colorScheme.onSurface,
            style = ItmoTheme.typography.titleSmall,
        )
        // Without a period the line keeps its height, so the card does not jump when the score arrives.
        Text(
            content?.periodLabel.orEmpty(),
            Modifier.padding(top = ItmoTheme.spacing.related),
            color = ItmoTheme.colorScheme.onSurfaceVariant,
            style = ItmoTheme.typography.bodySmall,
        )
        Box(Modifier.padding(top = ItmoTheme.spacing.compact).heightIn(min = ScoreAreaMinHeight)) {
            if (content != null) {
                SportScore(content.score)
            } else {
                SportScoreMissing(sport == RecordbookSportState.Error, onRetry)
            }
        }
        val score = content?.score
        if (score != null && score.bonus > score.creditedBonus) {
            Text(
                stringResource(Res.string.recordbook_sport_bonus_limit, score.creditedBonus, score.bonus),
                Modifier.padding(top = ItmoTheme.spacing.compact),
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.bodySmall,
            )
        }
        HorizontalDivider(
            Modifier.padding(vertical = ItmoTheme.spacing.group),
            color = ItmoTheme.colorScheme.outlineVariant,
        )
        OfficialResult(subject)
    }
}

/** The ring with the total in its middle, read as «N из 100 баллов», and the two parts beside it. */
@Composable
private fun SportScore(score: SportScoreSummary) {
    val progress = RecordbookSportProgress(score)
    val extended = ItmoTheme.extendedColors
    val description = stringResource(Res.string.recordbook_points_out_of, score.total.toString(), FULL_SCORE)
    Row(
        Modifier.fillMaxWidth().heightIn(min = ScoreAreaMinHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScoreRing(
            sectors = listOf(
                ScoreRingSector(extended.sportScoreAttendance, progress.attendancePercentage),
                ScoreRingSector(extended.sportScoreBonus, progress.bonusPercentage),
            ),
            modifier = Modifier.size(RingSize).clearAndSetSemantics { contentDescription = description },
            thickness = RingThickness,
            trackColor = ItmoTheme.colorScheme.outlineVariant,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    formatRecordbookNumber(score.total.toDouble()),
                    color = ItmoTheme.colorScheme.onSurface,
                    style = ItmoTheme.typography.headlineMedium.tabular(),
                )
                Text(
                    stringResource(CoreRes.string.sport_score_goal),
                    color = ItmoTheme.colorScheme.onSurfaceVariant,
                    style = ItmoTheme.typography.bodySmall,
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = ItmoTheme.spacing.group)) {
            ScorePart(
                score.attendances,
                extended.sportScoreAttendance,
                stringResource(Res.string.recordbook_sport_attendance),
            )
            ScorePart(
                score.creditedBonus,
                extended.sportScoreBonus,
                stringResource(Res.string.recordbook_sport_bonus),
                Modifier.padding(top = ItmoTheme.spacing.content),
            )
        }
    }
}

@Composable
private fun ScorePart(points: Int, color: Color, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(formatRecordbookNumber(points.toDouble()), color = color, style = ItmoTheme.typography.titleLarge)
        Text(label, color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodySmall)
    }
}

/** No sport period for the semester, or a failed score with a retry. */
@Composable
private fun SportScoreMissing(failed: Boolean, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = ScoreAreaMinHeight),
        verticalArrangement = Arrangement.Center,
    ) {
        val reason = if (failed) {
            Res.string.recordbook_sport_retry_description
        } else {
            Res.string.recordbook_sport_no_period
        }
        Text(
            stringResource(reason),
            color = ItmoTheme.colorScheme.onSurfaceVariant,
            style = ItmoTheme.typography.bodyMedium,
        )
        if (failed) {
            TextButton(onClick = onRetry, modifier = Modifier.padding(top = ItmoTheme.spacing.compact)) {
                Text(stringResource(CoreRes.string.common_retry))
            }
        }
    }
}

/** «Результат в My ITMO» with the credit, its absence, or a failure, and a mark once it is decided. */
@Composable
private fun OfficialResult(subject: RecordbookSubject) {
    val icon = when (subject.status) {
        RecordbookSubjectStatus.PASSED -> KitRes.drawable.ic_check
        RecordbookSubjectStatus.ATTENTION -> KitRes.drawable.ic_close
        RecordbookSubjectStatus.IN_PROGRESS -> null
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                modifier = Modifier.padding(end = ItmoTheme.spacing.content).size(OfficialIconSize),
                tint = ItmoTheme.colorScheme.onSurface,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.recordbook_official_result),
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.bodySmall,
            )
            Text(
                if (subject.normalizedRate == RecordbookRate.InProgress) {
                    stringResource(Res.string.recordbook_official_pending)
                } else {
                    subject.displayRate()
                },
                Modifier.padding(top = ItmoTheme.spacing.related),
                color = ItmoTheme.colorScheme.onSurface,
                style = ItmoTheme.typography.titleSmall,
            )
        }
    }
}

private fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = TABULAR_FIGURES)

private const val FULL_SCORE = "100"
private const val TABULAR_FIGURES = "tnum"

private val ScoreAreaMinHeight = 160.dp
private val RingSize = 132.dp
private val RingThickness = 8.dp
private val OfficialIconSize = 24.dp

/** The PE subject of the previews; [rate] is MyITMO's official result. */
private fun peSubject(kind: String, rate: String? = null) =
    RecordbookPreviewSamples.subject(RecordbookPreviewSamples.PE_ID, RecordbookPreviewSamples.PE, kind, null, rate)

/** The middle of the semester: 52 attendance and 16 bonus points, the credit not set yet. */
@Preview
@Composable
private fun SubjectSportOverviewPreview() = ItmoPreview {
    SubjectSportOverview(peSubject("Зачёт"), RecordbookPreviewSamples.sport, onRetry = {}, modifier = previewMargin())
}

/** The credit is set; 46 bonus points of which 40 count. */
@Preview(name = "credited")
@Composable
private fun SubjectSportOverviewCreditedPreview() = ItmoPreview {
    SubjectSportOverview(
        peSubject("Зачёт", "зачет"),
        RecordbookSportState.Content("Весна 2025/2026", SportScoreSummary(64, 46), endsAt = null, current = true),
        onRetry = {},
        modifier = previewMargin(),
    )
}

/** The score failed to load: the reason with a retry, and a failed credit. */
@Preview(name = "error")
@Composable
private fun SubjectSportOverviewErrorPreview() = ItmoPreview {
    SubjectSportOverview(
        peSubject("Зачёт", "незачет"),
        RecordbookSportState.Error,
        onRetry = {},
        modifier = previewMargin(),
    )
}

/** The study and sport semesters did not match. */
@Preview(name = "unavailable")
@Composable
private fun SubjectSportOverviewUnavailablePreview() = ItmoPreview {
    SubjectSportOverview(peSubject("Зачёт"), RecordbookSportState.Unavailable, onRetry = {}, modifier = previewMargin())
}

@Composable
private fun previewMargin(): Modifier = Modifier.padding(horizontal = ItmoTheme.spacing.screenMargin)
