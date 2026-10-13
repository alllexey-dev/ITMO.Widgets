package dev.alllexey.itmowidgets.designsystem.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.components.charts.ScoreRing
import dev.alllexey.itmowidgets.designsystem.components.charts.ScoreRingSector
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_arrow_drop_down
import dev.alllexey.itmowidgets.shared.designsystem.ic_arrow_drop_up
import org.jetbrains.compose.resources.painterResource

/** The colour of text in [this] tone on a plain surface: a reason, a broken minimum, a bar. */
@Composable
fun Tone.ink(): Color = when (this) {
    Tone.Neutral -> ItmoTheme.colorScheme.onSurfaceVariant
    Tone.Primary -> ItmoTheme.colorScheme.primary
    Tone.Tertiary -> ItmoTheme.colorScheme.tertiary
    Tone.Success -> ItmoTheme.extendedColors.success
    Tone.Warning -> ItmoTheme.extendedColors.warning
    Tone.Error -> ItmoTheme.colorScheme.error
}

/**
 * A number (or a pill, or a small ring) over its quiet [source] (`БАРС`, `My ITMO`, `таблица`): the number column of a
 * row. [stale] turns the source into a warning (an old BARS answer with its date). With [onSourceClick] the source
 * opens where the number comes from; [sourceLabel] is what TalkBack reads for it.
 */
@Composable
fun ValueWithSource(
    source: String,
    modifier: Modifier = Modifier,
    stale: Boolean = false,
    onSourceClick: (() -> Unit)? = null,
    sourceLabel: String? = null,
    value: @Composable () -> Unit,
) {
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related)) {
        value()
        Text(
            source,
            modifier = if (onSourceClick != null) {
                Modifier.clip(ItmoTheme.shapes.small).clickable(onClickLabel = sourceLabel, role = Role.Button, onClick = onSourceClick)
            } else {
                Modifier
            },
            color = if (stale) Tone.Warning.ink() else ItmoTheme.colorScheme.onSurfaceVariant,
            style = ItmoTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

/** Points out of 100 in a row: [text] (`63,5`) over a thin bar filled to [share] in [tone]'s colour. */
@Composable
fun PointsMeter(text: String, share: Float, tone: Tone, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related)) {
        Text(text, style = ItmoTheme.typography.titleLarge.tabular(), color = ItmoTheme.colorScheme.onSurface)
        Box(Modifier.width(MeterWidth).height(MeterHeight).clip(ItmoTheme.shapes.full).background(ItmoTheme.progressTrack)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(share.coerceIn(0f, 1f)).clip(ItmoTheme.shapes.full).background(tone.ink()))
        }
    }
}

/**
 * The sport points in a small ring: [visits] and [bonus] as shares of the turn in the sport colours, [text] (the total)
 * in the middle. The big ring of the sport tab is `ScoreRing` itself.
 */
@Composable
fun SportRing(text: String, visits: Float, bonus: Float, modifier: Modifier = Modifier) {
    val extended = ItmoTheme.extendedColors
    ScoreRing(
        sectors = listOf(
            ScoreRingSector(extended.sportScoreAttendance, visits * PERCENT),
            ScoreRingSector(extended.sportScoreBonus, bonus * PERCENT),
        ),
        modifier = modifier.size(SportRingSize),
        thickness = SportRingThickness,
    ) {
        Text(text, style = ItmoTheme.typography.labelLarge.tabular(), fontWeight = FontWeight.SemiBold)
    }
}

/**
 * The votes of a link: ▲ [score] ▼ in a pill, the viewer's own vote ([myVote] 1 or -1) in `primary`. Without [enabled]
 * only the score shows. [upLabel] and [downLabel] are what TalkBack reads for the arrows.
 */
@Composable
fun VotePill(
    score: Int,
    myVote: Int,
    upLabel: String,
    downLabel: String,
    onUp: () -> Unit,
    onDown: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier.clip(ItmoTheme.shapes.full).background(Tone.Neutral.container()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VoteArrow(painterResource(Res.drawable.ic_arrow_drop_up), upLabel, myVote > 0, enabled, onUp)
        Text(
            score.toString(),
            style = ItmoTheme.typography.labelLarge.tabular(),
            color = if (myVote != 0) ItmoTheme.colorScheme.primary else ItmoTheme.colorScheme.onSurface,
            modifier = if (enabled) Modifier else Modifier.padding(horizontal = ItmoTheme.spacing.content, vertical = ItmoTheme.spacing.related),
        )
        VoteArrow(painterResource(Res.drawable.ic_arrow_drop_down), downLabel, myVote < 0, enabled, onDown)
    }
}

@Composable
private fun VoteArrow(icon: androidx.compose.ui.graphics.painter.Painter, label: String, chosen: Boolean, enabled: Boolean, onClick: () -> Unit) {
    if (!enabled) return
    IconButton(onClick = onClick, modifier = Modifier.size(VoteTarget)) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (chosen) ItmoTheme.colorScheme.primary else ItmoTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Tabular figures: points line up in columns. */
fun androidx.compose.ui.text.TextStyle.tabular() = copy(fontFeatureSettings = "tnum")

private const val PERCENT = 100f
private val MeterWidth = 40.dp
private val MeterHeight = 4.dp
private val SportRingSize = 44.dp
private val SportRingThickness = 4.dp
private val VoteTarget = 40.dp

/** A row's number column in every form, and the votes. */
@Preview
@Composable
private fun ValueWithSourcePreview() = ItmoPreview {
    Column(
        Modifier.background(ItmoTheme.colorScheme.background).padding(ItmoTheme.spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.group),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.section)) {
            ValueWithSource("БАРС") { PointsMeter("74", 0.74f, Tone.Error) }
            ValueWithSource("таблица") { PointsMeter("41,5", 0.415f, Tone.Primary) }
            ValueWithSource("БАРС, 4 сент.", stale = true) { PointsMeter("63,5", 0.635f, Tone.Warning) }
            ValueWithSource("My ITMO") { StatusPill("4C", tone = Tone.Success) }
            ValueWithSource("Спорт") { SportRing("30", 0.15f, 0.15f) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content)) {
            VotePill(12, 0, "Полезная ссылка", "Бесполезная ссылка", {}, {})
            VotePill(8, 1, "Полезная ссылка", "Бесполезная ссылка", {}, {})
            VotePill(3, 0, "Полезная ссылка", "Бесполезная ссылка", {}, {}, enabled = false)
        }
    }
}
